/*
 * ============================================================================
 * Arquivo: ReconciliacaoThread.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T8 — ReconciliacaoThread (EJML)
 *   Período P₈ = 10000ms, Execução C₈ = 200-500ms, Deadline D₈ = 10000ms
 *   Prioridade RM: 7 (Estática/Fundo)
 *
 * DESCRIÇÃO TÉCNICA:
 *   Thread dedicada para reconciliação de dados e otimização logística.
 *   Recebe dados brutos da SensorThread, aplica reconciliação via EJML
 *   (DataReconciliation), calcula função de utilidade U(N), e decide
 *   autonomamente adicionar/remover/realocar canhões via Greedy Search.
 *
 * RECONCILIAÇÃO (AV2 §6.2.2-d):
 *   A cada 10s: ler buffer → reconciliar → estimar posições → otimizar
 *
 * OTIMIZAÇÃO (AV2 §6.2.2-e):
 *   Função de utilidade: U(N) = Σ r(N) · Σ exp(-β·d̂_ij)
 *   Decisão: se ΔU > custo → adicionar; se utilidade marginal < 0 → remover
 *   Realocação: centroide ponderado p_j = Σ w_ij·(X̂,Ŷ) / Σ w_ij
 *
 * ============================================================================
 */
package com.autotarget.service;

import android.util.Log;

import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.util.DataReconciliation;
import com.autotarget.util.RMAAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread dedicada para reconciliação de dados e otimização via EJML.
 */
public class ReconciliacaoThread extends Thread {

    private static final String TAG = "ReconciliacaoThread";

    /** Serviço de reconciliação de dados (EJML). */
    private final DataReconciliation dataReconciliation;

    /** Thread de sensores (fonte de dados brutos). */
    private final SensorThread sensorThread;

    /** Referência ao motor do jogo. */
    private final com.autotarget.engine.Jogo jogo;

    /** Lock compartilhado com thread de sensores. */
    private final Object sensorLock;

    /** Lock para proteção de colisão (necessário para otimização). */
    private final Object collisionLock;

    /** Flag de controle. */
    private volatile boolean ativo;

    /** Intervalo entre reconciliações (ms). */
    private static final int INTERVALO_RECONCILIACAO = 10000; // 10s

    /** Constante de dissipação para função de utilidade. */
    private static final double BETA = 0.005;

    /** Limiar para decisão de adicionar canhão (ganho marginal mínimo). */
    private static final double LIMIAR_GANHO = 0.1;

    /** Contador de reconciliações realizadas. */
    private volatile int reconciliacoesRealizadas;

    /** Callback para notificar o Jogo. */
    private OnReconciliacaoListener listener;

    /** Largura da tela (para determinar lado). */
    private volatile int larguraTela;

    /** Altura da tela. */
    private volatile int alturaTela;

    /**
     * Callback para notificar resultado da reconciliação.
     */
    public interface OnReconciliacaoListener {
        void onReconciliacaoConcluida(int totalReconciliacoes);
        void onSugestaoAdicionarCanhao(Lado lado, float x, float y);
        void onSugestaoRemoverCanhao(Lado lado);
        void onRealocarCanhao(Canhao canhao, float novoX, float novoY);
    }

    public ReconciliacaoThread(DataReconciliation dataReconciliation,
                                SensorThread sensorThread,
                                com.autotarget.engine.Jogo jogo,
                                Object sensorLock,
                                Object collisionLock) {
        super("ReconciliacaoThread");
        this.dataReconciliation = dataReconciliation;
        this.sensorThread = sensorThread;
        this.jogo = jogo;
        this.sensorLock = sensorLock;
        this.collisionLock = collisionLock;
        this.ativo = true;
        this.reconciliacoesRealizadas = 0;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (ativo) {
            long startNs = System.nanoTime();
            try {
                Thread.sleep(INTERVALO_RECONCILIACAO);
                if (!ativo) break;

                synchronized (sensorLock) {
                    executarReconciliacao();
                }

                // Instrumentação RMA
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                RMAAnalysis.checkDeadline("T8-Reconciliacao", elapsedMs, INTERVALO_RECONCILIACAO);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Executa reconciliação completa:
     * 1. Ler buffer histórico → calcular média/variância
     * 2. Reconciliar via EJML
     * 3. Realocar canhões (centroide ponderado)
     * 4. Avaliar custo-benefício (greedy search)
     */
    private void executarReconciliacao() {
        try {
            // Verificar dados suficientes
            if (sensorThread.getHistoricoCount() < 3) {
                Log.d(TAG, "Buffer insuficiente para reconciliação");
                return;
            }

            float[][] mediaD = sensorThread.getMediaDistancias();
            float[][] varD = sensorThread.getVarianciaDistancias();
            if (mediaD == null || varD == null) return;

            // Coletar posições dos canhões
            List<Canhao> canhoesSnapshot;
            synchronized (collisionLock) {
                canhoesSnapshot = new ArrayList<>(jogo.getAllCanhoes());
            }

            int N = canhoesSnapshot.size();
            if (N == 0) return;

            float[] canhoesX = new float[N];
            float[] canhoesY = new float[N];
            for (int j = 0; j < N; j++) {
                canhoesX[j] = canhoesSnapshot.get(j).getX();
                canhoesY[j] = canhoesSnapshot.get(j).getY();
            }

            // ── RECONCILIAÇÃO EJML ──────────────────────────────
            DataReconciliation.ReconciliationResult[] resultados =
                    dataReconciliation.reconciliar(canhoesX, canhoesY, mediaD, varD);

            if (resultados == null || resultados.length == 0) return;

            reconciliacoesRealizadas++;
            Log.i(TAG, "Reconciliação #" + reconciliacoesRealizadas + " concluída: "
                    + resultados.length + " alvos");

            // ── REALOCAÇÃO DE CANHÕES ───────────────────────────
            realocarCanhoes(canhoesSnapshot, resultados);

            // ── OTIMIZAÇÃO GREEDY ───────────────────────────────
            avaliarCustoBeneficio(canhoesSnapshot, resultados);

            // Notificar Jogo
            if (listener != null) {
                listener.onReconciliacaoConcluida(reconciliacoesRealizadas);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação", e);
        }
    }

    /**
     * Realoca canhões para centroide ponderado dos alvos próximos.
     * p_j_novo = Σ w_ij·(X̂_i,Ŷ_i) / Σ w_ij  onde w_ij = 1/d̂_ij
     */
    private void realocarCanhoes(List<Canhao> canhoesSnap,
                                  DataReconciliation.ReconciliationResult[] resultados) {
        for (int j = 0; j < canhoesSnap.size(); j++) {
            Canhao canhao = canhoesSnap.get(j);
            // Ignora canhões do lado esquerdo (controlados pelo jogador)
            if (canhao.getLado() == Lado.ESQUERDO) continue;

            float sumWX = 0, sumWY = 0, sumW = 0;

            for (DataReconciliation.ReconciliationResult r : resultados) {
                if (r.distanciasReconciliadas.length <= j) continue;
                float dist = r.distanciasReconciliadas[j];
                if (dist < 1f) dist = 1f; // Evitar divisão por zero

                // Filtrar apenas alvos do mesmo lado
                Lado ladoAlvo = Lado.determinar(r.x, larguraTela);
                if (ladoAlvo != canhao.getLado()) continue;

                float w = 1.0f / dist;
                sumWX += w * r.x;
                sumWY += w * r.y;
                sumW += w;
            }

            if (sumW > 0) {
                float novoX = sumWX / sumW;
                float novoY = sumWY / sumW;

                // Manter canhão no seu lado
                float metade = larguraTela / 2f;
                if (canhao.getLado() == Lado.ESQUERDO) {
                    novoX = Math.min(novoX, metade - 30);
                } else {
                    novoX = Math.max(novoX, metade + 30);
                }
                novoY = Math.max(50, Math.min(novoY, alturaTela - 50));

                float distMov = Alvo.calcularDistancia(canhao.getX(), canhao.getY(),
                        novoX, novoY);
                if (distMov > 30) { // Só mover se diferença significativa
                    canhao.moverPara(novoX, novoY);
                    if (listener != null) {
                        listener.onRealocarCanhao(canhao, novoX, novoY);
                    }
                }
            }
        }
    }

    /**
     * Avalia custo-benefício para adicionar/remover canhões.
     * Calcula U(N), U(N+1) e U(N-1) e decide via greedy.
     */
    private void avaliarCustoBeneficio(List<Canhao> canhoesSnap,
                                        DataReconciliation.ReconciliationResult[] resultados) {
        if (listener == null) return;

        for (Lado lado : Lado.values()) {
            // Ignora o lado esquerdo (controlado manualmente pelo jogador)
            if (lado == Lado.ESQUERDO) continue;

            // Contar canhões deste lado
            int nLado = 0;
            for (Canhao c : canhoesSnap) {
                if (c.getLado() == lado) nLado++;
            }

            // Construir matriz de distâncias para alvos deste lado
            List<float[]> distanciasLado = new ArrayList<>();
            List<DataReconciliation.ReconciliationResult> alvosLado = new ArrayList<>();
            for (DataReconciliation.ReconciliationResult r : resultados) {
                Lado ladoAlvo = Lado.determinar(r.x, larguraTela);
                if (ladoAlvo == lado) {
                    distanciasLado.add(r.distanciasReconciliadas);
                    alvosLado.add(r);
                }
            }

            if (distanciasLado.isEmpty() || nLado == 0) continue;

            float[][] distMatrix = distanciasLado.toArray(new float[0][]);

            // U(N) atual
            double uAtual = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            // U(N+1) hipotético
            double uMais1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado + 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            double ganhoMarginal = uMais1 - uAtual;

            // Decidir
            if (ganhoMarginal > LIMIAR_GANHO && nLado < 10) {
                // Calcular centroide dos alvos menos cobertos
                float cx = 0, cy = 0;
                for (DataReconciliation.ReconciliationResult r : alvosLado) {
                    cx += r.x;
                    cy += r.y;
                }
                cx /= alvosLado.size();
                cy /= alvosLado.size();

                Log.i(TAG, String.format("Sugestão: ADICIONAR canhão %s em (%.0f, %.0f) ΔU=%.3f",
                        lado.name(), cx, cy, ganhoMarginal));
                listener.onSugestaoAdicionarCanhao(lado, cx, cy);
            } else if (nLado > 1) {
                // Avaliar remoção
                double uMenos1 = DataReconciliation.calcularUtilidade(
                        distMatrix, nLado - 1,
                        Canhao.getLimiarPenalidade(),
                        Canhao.getAlphaPenalidade(), BETA,
                        Canhao.getIntervaloDisparoBase());

                double perdaMarginal = uAtual - uMenos1;
                if (perdaMarginal < LIMIAR_GANHO * 0.5) {
                    Log.i(TAG, String.format("Sugestão: REMOVER canhão %s (perda=%.3f)",
                            lado.name(), perdaMarginal));
                    listener.onSugestaoRemoverCanhao(lado);
                }
            }
        }
    }

    // ── Getters / Setters ────────────────────────────────────────

    public int getReconciliacoesRealizadas() { return reconciliacoesRealizadas; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }

    public void setListener(OnReconciliacaoListener listener) {
        this.listener = listener;
    }

    public void setLarguraTela(int largura) { this.larguraTela = largura; }
    public void setAlturaTela(int altura) { this.alturaTela = altura; }
}
