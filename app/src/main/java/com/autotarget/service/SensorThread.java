/*
 * ============================================================================
 * Arquivo: SensorThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para coleta simulada de dados de sensores dos alvos.
 *   Conforme o diagrama de arquitetura, esta thread opera de forma independente,
 *   coletando periodicamente (a cada 2s) as posições e velocidades dos alvos
 *   ativos, adicionando ruído gaussiano para simular imprecisão de sensores
 *   reais, e alimentando a ReconciliacaoThread com os dados brutos.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - SensorThread extends Thread — thread daemon dedicada.
 *     - Loop run(): coletarDados() → sleep(2000ms) → loop
 *     - Coleta posição (x, y) e velocidade de cada alvo ativo.
 *     - Adiciona ruído gaussiano (nextGaussian * 5.0 para posição,
 *       nextGaussian * 0.5 para velocidade) simulando sensor imperfeito.
 *     - Armazena leituras em arrays float[] compartilhados.
 *
 *   ► Sincronização e região crítica (6.1.5):
 *     - Lock ordering: collisionLock (externo) → sensorLock (interno).
 *     - synchronized(collisionLock) em coletarDados() — protege a iteração
 *       da lista de alvos, que é modificada por spawnTimer e physicsTimer.
 *     - synchronized(sensorLock) para publicar os arrays preenchidos e
 *       chamar sensorLock.notifyAll() — notifica a ReconciliacaoThread.
 *     - Modelo produtor-consumidor: esta thread é o PRODUTOR,
 *       ReconciliacaoThread é o CONSUMIDOR.
 *     - volatile em todos os arrays de leitura e no flag 'ativo'.
 *     - Daemon thread: não impede o encerramento da aplicação.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - InterruptedException capturada em run() — seta interrupt flag e para.
 *
 *   ► Reconciliação de dados (seção 4.2):
 *     - Implementa o nó "Thread Sensores/Coleta" do diagrama de arquitetura.
 *     - Dados brutos (com ruído) justificam a necessidade de reconciliação:
 *       posições imprecisas devem ser corrigidas antes de otimizar canhões.
 *
 * DADOS COLETADOS (buffers compartilhados):
 *   - leiturasPosX[]: posição X com ruído (float[])
 *   - leiturasPosY[]: posição Y com ruído (float[])
 *   - leiturasVelocidade[]: velocidade com ruído (float[])
 *   - quantidadeAlvosColetados: número de alvos ativos na coleta (int)
 *
 * FLUXO:
 *   Alvos (List<Alvo>) → coletarDados() [ruído gaussiano] → buffers
 *   → sensorLock.notifyAll() → ReconciliacaoThread consome
 *
 * LOCK ORDERING (regra global do projeto):
 *   collisionLock → sensorLock (nunca na ordem inversa)
 *   collisionLock → projeteis (dentro de Projetil/Canhao)
 *   canhoes → projeteis (dentro de GameSurfaceView)
 *
 * ============================================================================
 */
package com.autotarget.service;

import com.autotarget.model.Alvo;

import java.util.List;
import java.util.Random;

/**
 * Thread dedicada para coleta simulada de dados de sensores.
 * <p>
 * Conforme a arquitetura do AutoTarget, esta thread opera de forma
 * independente, simulando leituras de sensores (posição, velocidade)
 * dos alvos e alimentando a thread de Reconciliação com dados brutos.
 * <p>
 * Fluxo: Thread Sensores/Coleta → Thread Reconciliação+Otimização
 */
public class SensorThread extends Thread {

    /** Lista compartilhada de alvos (leitura). */
    private final List<Alvo> alvos;

    /** Dados de sensor coletados (buffer compartilhado com Reconciliação). */
    private volatile float[] leiturasPosX;
    private volatile float[] leiturasPosY;
    private volatile float[] leiturasVelocidade;
    private volatile int quantidadeAlvosColetados;

    /** Flag de controle. */
    private volatile boolean ativo;

    /** Lock para sincronizar com thread de reconciliação. */
    private final Object sensorLock;

    /**
     * Lock para proteger iteração da lista de alvos.
     * Compartilhado com Jogo, Projetil e physicsTimer.
     */
    private final Object collisionLock;

    /** Intervalo entre coletas (ms). */
    private static final int INTERVALO_COLETA = 2000;

    /** Ruído simulado nos sensores. */
    private final Random ruido = new Random();

    /**
     * Cria a thread de sensores.
     *
     * @param alvos         lista compartilhada de alvos
     * @param sensorLock    lock compartilhado com thread de reconciliação
     * @param collisionLock lock global para proteger iteração de alvos
     */
    public SensorThread(List<Alvo> alvos, Object sensorLock, Object collisionLock) {
        super("SensorThread");
        this.alvos = alvos;
        this.sensorLock = sensorLock;
        this.collisionLock = collisionLock;
        this.ativo = true;
        this.leiturasPosX = new float[0];
        this.leiturasPosY = new float[0];
        this.leiturasVelocidade = new float[0];
        this.quantidadeAlvosColetados = 0;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            try {
                coletarDados();
                Thread.sleep(INTERVALO_COLETA);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Coleta dados simulados de todos os alvos ativos.
     * Adiciona ruído gaussiano para simular imprecisão dos sensores.
     * <p>
     * Lock ordering: collisionLock (externo) → sensorLock (interno).
     * Primeiro adquire collisionLock para iterar alvos com segurança,
     * copia valores para arrays locais, depois adquire sensorLock
     * para publicar e notificar a ReconciliacaoThread.
     */
    private void coletarDados() {
        // ── Fase 1: copiar dados dos alvos sob collisionLock ──
        float[] localPosX;
        float[] localPosY;
        float[] localVel;
        int localCount;

        synchronized (collisionLock) {
            int count = 0;
            for (Alvo a : alvos) {
                if (a.isAtivo()) count++;
            }

            localPosX = new float[count];
            localPosY = new float[count];
            localVel = new float[count];

            int i = 0;
            for (Alvo a : alvos) {
                if (!a.isAtivo()) continue;
                if (i >= count) break;

                // Leitura com ruído gaussiano (simula sensor imperfeito)
                localPosX[i] = a.getX() + (float) (ruido.nextGaussian() * 5.0);
                localPosY[i] = a.getY() + (float) (ruido.nextGaussian() * 5.0);
                localVel[i] = a.getVelocidade() + (float) (ruido.nextGaussian() * 0.5);
                i++;
            }
            localCount = i;
        }

        // ── Fase 2: publicar dados e notificar sob sensorLock ──
        synchronized (sensorLock) {
            leiturasPosX = localPosX;
            leiturasPosY = localPosY;
            leiturasVelocidade = localVel;
            quantidadeAlvosColetados = localCount;

            // Notificar thread de reconciliação que há dados novos
            sensorLock.notifyAll();
        }
    }

    // ── Getters para Reconciliação ───────────────────────────────

    public float[] getLeiturasPosX() { return leiturasPosX; }
    public float[] getLeiturasPosY() { return leiturasPosY; }
    public float[] getLeiturasVelocidade() { return leiturasVelocidade; }
    public int getQuantidadeAlvosColetados() { return quantidadeAlvosColetados; }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
}
