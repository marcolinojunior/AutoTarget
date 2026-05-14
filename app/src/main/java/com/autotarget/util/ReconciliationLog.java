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

    private int totalSpawns;
    private int totalShots;
    private int totalHits;
    private int totalAdditions;
    private int totalRemovals;

    private static final class ReconSample {
        final double mseBruto;
        final double mseRecon;
        final double erroPos;
        final double normA;
        final String lado;

        ReconSample(double mseBruto, double mseRecon, double erroPos, double normA, String lado) {
            this.mseBruto = mseBruto;
            this.mseRecon = mseRecon;
            this.erroPos = erroPos;
            this.normA = normA;
            this.lado = lado;
        }
    }

    private ReconciliationLog() {}

    public static ReconciliationLog getInstance() {
        return INSTANCE;
    }

    public synchronized void reset() {
        eventos.clear();
        reconSamples.clear();
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
        double mseBruto = mseDistancias(distBrutas, xReal, yReal, canhoesX, canhoesY);
        double mseRecon = mseDistancias(distRecon, xReal, yReal, canhoesX, canhoesY);
        double erroPos = Math.hypot(xEstimado - xReal, yEstimado - yReal);
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
        }

        sb.append("\nEVENTOS_RECENTES\n");
        int from = Math.max(0, eventos.size() - 40);
        for (int i = from; i < eventos.size(); i++) {
            sb.append("- ").append(eventos.get(i)).append('\n');
        }
        return sb.toString();
    }
}
