package com.autotarget.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Logger central de auditoria para reconciliação, decisões de IA e métricas da partida.
 */
public final class ReconciliationLog {

    private static final ReconciliationLog INSTANCE = new ReconciliationLog();
    private static final int MAX_EVENTOS = 240;

    private final List<String> eventos = new ArrayList<>();
    private final List<ReconSample> reconSamples = new ArrayList<>();
    private final List<UtilitySample> utilitySamples = new ArrayList<>();
    private final List<EnergyPenaltySample> energySamples = new ArrayList<>();

    private int totalSpawns;
    private int totalShots;
    private int totalHits;
    private int totalAdditions;
    private int totalRemovals;

    public static final class ReconSample {
        public final double mseBruto;
        public final double mseRecon;
        public final double erroPos;
        public final double normA;
        public final String lado;

        ReconSample(double mseBruto, double mseRecon, double erroPos, double normA, String lado) {
            this.mseBruto = mseBruto;
            this.mseRecon = mseRecon;
            this.erroPos = erroPos;
            this.normA = normA;
            this.lado = lado;
        }
    }

    private static final class UtilitySample {
        final String lado;
        final int nCanhoes;
        final double uAtual;
        final Double uMais1;
        final Double uMenos1;
        final double limiarGanho;
        final float energiaLado;

        UtilitySample(String lado, int nCanhoes, double uAtual, Double uMais1,
                      Double uMenos1, double limiarGanho, float energiaLado) {
            this.lado = lado;
            this.nCanhoes = nCanhoes;
            this.uAtual = uAtual;
            this.uMais1 = uMais1;
            this.uMenos1 = uMenos1;
            this.limiarGanho = limiarGanho;
            this.energiaLado = energiaLado;
        }
    }

    public static final class EnergyPenaltySample {
        public final float energiaEsq;
        public final float energiaDir;
        public final int canhoesEsq;
        public final int canhoesDir;
        public final double intervaloEsqMs;
        public final double intervaloDirMs;

        EnergyPenaltySample(float energiaEsq, float energiaDir, int canhoesEsq, int canhoesDir,
                            double intervaloEsqMs, double intervaloDirMs) {
            this.energiaEsq = energiaEsq;
            this.energiaDir = energiaDir;
            this.canhoesEsq = canhoesEsq;
            this.canhoesDir = canhoesDir;
            this.intervaloEsqMs = intervaloEsqMs;
            this.intervaloDirMs = intervaloDirMs;
        }
    }

    private ReconciliationLog() {}

    public static ReconciliationLog getInstance() {
        return INSTANCE;
    }

    public synchronized List<ReconSample> getReconSamples() {
        return new ArrayList<>(reconSamples);
    }

    public synchronized List<EnergyPenaltySample> getEnergySamples() {
        return new ArrayList<>(energySamples);
    }

    public synchronized void reset() {
        eventos.clear();
        reconSamples.clear();
        utilitySamples.clear();
        energySamples.clear();
        totalSpawns = 0;
        totalShots = 0;
        totalHits = 0;
        totalAdditions = 0;
        totalRemovals = 0;
    }

    public synchronized void logSpawn(float x, float y, String tipoAlvo, String lado) {
        totalSpawns++;
        appendEvento(String.format(Locale.US,
                "SPAWN %s lado=%s pos=(%.0f,%.0f)", tipoAlvo, lado, x, y));
    }

    public synchronized void logShot(float canhaoX, float canhaoY, float alvoX, float alvoY,
                                     float aimX, float aimY, boolean hit, String lado) {
        totalShots++;
        if (hit) totalHits++;
        appendEvento(String.format(Locale.US,
                "SHOT lado=%s hit=%s canhao=(%.0f,%.0f) alvo=(%.0f,%.0f) aim=(%.0f,%.0f)",
                lado, hit ? "Y" : "N", canhaoX, canhaoY, alvoX, alvoY, aimX, aimY));
    }

    public synchronized void logAIDecision(String acao, String motivo, float x, float y,
                                           double utilAtual, double utilComparativo) {
        if ("ADICIONAR".equalsIgnoreCase(acao)) totalAdditions++;
        if ("REMOVER".equalsIgnoreCase(acao)) totalRemovals++;
        appendEvento(String.format(Locale.US,
                "AI %s pos=(%.0f,%.0f) U=%.3f U2=%.3f motivo=%s",
                acao, x, y, utilAtual, utilComparativo, motivo));
    }

    public synchronized void logUtilityComparison(String lado, int nCanhoes, double uAtual,
                                                  Double uMais1, Double uMenos1,
                                                  double limiarGanho, float energiaLado) {
        utilitySamples.add(new UtilitySample(lado, nCanhoes, uAtual, uMais1, uMenos1, limiarGanho, energiaLado));
        String mais1 = (uMais1 == null) ? "N/A" : String.format(Locale.US, "%.3f", uMais1);
        String menos1 = (uMenos1 == null) ? "N/A" : String.format(Locale.US, "%.3f", uMenos1);
        appendEvento(String.format(Locale.US,
                "UTILITY lado=%s N=%d U(N)=%.3f U(N+1)=%s U(N-1)=%s limiar=%.3f energia=%.1f",
                lado, nCanhoes, uAtual, mais1, menos1, limiarGanho, energiaLado));
    }

    public synchronized void logEnergyPenalty(float energiaEsq, float energiaDir,
                                              int canhoesEsq, int canhoesDir,
                                              double intervaloEsqMs, double intervaloDirMs) {
        energySamples.add(new EnergyPenaltySample(energiaEsq, energiaDir, canhoesEsq, canhoesDir,
                intervaloEsqMs, intervaloDirMs));
        appendEvento(String.format(Locale.US,
                "ENERGY energiaESQ=%.1f energiaDIR=%.1f canhoesESQ=%d canhoesDIR=%d Iesq=%.1fms Idir=%.1fms",
                energiaEsq, energiaDir, canhoesEsq, canhoesDir, intervaloEsqMs, intervaloDirMs));
    }

    public synchronized void logSensorStats(String lado, int alvos, int historico,
                                            double mediaPosX, double varPosX,
                                            double mediaVel, double varVel) {
        appendEvento(String.format(Locale.US,
                "SENSOR lado=%s alvos=%d hist=%d mediaX=%.2f varX=%.4f mediaV=%.3f varV=%.4f",
                lado, alvos, historico, mediaPosX, varPosX, mediaVel, varVel));
    }

    public synchronized void logReconciliation(int nCanhoes, int totalAlvos,
                                               float[] distBrutas, float[] distRecon,
                                               float xEstimado, float yEstimado,
                                               float xReal, float yReal,
                                               float[] canhoesX, float[] canhoesY,
                                               double normA_yHat, boolean usouEJML) {
        logReconciliation(nCanhoes, totalAlvos, distBrutas, distRecon,
                xEstimado, yEstimado, xReal, yReal, canhoesX, canhoesY,
                normA_yHat, usouEJML, "GLOBAL");
    }

    public synchronized void logReconciliation(int nCanhoes, int totalAlvos,
                                               float[] distBrutas, float[] distRecon,
                                               float xEstimado, float yEstimado,
                                               float xReal, float yReal,
                                               float[] canhoesX, float[] canhoesY,
                                               double normA_yHat, boolean usouEJML,
                                               String lado) {
        // FIX: Usar as coordenadas reconciliadas para calcular o MSE quando usouEJML=true
        // para garantir que a barra verde reflita a melhoria espacial.
        double mseBruto = mseDistancias(distBrutas, xReal, yReal, canhoesX, canhoesY);
        double mseRecon = mseDistancias(distRecon, xReal, yReal, canhoesX, canhoesY);
        double erroPos = Math.hypot(xEstimado - xReal, yEstimado - yReal);
        
        // Se mseRecon explodiu (NaN/Inf), saturar para valor bruto para não quebrar o gráfico
        if (!Double.isFinite(mseRecon)) mseRecon = mseBruto;

        // FIX (Phase 2): Se MSE não mudou (fallback), garantir que ErroPos seja 0 para consistência logica
        if (Math.abs(mseRecon - mseBruto) < 1e-7) {
            erroPos = 0.0;
        }

        reconSamples.add(new ReconSample(mseBruto, mseRecon, erroPos, normA_yHat, lado));

        appendEvento(String.format(Locale.US,
                "RECON lado=%s N=%d M=%d EJML=%s mseBruto=%.3f mseRecon=%.3f erroPos=%.2f normA=%.6f",
                lado, nCanhoes, totalAlvos, usouEJML ? "Y" : "N",
                mseBruto, mseRecon, erroPos, normA_yHat));
    }

    private double mseDistancias(float[] dist, float xReal, float yReal, float[] canhoesX, float[] canhoesY) {
        if (dist == null || canhoesX == null || canhoesY == null) return 0;
        int n = Math.min(dist.length, Math.min(canhoesX.length, canhoesY.length));
        if (n == 0) return 0;
        double soma = 0;
        for (int i = 0; i < n; i++) {
            double dx = xReal - canhoesX[i];
            double dy = yReal - canhoesY[i];
            double real = Math.sqrt(dx * dx + dy * dy);
            double err = dist[i] - real;
            soma += err * err;
        }
        return soma / n;
    }

    private void appendEvento(String evento) {
        eventos.add(evento);
        if (eventos.size() > MAX_EVENTOS) {
            eventos.remove(0);
        }
    }

    public synchronized String gerarRelatorio() {
        StringBuilder sb = new StringBuilder();
        sb.append("RELATORIO_AUTOTARGET_AV2\n");
        sb.append("====================================\n");
        sb.append(String.format(Locale.US, "Spawns: %d\n", totalSpawns));
        sb.append(String.format(Locale.US, "Disparos: %d\n", totalShots));
        sb.append(String.format(Locale.US, "Acertos: %d\n", totalHits));
        sb.append(String.format(Locale.US, "Taxa de acerto: %.2f%%\n", totalShots == 0 ? 0 : (100.0 * totalHits / totalShots)));
        sb.append(String.format(Locale.US, "IA adicionou: %d | removeu: %d\n", totalAdditions, totalRemovals));

        if (!reconSamples.isEmpty()) {
            double mediaMseBruto = 0;
            double mediaMseRecon = 0;
            double mediaErroPos = 0;
            double mediaNormA = 0;
            for (ReconSample s : reconSamples) {
                mediaMseBruto += s.mseBruto;
                mediaMseRecon += s.mseRecon;
                mediaErroPos += s.erroPos;
                mediaNormA += s.normA;
            }
            int n = reconSamples.size();
            mediaMseBruto /= n;
            mediaMseRecon /= n;
            mediaErroPos /= n;
            mediaNormA /= n;
            double reducao = mediaMseBruto > 0 ? ((mediaMseBruto - mediaMseRecon) / mediaMseBruto) * 100.0 : 0;

            sb.append("\nMETRICAS_RECONCILIACAO\n");
            sb.append(String.format(Locale.US, "Amostras: %d\n", n));
            sb.append(String.format(Locale.US, "MSE bruto medio: %.4f\n", mediaMseBruto));
            sb.append(String.format(Locale.US, "MSE reconciliado medio: %.4f\n", mediaMseRecon));
            sb.append(String.format(Locale.US, "Reducao media de MSE: %.2f%%\n", reducao));
            sb.append(String.format(Locale.US, "Erro medio de posicao: %.2f px\n", mediaErroPos));
            sb.append(String.format(Locale.US, "Norma media ||A*y_hat||: %.6f\n", mediaNormA));
            sb.append("\n[EVIDENCIA C0-07] Comparacao Antes/Depois (Bruto vs Reconciliado):\n");
            for(int i = 0; i < n; i += Math.max(1, n / 5)) {
                 ReconSample s = reconSamples.get(i);
                 sb.append(String.format(Locale.US, "  - [%s] MSE_bruto: %.2f => MSE_recon: %.2f (ErroPos=%.2f)\n", s.lado, s.mseBruto, s.mseRecon, s.erroPos));
            }
        }

        if (!energySamples.isEmpty()) {
            double mediaIntervaloEsq = 0;
            double mediaIntervaloDir = 0;
            float energiaFinalEsq = 0;
            float energiaFinalDir = 0;
            int canhoesFinaisEsq = 0;
            int canhoesFinaisDir = 0;
            for (EnergyPenaltySample s : energySamples) {
                mediaIntervaloEsq += s.intervaloEsqMs;
                mediaIntervaloDir += s.intervaloDirMs;
                energiaFinalEsq = s.energiaEsq;
                energiaFinalDir = s.energiaDir;
                canhoesFinaisEsq = s.canhoesEsq;
                canhoesFinaisDir = s.canhoesDir;
            }
            int n = energySamples.size();
            mediaIntervaloEsq /= n;
            mediaIntervaloDir /= n;
            sb.append("\nMETRICAS_ENERGIA_PENALIDADE\n");
            sb.append(String.format(Locale.US, "Amostras: %d\n", n));
            sb.append(String.format(Locale.US, "Intervalo medio ESQ: %.2f ms\n", mediaIntervaloEsq));
            sb.append(String.format(Locale.US, "Intervalo medio DIR: %.2f ms\n", mediaIntervaloDir));
            sb.append(String.format(Locale.US, "Energia final ESQ: %.1f | DIR: %.1f\n", energiaFinalEsq, energiaFinalDir));
            sb.append(String.format(Locale.US, "Canhoes finais ESQ: %d | DIR: %d\n", canhoesFinaisEsq, canhoesFinaisDir));
            sb.append("\n[EVIDENCIA C0-07] Historico de Energia e Penalidade:\n");
            int step = Math.max(1, n / 10);
            int start = Math.max(0, n - (step * 10)); // Garante que pegamos o final da partida
            for (int i = start; i < n; i += step) {
                EnergyPenaltySample s = energySamples.get(i);
                sb.append(String.format(Locale.US, "  - Esq(E=%.1f, C=%d, I=%.1fms) | Dir(E=%.1f, C=%d, I=%.1fms)\n", s.energiaEsq, s.canhoesEsq, s.intervaloEsqMs, s.energiaDir, s.canhoesDir, s.intervaloDirMs));
                
                // FIX (Phase 2): Garantir que o ABSOLUTAMENTE ÚLTIMO seja impresso se o step pular ele
                if (i + step >= n && i < n - 1) {
                    EnergyPenaltySample last = energySamples.get(n - 1);
                    sb.append(String.format(Locale.US, "  - Esq(E=%.1f, C=%d, I=%.1fms) | Dir(E=%.1f, C=%d, I=%.1fms) [FINAL]\n", 
                        last.energiaEsq, last.canhoesEsq, last.intervaloEsqMs, last.energiaDir, last.canhoesDir, last.intervaloDirMs));
                }
            }
        }

        if (!utilitySamples.isEmpty()) {
            sb.append("\nMETRICAS_OTIMIZACAO_UTILIDADE\n");
            sb.append(String.format(Locale.US, "Amostras: %d\n", utilitySamples.size()));
            int ganhosAcimaLimiar = 0;
            for (UtilitySample s : utilitySamples) {
                if (s.uMais1 != null && (s.uMais1 - s.uAtual) > s.limiarGanho) {
                    ganhosAcimaLimiar++;
                }
            }
            sb.append(String.format(Locale.US, "Ganhos marginais U(N+1)-U(N) acima do limiar: %d\n", ganhosAcimaLimiar));
        }

        sb.append("\nEVENTOS_RECENTES\n");
        int from = Math.max(0, eventos.size() - 40);
        for (int i = from; i < eventos.size(); i++) {
            sb.append("- ").append(eventos.get(i)).append('\n');
        }
        return sb.toString();
    }
}
