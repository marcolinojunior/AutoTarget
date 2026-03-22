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

    /** Intervalo entre coletas (ms). */
    private static final int INTERVALO_COLETA = 2000;

    /** Ruído simulado nos sensores. */
    private final Random ruido = new Random();

    /**
     * Cria a thread de sensores.
     *
     * @param alvos      lista compartilhada de alvos
     * @param sensorLock lock compartilhado com thread de reconciliação
     */
    public SensorThread(List<Alvo> alvos, Object sensorLock) {
        super("SensorThread");
        this.alvos = alvos;
        this.sensorLock = sensorLock;
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
     */
    private void coletarDados() {
        synchronized (sensorLock) {
            int count = 0;
            for (Alvo a : alvos) {
                if (a.isAtivo()) count++;
            }

            leiturasPosX = new float[count];
            leiturasPosY = new float[count];
            leiturasVelocidade = new float[count];

            int i = 0;
            for (Alvo a : alvos) {
                if (!a.isAtivo()) continue;
                if (i >= count) break;

                // Leitura com ruído gaussiano (simula sensor imperfeito)
                leiturasPosX[i] = a.getX() + (float) (ruido.nextGaussian() * 5.0);
                leiturasPosY[i] = a.getY() + (float) (ruido.nextGaussian() * 5.0);
                leiturasVelocidade[i] = a.getVelocidade() + (float) (ruido.nextGaussian() * 0.5);
                i++;
            }
            quantidadeAlvosColetados = i;

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
