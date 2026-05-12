/*
 * ============================================================================
 * Arquivo: SensorThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T7 — SensorThread.coletar
 *   Período P₇ = 1000ms (1s), Execução C₇ = 5-10ms, Deadline D₇ = 1000ms
 *   Prioridade RM: 6 (Fundo)
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para coleta simulada de dados de sensores. Opera a cada
 *   1s (para acumular ≥10 leituras em 10s conforme §6.2.2-c), coleta posições
 *   e velocidades com ruído gaussiano PROPORCIONAL (5% do valor real),
 *   e alimenta buffer histórico de distâncias para reconciliação.
 *
 * RUÍDO (AV2 §6.2.2-c):
 *   Desvio padrão proporcional ao valor real (5%):
 *   posX_ruidosa = posX_real × (1 + N(0, 0.05))
 *
 * BUFFER (AV2 §6.2.2-c):
 *   ≥10 leituras por alvo. Com intervalo 1s e janela 10s → 10 amostras.
 *
 * LOCK ORDERING: collisionLock → sensorLock (nunca na ordem inversa)
 *
 * ============================================================================
 */
package com.autotarget.service;

import android.util.Log;
import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.util.RMAAnalysis;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Thread dedicada para coleta simulada de dados de sensores.
 * Produz leituras ruidosas (ruído 5% proporcional) e alimenta
 * buffer histórico de distâncias para reconciliação EJML.
 */
public class SensorThread extends Thread {

    private static final String TAG = "SensorThread";

    /** Referência ao motor do jogo para obter listas dinâmicas. */
    private final com.autotarget.engine.Jogo jogo;

    /** Dados de sensor coletados (buffer compartilhado com Reconciliação). */
    private volatile float[] leiturasPosX;
    private volatile float[] leiturasPosY;
    private volatile float[] leiturasVelocidade;
    private volatile int quantidadeAlvosColetados;

    /**
     * Buffer histórico de distâncias d_ij para reconciliação.
     * Cada entrada é float[M][N] onde M=alvos, N=canhões.
     * Mantém as últimas 10 coletas (janela de 10 segundos).
     */
    private final LinkedList<float[][]> historicoDistancias;
    private static final int TAMANHO_HISTORICO = 10;

    /** Flag de controle. */
    private volatile boolean ativo;

    /** Lock para sincronizar com thread de reconciliação. */
    private final Object sensorLock;

    /** Lock para proteger iteração da lista de alvos. */
    private final Object collisionLock;

    /** Intervalo entre coletas (ms) — 1s para ≥10 leituras em 10s. */
    private static final int INTERVALO_COLETA = 1000;

    /** Proporção de ruído (5% do valor real). */
    private static final double PROPORCAO_RUIDO = 0.05;

    /** Ruído simulado nos sensores. */
    private final Random ruido = new Random();

    /**
     * Cria a thread de sensores.
     *
     * @param alvos         lista compartilhada de alvos
     * @param canhoes       lista de canhões (para distâncias)
     * @param sensorLock    lock compartilhado com thread de reconciliação
     * @param collisionLock lock global para proteger iteração de alvos
     */
    public SensorThread(com.autotarget.engine.Jogo jogo,
                        Object sensorLock, Object collisionLock) {
        super("SensorThread");
        this.jogo = jogo;
        this.sensorLock = sensorLock;
        this.collisionLock = collisionLock;
        this.ativo = true;
        this.leiturasPosX = new float[0];
        this.leiturasPosY = new float[0];
        this.leiturasVelocidade = new float[0];
        this.quantidadeAlvosColetados = 0;
        this.historicoDistancias = new LinkedList<>();
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            long startNs = System.nanoTime();
            try {
                coletarDados();
                Thread.sleep(INTERVALO_COLETA);

                // Instrumentação RMA
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                RMAAnalysis.checkDeadline("T7-Sensor", elapsedMs, INTERVALO_COLETA);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Coleta dados simulados com ruído proporcional (5% do valor real).
     * Lock ordering: collisionLock (externo) → sensorLock (interno).
     */
    private void coletarDados() {
        float[] localPosX;
        float[] localPosY;
        float[] localVel;
        int localCount;
        float[][] localDistancias = null;

        synchronized (collisionLock) {
            // Contar alvos ativos
            List<Alvo> alvosAtuais = jogo.getAllAlvos();
            List<Canhao> canhoesAtuais = jogo.getAllCanhoes();
            
            int count = 0;
            for (Alvo a : alvosAtuais) {
                if (a.isAtivo()) count++;
            }

            localPosX = new float[count];
            localPosY = new float[count];
            localVel = new float[count];

            // Coletar posições dos alvos ativos para distâncias
            List<float[]> alvosAtivosPos = new ArrayList<>();

            int i = 0;
            for (Alvo a : alvosAtuais) {
                if (!a.isAtivo()) continue;
                if (i >= count) break;

                // Ruído proporcional: valor × (1 + N(0, 0.05))
                float realX = a.getX();
                float realY = a.getY();
                float realV = a.getVelocidade();

                localPosX[i] = realX * (1.0f + (float) (ruido.nextGaussian() * PROPORCAO_RUIDO));
                localPosY[i] = realY * (1.0f + (float) (ruido.nextGaussian() * PROPORCAO_RUIDO));
                localVel[i] = realV * (1.0f + (float) (ruido.nextGaussian() * PROPORCAO_RUIDO));

                alvosAtivosPos.add(new float[]{realX, realY});
                i++;
            }
            localCount = i;

            // Calcular distâncias d_ij entre cada alvo i e canhão j
            int numCanhoes = canhoesAtuais.size();
            if (numCanhoes > 0 && localCount > 0) {
                localDistancias = new float[localCount][numCanhoes];
                for (int ai = 0; ai < localCount && ai < alvosAtivosPos.size(); ai++) {
                    float[] apos = alvosAtivosPos.get(ai);
                    for (int cj = 0; cj < numCanhoes; cj++) {
                        Canhao c = canhoesAtuais.get(cj);
                        float dx = apos[0] - c.getX();
                        float dy = apos[1] - c.getY();
                        // Distância com ruído
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        localDistancias[ai][cj] = dist
                                * (1.0f + (float) (ruido.nextGaussian() * PROPORCAO_RUIDO));
                    }
                }
            }
        }

        // ── Fase 2: publicar dados e notificar sob sensorLock ──
        synchronized (sensorLock) {
            leiturasPosX = localPosX;
            leiturasPosY = localPosY;
            leiturasVelocidade = localVel;
            quantidadeAlvosColetados = localCount;

            // Adicionar ao buffer histórico de distâncias
            if (localDistancias != null) {
                historicoDistancias.addLast(localDistancias);
                while (historicoDistancias.size() > TAMANHO_HISTORICO) {
                    historicoDistancias.removeFirst();
                }
            }

            sensorLock.notifyAll();
        }
    }

    // ── Estatísticas para Reconciliação ──────────────────────────

    /**
     * Calcula a média das distâncias d̄_ij sobre o buffer histórico.
     * @return float[M][N] com médias, ou null se dados insuficientes
     */
    public float[][] getMediaDistancias() {
        synchronized (sensorLock) {
            if (historicoDistancias.isEmpty()) return null;

            int M = historicoDistancias.getLast().length;
            int N = historicoDistancias.getLast()[0].length;
            float[][] media = new float[M][N];
            int count = 0;

            for (float[][] snapshot : historicoDistancias) {
                if (snapshot.length != M || snapshot[0].length != N) continue;
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        media[i][j] += snapshot[i][j];
                    }
                }
                count++;
            }

            if (count == 0) return null;
            for (int i = 0; i < M; i++) {
                for (int j = 0; j < N; j++) {
                    media[i][j] /= count;
                }
            }
            return media;
        }
    }

    /**
     * Calcula a variância amostral s²_ij das distâncias.
     * @return float[M][N] com variâncias, ou null se dados insuficientes
     */
    public float[][] getVarianciaDistancias() {
        synchronized (sensorLock) {
            float[][] media = getMediaDistancias();
            if (media == null || historicoDistancias.size() < 2) return null;

            int M = media.length;
            int N = media[0].length;
            float[][] variancia = new float[M][N];
            int count = 0;

            for (float[][] snapshot : historicoDistancias) {
                if (snapshot.length != M || snapshot[0].length != N) continue;
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        float diff = snapshot[i][j] - media[i][j];
                        variancia[i][j] += diff * diff;
                    }
                }
                count++;
            }

            if (count <= 1) return null;
            for (int i = 0; i < M; i++) {
                for (int j = 0; j < N; j++) {
                    variancia[i][j] /= (count - 1);
                }
            }
            return variancia;
        }
    }

    /**
     * @return número de leituras no buffer histórico
     */
    public int getHistoricoCount() {
        synchronized (sensorLock) {
            return historicoDistancias.size();
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
