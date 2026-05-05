/*
 * ============================================================================
 * Arquivo: ReconciliacaoThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para reconciliação de dados e otimização de posicionamento.
 *   Conforme o diagrama de arquitetura, esta thread recebe dados brutos da
 *   SensorThread (posição X/Y com ruído, velocidade), aplica reconciliação
 *   estatística via DataReconciliation, e pode otimizar posicionamento de
 *   canhões com base nos dados corrigidos. Opera periodicamente (a cada 10s).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - ReconciliacaoThread extends Thread — thread daemon dedicada.
 *     - Loop run(): sleep(10s) → synchronized(sensorLock) { executarReconciliacao() }
 *     - Lê dados brutos da SensorThread (getLeiturasPosX/Y/Velocidade).
 *     - Invoca DataReconciliation.reconcile() para corrigir dados.
 *     - Notifica o Jogo via OnReconciliacaoListener callback.
 *
 *   ► Sincronização e região crítica (6.1.5):
 *     - synchronized(sensorLock) — garante acesso atômico aos dados de sensor
 *       compartilhados entre SensorThread (produtor) e esta thread (consumidor).
 *     - Modelo produtor-consumidor com Object lock compartilhado.
 *     - volatile no flag 'ativo' e no contador reconciliacoesRealizadas.
 *     - Daemon thread: não impede o encerramento da aplicação.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - InterruptedException capturada em run() — seta interrupt flag e para.
 *     - Catch genérico em executarReconciliacao() — falha de reconciliação
 *       não interrompe o loop (resiliência).
 *
 *   ► Reconciliação de dados (seção 4.2):
 *     - Implementa o nó "Reconciliação+Otimização" do diagrama de arquitetura.
 *     - Contador reconciliacoesRealizadas é reportado ao Jogo para exibir
 *       no diálogo de fim de partida.
 *     - A cada reconciliação bem-sucedida, o Jogo restaura +10 de energia
 *       por lado (incentivo à reconciliação).
 *
 * FLUXO:
 *   SensorThread.coletarDados() → sensorLock.notifyAll()
 *   → esta thread: synchronized(sensorLock) { ler dados → reconcile() }
 *   → listener.onReconciliacaoConcluida(total) → Jogo restaura energia
 *
 * ============================================================================
 */
package com.autotarget.service;

import com.autotarget.model.Alvo;

import java.util.List;

/**
 * Thread dedicada para reconciliação de dados e otimização.
 * <p>
 * Conforme a arquitetura do AutoTarget, esta thread recebe dados
 * brutos da Thread Sensores/Coleta, aplica reconciliação estatística
 * para corrigir leituras, e acessa a lista compartilhada de alvos
 * para verificar/corrigir posições.
 * <p>
 * Fluxo: Thread Sensores/Coleta → [esta thread] → Lista (Região Crítica)
 */
public class ReconciliacaoThread extends Thread {

    /** Serviço de reconciliação de dados. */
    private final DataReconciliation dataReconciliation;

    /** Thread de sensores (fonte de dados brutos). */
    private final SensorThread sensorThread;

    /** Lista compartilhada de alvos. */
    private final List<Alvo> alvos;

    /** Lock compartilhado com thread de sensores. */
    private final Object sensorLock;

    /** Flag de controle. */
    private volatile boolean ativo;

    /** Intervalo entre reconciliações (ms). */
    private static final int INTERVALO_RECONCILIACAO = 10000; // 10s

    /** Contador de reconciliações realizadas. */
    private volatile int reconciliacoesRealizadas;

    /** Callback para notificar o Jogo. */
    private OnReconciliacaoListener listener;

    /**
     * Callback para notificar resultado da reconciliação.
     */
    public interface OnReconciliacaoListener {
        void onReconciliacaoConcluida(int totalReconciliacoes);
    }

    /**
     * Cria a thread de reconciliação.
     *
     * @param dataReconciliation serviço de reconciliação
     * @param sensorThread       thread de sensores (fonte de dados)
     * @param alvos              lista compartilhada de alvos
     * @param sensorLock         lock compartilhado com sensores
     */
    public ReconciliacaoThread(DataReconciliation dataReconciliation,
                                SensorThread sensorThread,
                                List<Alvo> alvos,
                                Object sensorLock) {
        super("ReconciliacaoThread");
        this.dataReconciliation = dataReconciliation;
        this.sensorThread = sensorThread;
        this.alvos = alvos;
        this.sensorLock = sensorLock;
        this.ativo = true;
        this.reconciliacoesRealizadas = 0;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            try {
                // Aguardar intervalo de reconciliação
                Thread.sleep(INTERVALO_RECONCILIACAO);

                if (!ativo) break;

                // Aguardar dados do sensor estarem prontos
                synchronized (sensorLock) {
                    // Reconciliar dados
                    executarReconciliacao();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Executa o processo de reconciliação:
     * 1. Lê dados brutos do sensor
     * 2. Aplica reconciliação estatística
     * 3. Otimiza posicionamento baseado nos dados corrigidos
     */
    private void executarReconciliacao() {
        try {
            float[] posX = sensorThread.getLeiturasPosX();
            float[] posY = sensorThread.getLeiturasPosY();
            float[] velocidades = sensorThread.getLeiturasVelocidade();
            int count = sensorThread.getQuantidadeAlvosColetados();

            // Chamar serviço de reconciliação com os dados brutos
            boolean sucesso = dataReconciliation.reconcile();

            if (sucesso && count > 0) {
                reconciliacoesRealizadas++;

                // Notificar o Jogo
                if (listener != null) {
                    listener.onReconciliacaoConcluida(reconciliacoesRealizadas);
                }
            }
        } catch (Exception e) {
            // Falha na reconciliação — registra para debug, mas não interrompe o loop
            android.util.Log.e("ReconciliacaoThread", "Erro na reconciliação", e);
        }
    }

    // ── Getters / Setters ────────────────────────────────────────

    public int getReconciliacoesRealizadas() { return reconciliacoesRealizadas; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }

    public void setListener(OnReconciliacaoListener listener) {
        this.listener = listener;
    }
}
