package com.autotarget.util;

import com.autotarget.model.Lado;

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
    private final List<ConditioningSample> conditioningSamples = new ArrayList<>();
    private final List<SensorCountSample> sensorCountSamples = new ArrayList<>();
    private final List<StarvationSample> starvationSamples = new ArrayList<>();
    private final List<EnergyRestorationSample> energyRestorationSamples = new ArrayList<>();
    private final List<SensorVarianceSample> sensorVarianceSamples = new ArrayList<>();
    private final List<PerformanceMetricsSample> performanceMetricsSamples = new ArrayList<>();

    private int totalSpawns;
    private int totalShots;
    private int totalShotsEsq;
    private int totalShotsDir;
    private int totalHits;
    private int totalAdditions;
    private int totalRemovals;
    private int totalHitsEsq;
    private int totalHitsDir;

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

    public static final class UtilitySample {
        public final String lado;
        public final int nCanhoes;
        public final double uAtual;
        public final Double uMais1;
        public final Double uMenos1;
        public final double limiarGanho;
        public final float energiaLado;

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

    public static final class ConditioningSample {
        public final String contexto;
        public final int dimensao;
        public final double conditionNumber;
        public final boolean usouFallback;

        ConditioningSample(String contexto, int dimensao, double conditionNumber, boolean usouFallback) {
            this.contexto = contexto;
            this.dimensao = dimensao;
            this.conditionNumber = conditionNumber;
            this.usouFallback = usouFallback;
        }
    }

    public static final class SensorCountSample {
        public final String lado;
        public final int alvos;
        public final int historico;

        SensorCountSample(String lado, int alvos, int historico) {
            this.lado = lado;
            this.alvos = alvos;
            this.historico = historico;
        }
    }

    public static final class StarvationSample {
        public final int historicoEsq;
        public final int historicoDir;
        public final double evasaoEsq;
        public final double evasaoDir;

        StarvationSample(int historicoEsq, int historicoDir, double evasaoEsq, double evasaoDir) {
            this.historicoEsq = historicoEsq;
            this.historicoDir = historicoDir;
            this.evasaoEsq = evasaoEsq;
            this.evasaoDir = evasaoDir;
        }
    }

    public static final class EnergyRestorationSample {
        public final String lado;
        public final float energiaRestaurada;
        public final float energiaApos;
        public final int alvosAbatidosCumulativo;

        EnergyRestorationSample(String lado, float energiaRestaurada, float energiaApos, int alvosAbatidosCumulativo) {
            this.lado = lado;
            this.energiaRestaurada = energiaRestaurada;
            this.energiaApos = energiaApos;
            this.alvosAbatidosCumulativo = alvosAbatidosCumulativo;
        }
    }

    public static final class SensorVarianceSample {
        public final String lado;
        public final double mediaX;
        public final double varX;
        public final double mediaVelX;
        public final double varVelX;
        public final double mediaY;
        public final double varY;
        public final double mediaVelY;
        public final double varVelY;

        SensorVarianceSample(String lado, double mediaX, double varX, double mediaVelX, double varVelX,
                            double mediaY, double varY, double mediaVelY, double varVelY) {
            this.lado = lado;
            this.mediaX = mediaX;
            this.varX = varX;
            this.mediaVelX = mediaVelX;
            this.varVelX = varVelX;
            this.mediaY = mediaY;
            this.varY = varY;
            this.mediaVelY = mediaVelY;
            this.varVelY = varVelY;
        }
    }

    public static final class PerformanceMetricsSample {
        public final String lado;
        public final int disparosLado;
        public final int acertosLado;
        public final double taxaAcertoLado;
        public final double energiaConsumidaLado;
        public final int canhoesAtivosLado;

        PerformanceMetricsSample(String lado, int disparosLado, int acertosLado, double taxaAcertoLado,
                                double energiaConsumidaLado, int canhoesAtivosLado) {
            this.lado = lado;
            this.disparosLado = disparosLado;
            this.acertosLado = acertosLado;
            this.taxaAcertoLado = taxaAcertoLado;
            this.energiaConsumidaLado = energiaConsumidaLado;
            this.canhoesAtivosLado = canhoesAtivosLado;
        }
    }

    private ReconciliationLog() {}

    public static ReconciliationLog getInstance() {
        return INSTANCE;
    }

    public synchronized List<ReconSample> getReconSamples() { return new ArrayList<>(reconSamples); }
    public synchronized List<EnergyPenaltySample> getEnergySamples() { return new ArrayList<>(energySamples); }
    public synchronized List<UtilitySample> getUtilitySamples() { return new ArrayList<>(utilitySamples); }
    public synchronized List<ConditioningSample> getConditioningSamples() { return new ArrayList<>(conditioningSamples); }
    public synchronized List<SensorCountSample> getSensorCountSamples() { return new ArrayList<>(sensorCountSamples); }
    public synchronized List<StarvationSample> getStarvationSamples() { return new ArrayList<>(starvationSamples); }
    public synchronized List<EnergyRestorationSample> getEnergyRestorationSamples() { return new ArrayList<>(energyRestorationSamples); }
    public synchronized List<SensorVarianceSample> getSensorVarianceSamples() { return new ArrayList<>(sensorVarianceSamples); }
    public synchronized List<PerformanceMetricsSample> getPerformanceMetricsSamples() { return new ArrayList<>(performanceMetricsSamples); }

    public synchronized void reset() {
        eventos.clear();
        reconSamples.clear();
        utilitySamples.clear();
        energySamples.clear();
        conditioningSamples.clear();
        sensorCountSamples.clear();
        starvationSamples.clear();
        energyRestorationSamples.clear();
        sensorVarianceSamples.clear();
        performanceMetricsSamples.clear();
        totalSpawns = 0;
        totalShots = 0;
        totalShotsEsq = 0;
        totalShotsDir = 0;
        totalHits = 0;
        totalAdditions = 0;
        totalRemovals = 0;
        totalHitsEsq = 0;
        totalHitsDir = 0;
    }

    public synchronized void logSpawn(float x, float y, String tipoAlvo, String lado) {
        totalSpawns++;
        appendEvento(String.format(Locale.US, "SPAWN %s lado=%s pos=(%.0f,%.0f)", tipoAlvo, lado, x, y));
    }

    public synchronized void logShot(float canhaoX, float canhaoY, float alvoX, float alvoY,
                                     float aimX, float aimY, boolean hit, String lado) {
        if (!hit) {
            totalShots++;
            if ("ESQUERDO".equalsIgnoreCase(lado)) totalShotsEsq++;
            else if ("DIREITO".equalsIgnoreCase(lado)) totalShotsDir++;
        } else {
            totalHits++;
            if ("ESQUERDO".equalsIgnoreCase(lado)) totalHitsEsq++;
            else if ("DIREITO".equalsIgnoreCase(lado)) totalHitsDir++;
        }
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

    public synchronized void logConditioning(String contexto, int dimensao,
                                             double conditionNumber, boolean usouFallback) {
        conditioningSamples.add(new ConditioningSample(contexto, dimensao, conditionNumber, usouFallback));
        appendEvento(String.format(Locale.US,
                "COND contexto=%s dim=%d cond=%.3e fallback=%s",
                contexto, dimensao, conditionNumber, usouFallback ? "Y" : "N"));
    }

    public synchronized void logEvasion(Lado lado, String mensagem) {
        appendEvento(String.format(Locale.US, "EVADE lado=%s msg=%s", lado.name(), mensagem));
    }

    public synchronized void logStarvationState(int historicoEsq, int historicoDir,
                                                double evasaoEsq, double evasaoDir) {
        starvationSamples.add(new StarvationSample(historicoEsq, historicoDir, evasaoEsq, evasaoDir));
        appendEvento(String.format(Locale.US,
                "STARVATION histESQ=%d histDIR=%d evasaoESQ=%.2f evasaoDIR=%.2f",
                historicoEsq, historicoDir, evasaoEsq, evasaoDir));
    }

    public synchronized void logSensorStats(String lado, int alvos, int historico,
                                            double mediaPosX, double varPosX,
                                            double mediaVel, double varVel) {
        sensorCountSamples.add(new SensorCountSample(lado, alvos, historico));
        appendEvento(String.format(Locale.US,
                "SENSOR lado=%s alvos=%d hist=%d mediaX=%.2f varX=%.4f mediaV=%.3f varV=%.4f",
                lado, alvos, historico, mediaPosX, varPosX, mediaVel, varVel));
    }

    public synchronized void logEnergyRestoration(String lado, float energiaRestaurada, float energiaApos) {
        energyRestorationSamples.add(new EnergyRestorationSample(lado, energiaRestaurada, energiaApos,
                "ESQUERDO".equalsIgnoreCase(lado) ? totalHitsEsq : totalHitsDir));
        appendEvento(String.format(Locale.US,
                "ENERGY_RESTORATION lado=%s restaurada=%.1f energiaApos=%.1f",
                lado, energiaRestaurada, energiaApos));
    }

    public synchronized void logSensorVariance(String lado, double mediaX, double varX,
                                               double mediaVelX, double varVelX,
                                               double mediaY, double varY,
                                               double mediaVelY, double varVelY) {
        sensorVarianceSamples.add(new SensorVarianceSample(lado, mediaX, varX, mediaVelX, varVelX,
                mediaY, varY, mediaVelY, varVelY));
        appendEvento(String.format(Locale.US,
                "SENSOR_VARIANCE lado=%s X(mu=%.1f,var=%.2f) VX(mu=%.2f,var=%.2f) Y(mu=%.1f,var=%.2f) VY(mu=%.2f,var=%.2f)",
                lado, mediaX, varX, mediaVelX, varVelX, mediaY, varY, mediaVelY, varVelY));
    }

    public synchronized void logPerformanceMetrics(String lado, int disparosLado, int acertosLado,
                                                   double energiaConsumidaLado, int canhoesAtivosLado) {
        double taxaAcerto = disparosLado == 0 ? 0 : (100.0 * acertosLado / disparosLado);
        performanceMetricsSamples.add(new PerformanceMetricsSample(lado, disparosLado, acertosLado,
                taxaAcerto, energiaConsumidaLado, canhoesAtivosLado));
        appendEvento(String.format(Locale.US,
                "PERFORMANCE lado=%s disparos=%d acertos=%d taxa=%.2f%% consumo=%.1f canhoes=%d",
                lado, disparosLado, acertosLado, taxaAcerto, energiaConsumidaLado, canhoesAtivosLado));
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

        if (!Double.isFinite(mseRecon)) mseRecon = mseBruto;
        if (Math.abs(mseRecon - mseBruto) < 1e-7) erroPos = 0.0;

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

    /**
     * Retorna as últimas N decisões da IA como uma string formatada.
     * Usado para telemetria em tempo real na UI.
     */
    public synchronized String getUltimasDecisoes(int n) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = eventos.size() - 1; i >= 0 && count < n; i--) {
            String evento = eventos.get(i);
            if (evento.startsWith("AI ")) {
                sb.append(evento).append("\n");
                count++;
            }
        }
        return sb.toString();
    }

    public synchronized String gerarRelatorio() {
        StringBuilder sb = new StringBuilder();
        sb.append("RELATORIO_AUTOTARGET_AV2\n");
        sb.append("====================================\n");
        sb.append(String.format(Locale.US, "Spawns: %d\n", totalSpawns));
        int disparosEfetivos = totalShots + totalHits;
        sb.append(String.format(Locale.US, "Disparos: %d\n", disparosEfetivos));
        sb.append(String.format(Locale.US, "Acertos: %d\n", totalHits));
        sb.append(String.format(Locale.US, "Taxa de acerto: %.2f%%\n", disparosEfetivos == 0 ? 0 : (100.0 * totalHits / disparosEfetivos)));
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
            for (int i = 0; i < n; i += Math.max(1, n / 5)) {
                ReconSample s = reconSamples.get(i);
                sb.append(String.format(Locale.US, "  - [%s] MSE_bruto: %.2f => MSE_recon: %.2f (ErroPos=%.2f)\n",
                        s.lado, s.mseBruto, s.mseRecon, s.erroPos));
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
            int start = Math.max(0, n - (step * 10));
            for (int i = start; i < n; i += step) {
                EnergyPenaltySample s = energySamples.get(i);
                sb.append(String.format(Locale.US, "  - Esq(E=%.1f, C=%d, I=%.1fms) | Dir(E=%.1f, C=%d, I=%.1fms)\n",
                        s.energiaEsq, s.canhoesEsq, s.intervaloEsqMs, s.energiaDir, s.canhoesDir, s.intervaloDirMs));
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

        if (!conditioningSamples.isEmpty()) {
            sb.append("\nMETRICAS_CONDICIONAMENTO_NUMERICO\n");
            double mediaCond = 0;
            int fallbackCount = 0;
            for (ConditioningSample s : conditioningSamples) {
                mediaCond += s.conditionNumber;
                if (s.usouFallback) fallbackCount++;
            }
            mediaCond /= conditioningSamples.size();
            sb.append(String.format(Locale.US, "Amostras: %d\n", conditioningSamples.size()));
            sb.append(String.format(Locale.US, "Condition number medio: %.3e\n", mediaCond));
            sb.append(String.format(Locale.US, "Fallbacks acionados: %d\n", fallbackCount));
        }

        if (!energyRestorationSamples.isEmpty()) {
            sb.append("\n[EXCELENTE] METRICAS_ENERGIA_RESTAURADA\n");
            float totalRestauradoEsq = 0;
            float totalRestauradoDir = 0;
            int abatesEsq = 0;
            int abatesDir = 0;
            for (EnergyRestorationSample s : energyRestorationSamples) {
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                    totalRestauradoEsq += s.energiaRestaurada;
                    abatesEsq++;
                } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                    totalRestauradoDir += s.energiaRestaurada;
                    abatesDir++;
                }
            }
            sb.append(String.format(Locale.US, "[ESQUERDO] Abates: %d | Energia total restaurada: %.1f | Media por abate: %.2f\n",
                    abatesEsq, totalRestauradoEsq, abatesEsq > 0 ? totalRestauradoEsq / abatesEsq : 0));
            sb.append(String.format(Locale.US, "[DIREITO]  Abates: %d | Energia total restaurada: %.1f | Media por abate: %.2f\n",
                    abatesDir, totalRestauradoDir, abatesDir > 0 ? totalRestauradoDir / abatesDir : 0));
            sb.append("\n[EVIDENCIA] Historico de Restauracao (ultimos 20):\n");
            int start = Math.max(0, energyRestorationSamples.size() - 20);
            for (int i = start; i < energyRestorationSamples.size(); i++) {
                EnergyRestorationSample s = energyRestorationSamples.get(i);
                sb.append(String.format(Locale.US, "  - [%s] Restaurado: %.1f | Energia apos: %.1f | Abates cumulativos: %d\n",
                        s.lado, s.energiaRestaurada, s.energiaApos, s.alvosAbatidosCumulativo));
            }
        }

        if (!sensorVarianceSamples.isEmpty()) {
            sb.append("\n[EXCELENTE] ANALISE_ESTATISTICA_SENSORES\n");
            double mediaVarXEsq = 0, mediaVarYEsq = 0, mediaVarVelEsq = 0;
            double mediaVarXDir = 0, mediaVarYDir = 0, mediaVarVelDir = 0;
            int contEsq = 0, contDir = 0;
            for (SensorVarianceSample s : sensorVarianceSamples) {
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                    mediaVarXEsq += s.varX;
                    mediaVarYEsq += s.varY;
                    mediaVarVelEsq += (s.varVelX + s.varVelY) / 2.0;
                    contEsq++;
                } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                    mediaVarXDir += s.varX;
                    mediaVarYDir += s.varY;
                    mediaVarVelDir += (s.varVelX + s.varVelY) / 2.0;
                    contDir++;
                }
            }
            if (contEsq > 0) {
                mediaVarXEsq /= contEsq;
                mediaVarYEsq /= contEsq;
                mediaVarVelEsq /= contEsq;
                sb.append(String.format(Locale.US, "[ESQUERDO] Var(X): %.4f | Var(Y): %.4f | Var(Vel): %.4f\n",
                        mediaVarXEsq, mediaVarYEsq, mediaVarVelEsq));
            }
            if (contDir > 0) {
                mediaVarXDir /= contDir;
                mediaVarYDir /= contDir;
                mediaVarVelDir /= contDir;
                sb.append(String.format(Locale.US, "[DIREITO]  Var(X): %.4f | Var(Y): %.4f | Var(Vel): %.4f\n",
                        mediaVarXDir, mediaVarYDir, mediaVarVelDir));
            }
        }

        if (!performanceMetricsSamples.isEmpty()) {
            sb.append("\n[EXCELENTE] METRICAS_DESEMPENHO_POR_LADO\n");
            double taxaAcertoEsqMed = 0, consumoEnergiEsq = 0;
            double taxaAcertoDirMed = 0, consumoEnergiDir = 0;
            int contEsq = 0, contDir = 0;
            for (PerformanceMetricsSample s : performanceMetricsSamples) {
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                    taxaAcertoEsqMed += s.taxaAcertoLado;
                    consumoEnergiEsq += s.energiaConsumidaLado;
                    contEsq++;
                } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                    taxaAcertoDirMed += s.taxaAcertoLado;
                    consumoEnergiDir += s.energiaConsumidaLado;
                    contDir++;
                }
            }

            int disparosEsqTotal = totalShotsEsq + totalHitsEsq;
            int disparosDirTotal = totalShotsDir + totalHitsDir;
            double taxaAcertoEsqReal = disparosEsqTotal == 0 ? 0 : (100.0 * totalHitsEsq / disparosEsqTotal);
            double taxaAcertoDirReal = disparosDirTotal == 0 ? 0 : (100.0 * totalHitsDir / disparosDirTotal);

            if (contEsq > 0) {
                sb.append(String.format(Locale.US, "[ESQUERDO] Taxa real: %.2f%% (Acertos: %d, Disparos: %d) | Taxa media amostrada: %.2f%% | Consumo medio: %.1f\n",
                        taxaAcertoEsqReal, totalHitsEsq, disparosEsqTotal, taxaAcertoEsqMed / contEsq, consumoEnergiEsq / contEsq));
            }
            if (contDir > 0) {
                sb.append(String.format(Locale.US, "[DIREITO]  Taxa real: %.2f%% (Acertos: %d, Disparos: %d) | Taxa media amostrada: %.2f%% | Consumo medio: %.1f\n",
                        taxaAcertoDirReal, totalHitsDir, disparosDirTotal, taxaAcertoDirMed / contDir, consumoEnergiDir / contDir));
            }
            sb.append("\n[EVIDENCIA] Comparacao de Desempenho Final:\n");
            sb.append(String.format(Locale.US, "Vencedor por Abates: %s (ESQ=%d | DIR=%d)\n",
                    totalHitsEsq > totalHitsDir ? "ESQUERDO" : totalHitsDir > totalHitsEsq ? "DIREITO" : "EMPATE",
                    totalHitsEsq, totalHitsDir));
        }

        sb.append("\nEVENTOS_RECENTES\n");
        int from = Math.max(0, eventos.size() - 40);
        for (int i = from; i < eventos.size(); i++) {
            sb.append("- ").append(eventos.get(i)).append('\n');
        }
        return sb.toString();
    }
}