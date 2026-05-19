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
    private int totalSpawnsEsq;
    private int totalSpawnsDir;
    private int totalShots;
    private int totalShotsEsq;
    private int totalShotsDir;
    private int totalHits;
    private int totalHitsEsq;
    private int totalHitsDir;
    private int totalAdditions;
    private int totalAdditionsEsq;
    private int totalAdditionsDir;
    private int totalRemovals;
    private int totalRemovalsEsq;
    private int totalRemovalsDir;

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
        totalSpawnsEsq = 0;
        totalSpawnsDir = 0;
        totalShots = 0;
        totalShotsEsq = 0;
        totalShotsDir = 0;
        totalHits = 0;
        totalHitsEsq = 0;
        totalHitsDir = 0;
        totalAdditions = 0;
        totalAdditionsEsq = 0;
        totalAdditionsDir = 0;
        totalRemovals = 0;
        totalRemovalsEsq = 0;
        totalRemovalsDir = 0;
    }

    public synchronized void logSpawn(float x, float y, String tipoAlvo, String lado) {
        totalSpawns++;
        if ("ESQUERDO".equalsIgnoreCase(lado)) totalSpawnsEsq++;
        else if ("DIREITO".equalsIgnoreCase(lado)) totalSpawnsDir++;
        appendEvento(String.format(Locale.US, "SPAWN %s lado=%s pos=(%.0f,%.0f)", tipoAlvo, lado, x, y));
    }

    public synchronized void logShot(float canhaoX, float canhaoY, float alvoX, float alvoY,
                                     float aimX, float aimY, boolean hit, String lado) {
        // TODO DISPARO (independente de acerto)
        totalShots++;
        if ("ESQUERDO".equalsIgnoreCase(lado)) totalShotsEsq++;
        else if ("DIREITO".equalsIgnoreCase(lado)) totalShotsDir++;
        // APENAS ACERTOS
        if (hit) {
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
        // Tentar extrair o lado do motivo (ex: "Pressão tática ESQUERDO" ou "Pressão tática DIREITO")
        String ladoExtraido = extrairLadoDoMotivo(motivo);
        if ("ADICIONAR".equalsIgnoreCase(acao)) {
            if ("ESQUERDO".equalsIgnoreCase(ladoExtraido)) totalAdditionsEsq++;
            else if ("DIREITO".equalsIgnoreCase(ladoExtraido)) totalAdditionsDir++;
        }
        if ("REMOVER".equalsIgnoreCase(acao)) {
            if ("ESQUERDO".equalsIgnoreCase(ladoExtraido)) totalRemovalsEsq++;
            else if ("DIREITO".equalsIgnoreCase(ladoExtraido)) totalRemovalsDir++;
        }
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
        // FIX: Não zerar artificialmente o erro posicional; manter o valor real calculado
        // para evidência da qualidade da reconciliação (AV2).

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

    /**
     * Calcula médias de reconciliação para uma lista de amostras.
     * Retorna array: [mseBruto, mseRecon, reducao%, erroPos, normA] ou null se vazio.
     */
    private static double[] calcularMediasRecon(List<ReconSample> amostras) {
        if (amostras == null || amostras.isEmpty()) return null;
        double mseBruto = 0, mseRecon = 0, erroPos = 0, normA = 0;
        for (ReconSample s : amostras) {
            mseBruto += s.mseBruto;
            mseRecon += s.mseRecon;
            erroPos += s.erroPos;
            normA += s.normA;
        }
        int n = amostras.size();
        mseBruto /= n;
        mseRecon /= n;
        erroPos /= n;
        normA /= n;
        double reducao = mseBruto > 0 ? ((mseBruto - mseRecon) / mseBruto) * 100.0 : 0;
        return new double[]{mseBruto, mseRecon, reducao, erroPos, normA};
    }

    /**
     * Extrai o lado (ESQUERDI/DIREITO) de uma string de motivo da IA.
     * Ex: "Pressão tática ESQUERDO" -> "ESQUERDO"
     */
    private static String extrairLadoDoMotivo(String motivo) {
        if (motivo == null) return "";
        if (motivo.contains("ESQUERDO")) return "ESQUERDO";
        if (motivo.contains("DIREITO")) return "DIREITO";
        return "";
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

        // --- Cabeçalho separado por lado ---
        int disparosEsq = totalShotsEsq + totalHitsEsq;
        int disparosDir = totalShotsDir + totalHitsDir;
        int disparosGlobal = totalShots + totalHits;
        double taxaEsq = disparosEsq == 0 ? 0 : (100.0 * totalHitsEsq / disparosEsq);
        double taxaDir = disparosDir == 0 ? 0 : (100.0 * totalHitsDir / disparosDir);
        double taxaGlobal = disparosGlobal == 0 ? 0 : (100.0 * totalHits / disparosGlobal);

        sb.append(String.format(Locale.US, "[ESQUERDO] Spawns: %d | Disparos: %d | Acertos: %d | Taxa: %.1f%%\n",
                totalSpawnsEsq, disparosEsq, totalHitsEsq, taxaEsq));
        sb.append(String.format(Locale.US, "[DIREITO]  Spawns: %d | Disparos: %d | Acertos: %d | Taxa: %.1f%%\n",
                totalSpawnsDir, disparosDir, totalHitsDir, taxaDir));
        sb.append(String.format(Locale.US, "[GLOBAL]   Spawns: %d | Disparos: %d | Acertos: %d | Taxa: %.1f%%\n",
                totalSpawns, disparosGlobal, totalHits, taxaGlobal));
        sb.append(String.format(Locale.US, "IA adicionou: ESQ=%d DIR=%d (total=%d) | removeu: ESQ=%d DIR=%d (total=%d)\n",
                totalAdditionsEsq, totalAdditionsDir, totalAdditions,
                totalRemovalsEsq, totalRemovalsDir, totalRemovals));

        if (!reconSamples.isEmpty()) {
            // Separar amostras por lado
            List<ReconSample> amostrasEsq = new ArrayList<>();
            List<ReconSample> amostrasDir = new ArrayList<>();
            for (ReconSample s : reconSamples) {
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) amostrasEsq.add(s);
                else if ("DIREITO".equalsIgnoreCase(s.lado)) amostrasDir.add(s);
            }

            // Calcular médias globais
            double mediaMseBruto = 0, mediaMseRecon = 0, mediaErroPos = 0, mediaNormA = 0;
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

            // Calcular médias por lado
            double[] mediasEsq = calcularMediasRecon(amostrasEsq);
            double[] mediasDir = calcularMediasRecon(amostrasDir);

            sb.append("\nMETRICAS_RECONCILIACAO\n");
            sb.append(String.format(Locale.US, "Amostras: %d (ESQ=%d, DIR=%d)\n", n, amostrasEsq.size(), amostrasDir.size()));
            sb.append(String.format(Locale.US, "[GLOBAL]   MSE bruto medio: %.4f | MSE recon: %.4f | Reducao: %.2f%% | ErroPos: %.2f px | NormA: %.6f\n",
                    mediaMseBruto, mediaMseRecon, reducao, mediaErroPos, mediaNormA));
            if (mediasEsq != null) {
                sb.append(String.format(Locale.US, "[ESQUERDO] MSE bruto medio: %.4f | MSE recon: %.4f | Reducao: %.2f%% | ErroPos: %.2f px | NormA: %.6f\n",
                        mediasEsq[0], mediasEsq[1], mediasEsq[2], mediasEsq[3], mediasEsq[4]));
            }
            if (mediasDir != null) {
                sb.append(String.format(Locale.US, "[DIREITO]  MSE bruto medio: %.4f | MSE recon: %.4f | Reducao: %.2f%% | ErroPos: %.2f px | NormA: %.6f\n",
                        mediasDir[0], mediasDir[1], mediasDir[2], mediasDir[3], mediasDir[4]));
            }
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
            int ganhosAcimaLimiarEsq = 0, ganhosAcimaLimiarDir = 0;
            int contEsq = 0, contDir = 0;
            for (UtilitySample s : utilitySamples) {
                if (s.uMais1 != null && (s.uMais1 - s.uAtual) > s.limiarGanho) {
                    ganhosAcimaLimiar++;
                    if ("ESQUERDO".equalsIgnoreCase(s.lado)) ganhosAcimaLimiarEsq++;
                    else if ("DIREITO".equalsIgnoreCase(s.lado)) ganhosAcimaLimiarDir++;
                }
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) contEsq++;
                else if ("DIREITO".equalsIgnoreCase(s.lado)) contDir++;
            }
            sb.append(String.format(Locale.US, "[GLOBAL]   Ganhos marginais U(N+1)-U(N) acima do limiar: %d\n", ganhosAcimaLimiar));
            sb.append(String.format(Locale.US, "[ESQUERDO] Ganhos marginais acima do limiar: %d (amostras: %d)\n", ganhosAcimaLimiarEsq, contEsq));
            sb.append(String.format(Locale.US, "[DIREITO]  Ganhos marginais acima do limiar: %d (amostras: %d)\n", ganhosAcimaLimiarDir, contDir));
        }

        if (!conditioningSamples.isEmpty()) {
            sb.append("\nMETRICAS_CONDICIONAMENTO_NUMERICO\n");
            double mediaCond = 0;
            int fallbackCount = 0;
            double mediaCondEsq = 0, mediaCondDir = 0;
            int fallbackEsq = 0, fallbackDir = 0;
            int contEsq = 0, contDir = 0;
            for (ConditioningSample s : conditioningSamples) {
                mediaCond += s.conditionNumber;
                if (s.usouFallback) fallbackCount++;
                if (s.contexto != null && s.contexto.contains("ESQ")) {
                    mediaCondEsq += s.conditionNumber;
                    if (s.usouFallback) fallbackEsq++;
                    contEsq++;
                } else if (s.contexto != null && s.contexto.contains("DIR")) {
                    mediaCondDir += s.conditionNumber;
                    if (s.usouFallback) fallbackDir++;
                    contDir++;
                }
            }
            mediaCond /= conditioningSamples.size();
            sb.append(String.format(Locale.US, "Amostras: %d\n", conditioningSamples.size()));
            sb.append(String.format(Locale.US, "[GLOBAL]   Condition number medio: %.3e | Fallbacks: %d\n", mediaCond, fallbackCount));
            if (contEsq > 0) {
                mediaCondEsq /= contEsq;
                sb.append(String.format(Locale.US, "[ESQUERDO] Condition number medio: %.3e | Fallbacks: %d\n", mediaCondEsq, fallbackEsq));
            }
            if (contDir > 0) {
                mediaCondDir /= contDir;
                sb.append(String.format(Locale.US, "[DIREITO]  Condition number medio: %.3e | Fallbacks: %d\n", mediaCondDir, fallbackDir));
            }
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

        // ── H7: Relatório RMA estruturado ──
        sb.append("\n[EXCELENTE] ANALISE_RMA_TEMPO_REAL\n");
        String rmaReport = RMAAnalysis.getRuntimeMetricsReport();
        if (rmaReport != null && rmaReport.length() > "RUNTIME_METRICS\n".length()) {
            sb.append(rmaReport);
        } else {
            sb.append("Nenhuma métrica de runtime RMA coletada.\n");
        }

        sb.append("\nEVENTOS_RECENTES\n");
        int from = Math.max(0, eventos.size() - 40);
        for (int i = from; i < eventos.size(); i++) {
            sb.append("- ").append(eventos.get(i)).append('\n');
        }
        return sb.toString();
    }

    // ── TELEMETRIA CSV (Exigência "Excelente" — AV2) ─────────────────────

    /**
     * Exporta todos os dados de telemetria em arquivos CSV estruturados
     * para geração de gráficos no relatório PDF.
     *
     * Arquivos gerados:
     *   - telemetry_reconciliation.csv: MSE bruto, MSE reconciliado, erro posicional, normA
     *   - telemetry_energy_penalty.csv: energia por lado, canhões, intervalo de disparo
     *   - telemetry_rma_runtime.csv: Ci observado, deadline misses por tarefa
     *   - telemetry_sensor_variance.csv: variância dos sensores por lado
     *   - telemetry_utility.csv: função de utilidade U(N) por ciclo
     *
     * @param context Context do Android para acesso ao armazenamento interno
     * @return lista de nomes dos arquivos gerados
     */
    public synchronized java.util.List<String> exportarCSV(android.content.Context context) {
        java.util.List<String> arquivos = new java.util.ArrayList<>();
        if (context == null) return arquivos;

        // 1. CSV de Reconciliação
        try {
            String filename = "telemetry_reconciliation.csv";
            StringBuilder sb = new StringBuilder();
            sb.append("Index,MSE_Bruto,MSE_Reconciliado,Reducao_Pct,Erro_Posicional_px,NormA_yHat,Lado\n");
            for (int i = 0; i < reconSamples.size(); i++) {
                ReconSample s = reconSamples.get(i);
                double reducao = s.mseBruto > 0 ? ((s.mseBruto - s.mseRecon) / s.mseBruto) * 100.0 : 0;
                sb.append(String.format(Locale.US, "%d,%.6f,%.6f,%.2f,%.4f,%.8f,%s\n",
                        i, s.mseBruto, s.mseRecon, reducao, s.erroPos, s.normA, s.lado));
            }
            writeCSV(context, filename, sb.toString());
            arquivos.add(filename);
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de reconciliação", e);
        }

        // 2. CSV de Energia e Penalidade
        try {
            String filename = "telemetry_energy_penalty.csv";
            StringBuilder sb = new StringBuilder();
            sb.append("Index,Energia_Esq,Energia_Dir,Canhoes_Esq,Canhoes_Dir,Intervalo_Esq_ms,Intervalo_Dir_ms\n");
            for (int i = 0; i < energySamples.size(); i++) {
                EnergyPenaltySample s = energySamples.get(i);
                sb.append(String.format(Locale.US, "%d,%.2f,%.2f,%d,%d,%.2f,%.2f\n",
                        i, s.energiaEsq, s.energiaDir, s.canhoesEsq, s.canhoesDir,
                        s.intervaloEsqMs, s.intervaloDirMs));
            }
            writeCSV(context, filename, sb.toString());
            arquivos.add(filename);
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de energia", e);
        }

        // 3. CSV de RMA Runtime Metrics
        try {
            String filename = "telemetry_rma_runtime.csv";
            String rmaCSV = RMAAnalysis.getRuntimeMetricsReport();
            if (rmaCSV != null && !rmaCSV.isEmpty()) {
                writeCSV(context, filename, rmaCSV);
                arquivos.add(filename);
            }
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de RMA", e);
        }

        // 4. CSV de Variância dos Sensores
        try {
            String filename = "telemetry_sensor_variance.csv";
            StringBuilder sb = new StringBuilder();
            sb.append("Index,Lado,Media_X,Var_X,Media_Y,Var_Y,Media_VelX,Var_VelX,Media_VelY,Var_VelY\n");
            for (int i = 0; i < sensorVarianceSamples.size(); i++) {
                SensorVarianceSample s = sensorVarianceSamples.get(i);
                sb.append(String.format(Locale.US, "%d,%s,%.4f,%.6f,%.4f,%.6f,%.4f,%.6f,%.4f,%.6f\n",
                        i, s.lado, s.mediaX, s.varX, s.mediaY, s.varY,
                        s.mediaVelX, s.varVelX, s.mediaVelY, s.varVelY));
            }
            writeCSV(context, filename, sb.toString());
            arquivos.add(filename);
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de sensores", e);
        }

        // 5. CSV de Utilidade U(N)
        try {
            String filename = "telemetry_utility.csv";
            StringBuilder sb = new StringBuilder();
            sb.append("Index,Lado,N_Canhoes,U_Atual,U_Mais1,U_Menos1,Limiar_Ganho,Energia_Lado\n");
            for (int i = 0; i < utilitySamples.size(); i++) {
                UtilitySample s = utilitySamples.get(i);
                sb.append(String.format(Locale.US, "%d,%s,%d,%.6f,%s,%s,%.4f,%.2f\n",
                        i, s.lado, s.nCanhoes, s.uAtual,
                        s.uMais1 != null ? String.format(Locale.US, "%.6f", s.uMais1) : "N/A",
                        s.uMenos1 != null ? String.format(Locale.US, "%.6f", s.uMenos1) : "N/A",
                        s.limiarGanho, s.energiaLado));
            }
            writeCSV(context, filename, sb.toString());
            arquivos.add(filename);
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de utilidade", e);
        }

        // 6. CSV de Restauração de Energia
        try {
            String filename = "telemetry_energy_restoration.csv";
            StringBuilder sb = new StringBuilder();
            sb.append("Index,Lado,Energia_Restaurada,Energia_Apos,Abates_Cumulativos\n");
            for (int i = 0; i < energyRestorationSamples.size(); i++) {
                EnergyRestorationSample s = energyRestorationSamples.get(i);
                sb.append(String.format(Locale.US, "%d,%s,%.2f,%.2f,%d\n",
                        i, s.lado, s.energiaRestaurada, s.energiaApos, s.alvosAbatidosCumulativo));
            }
            writeCSV(context, filename, sb.toString());
            arquivos.add(filename);
        } catch (Exception e) {
            android.util.Log.e("TelemetryCSV", "Erro ao exportar CSV de restauração", e);
        }

        android.util.Log.i("TelemetryCSV",
                "Telemetria exportada: " + arquivos.size() + " arquivos CSV gerados");
        return arquivos;
    }

    /**
     * Escreve conteúdo em um arquivo CSV no armazenamento interno do app.
     */
    private static void writeCSV(android.content.Context context, String filename, String content) throws java.io.IOException {
        try (java.io.FileOutputStream fos = context.openFileOutput(filename, android.content.Context.MODE_PRIVATE)) {
            fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            android.util.Log.i("TelemetryCSV", "CSV gerado: " + filename
                    + " (" + content.length() + " bytes)");
        }
    }

    /**
     * Retorna a lista de nomes dos arquivos CSV gerados na última exportação.
     * Útil para a UI listar e compartilhar os arquivos.
     */
    public static String[] getCSVFilenames() {
        return new String[]{
                "telemetry_reconciliation.csv",
                "telemetry_energy_penalty.csv",
                "telemetry_rma_runtime.csv",
                "telemetry_sensor_variance.csv",
                "telemetry_utility.csv",
                "telemetry_energy_restoration.csv"
        };
    }
}