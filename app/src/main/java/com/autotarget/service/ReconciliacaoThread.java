package com.autotarget.service;

import android.util.Log;

import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.util.DataReconciliation;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ReconciliationLog;
import com.autotarget.util.ReconciliationVisualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thread periódica de reconciliação e otimização tática para ambos os lados.
 */
public class ReconciliacaoThread extends Thread {

    private static final String TAG = "ReconciliacaoThread";
    private static final double BETA = 0.005;
    private static final double LIMIAR_GANHO = 0.01;
    private static final float ENERGIA_SEGURA_MINIMA = 3f;
    private static final float ENERGIA_CRITICA = 5f;
    private static final float RAZAO_PRESSAO_TATICA = 1.0f;
    private static final int MAX_CANHOES_POR_LADO = 10;
    private static final int INTERVALO_TATICO = 3000;
    private static final int INTERVALO_RECONCILIACAO = 10000;

    private final DataReconciliation dataReconciliation;
    private final SensorThread sensorThread;
    private final com.autotarget.engine.Jogo jogo;
    private final Object sensorLock;
    private final Object collisionLock;

    private volatile boolean ativo;
    private volatile int reconciliacoesRealizadas;
    private volatile int ciclosTaticos;
    private volatile int larguraTela;
    private volatile int alturaTela;
    private OnReconciliacaoListener listener;

    private OnReconciliacaoListener getListener() {
        return listener;
    }

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
        this.ciclosTaticos = 0;
        setDaemon(true);
    }

    @Override
    public void run() {
        com.autotarget.util.ThreadAffinityHelper.setAffinityForBackgroundTask(android.os.Process.myTid());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            avaliarPressaoTatica(Lado.ESQUERDO);
            avaliarPressaoTatica(Lado.DIREITO);
            long ultimoCicloReconciliacaoMs = System.currentTimeMillis();

            while (ativo) {
                long startNs = System.nanoTime();
                try {
                    Thread.sleep(INTERVALO_TATICO);
                    if (!ativo) break;

                    avaliarPressaoTatica(Lado.ESQUERDO);
                    avaliarPressaoTatica(Lado.DIREITO);
                    ciclosTaticos++;
                    long agora = System.currentTimeMillis();
                    if (agora - ultimoCicloReconciliacaoMs >= INTERVALO_RECONCILIACAO) {
                        executarReconciliacaoCompleta();
                        ultimoCicloReconciliacaoMs = agora;
                    }

                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    RMAAnalysis.checkDeadline("T8-Reconciliacao", elapsedMs, INTERVALO_TATICO);
                } catch (InterruptedException e) {
                    Log.w(TAG, "ReconciliacaoThread interrompida.");
                    Thread.currentThread().interrupt();
                    ativo = false;
                } catch (Exception e) {
                    Log.e(TAG, "Erro no loop tático/reconciliação", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERRO FATAL na ReconciliacaoThread", e);
        } finally {
            Log.i(TAG, "ReconciliacaoThread finalizada.");
        }
    }

    private void executarReconciliacaoCompleta() {
        try {
            int reconciliacoesNoCiclo = 0;
            for (Lado lado : Lado.values()) {
                if (executarReconciliacaoPorLado(lado)) {
                    reconciliacoesNoCiclo++;
                }
            }

            if (reconciliacoesNoCiclo > 0) {
                reconciliacoesRealizadas += reconciliacoesNoCiclo;
                OnReconciliacaoListener l = getListener();
                if (l != null) l.onReconciliacaoConcluida(reconciliacoesRealizadas);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação", e);
        }
    }

    private boolean executarReconciliacaoPorLado(Lado lado) {
        List<Canhao> canhoesLado = coletarCanhoesAtivos(lado);
        float[][] mediaD = sensorThread.getMediaDistancias(lado);
        float[][] varD = sensorThread.getVarianciaDistancias(lado);
        if (!validarPrecondicoesReconciliacao(lado, canhoesLado, mediaD, varD)) return false;

        int nCanhoes = canhoesLado.size();
        float[] canhoesX = new float[nCanhoes];
        float[] canhoesY = new float[nCanhoes];
        for (int j = 0; j < nCanhoes; j++) {
            canhoesX[j] = canhoesLado.get(j).getX();
            canhoesY[j] = canhoesLado.get(j).getY();
        }

        DataReconciliation.ReconciliationResult[] resultados =
                dataReconciliation.reconciliar(canhoesX, canhoesY, mediaD, varD);
        if (resultados == null || resultados.length == 0) return false;

        float[] verdadeiroX = sensorThread.getVerdadeiroPosX(lado);
        float[] verdadeiroY = sensorThread.getVerdadeiroPosY(lado);
        logReconciliationResults(resultados, canhoesX, canhoesY, mediaD, verdadeiroX, verdadeiroY, nCanhoes, lado);

        avaliarCustoBeneficio(lado, canhoesLado, resultados);
        realocarCanhoes(lado, canhoesLado, resultados);

        Log.i(TAG, String.format(Locale.US,
                "Reconciliação lado %s concluída com %d alvos", lado.name(), resultados.length));
        return true;
    }

    private void avaliarPressaoTatica(Lado lado) {
        OnReconciliacaoListener l = getListener();
        if (l == null || larguraTela <= 0 || alturaTela <= 0) return;

        int nCanhoes = jogo.contarCanhoesAtivos(lado);
        int nAlvos = lado == Lado.ESQUERDO ? jogo.getAlvosEsquerdo().size() : jogo.getAlvosDireito().size();
        float energia = jogo.getEnergia(lado);

        if (nCanhoes == 0) {
            semearCanhoesIniciais(lado);
            return;
        }

        if (nAlvos > 0 && nCanhoes < MAX_CANHOES_POR_LADO && energia > ENERGIA_SEGURA_MINIMA) {
            float ratio = (float) nAlvos / nCanhoes;
            if (ratio > RAZAO_PRESSAO_TATICA) {
                float novoX = calcularPosicaoEstrategica(lado, nCanhoes);
                float novoY = calcularAlturaEstrategica(nCanhoes);
                ReconciliationLog.getInstance().logAIDecision(
                        "ADICIONAR", "Pressão tática " + lado.name() + " (ratio=" + String.format(Locale.US, "%.2f", ratio) + ")",
                        novoX, novoY, 0, 0);
                l.onSugestaoAdicionarCanhao(lado, novoX, novoY);
            }
        }

        if (nAlvos == 0 && nCanhoes > 2) {
            Canhao ultimo = obterUltimoCanhaoAtivo(lado);
            if (ultimo != null) {
                ReconciliationLog.getInstance().logAIDecision(
                        "REMOVER", "Sem alvos no território " + lado.name(),
                        ultimo.getX(), ultimo.getY(), 0, 0);
                jogo.removerCanhao(ultimo);
            }
        }

        if (energia < ENERGIA_CRITICA && nCanhoes > 1) {
            Canhao ultimo = obterUltimoCanhaoAtivo(lado);
            if (ultimo != null) {
                ReconciliationLog.getInstance().logAIDecision(
                        "REMOVER", "Energia crítica " + lado.name() + " (" + (int) energia + ")",
                        ultimo.getX(), ultimo.getY(), 0, 0);
                jogo.removerCanhao(ultimo);
            }
        }
    }

    private Canhao obterUltimoCanhaoAtivo(Lado lado) {
        synchronized(jogo.getCanhoesLock()) {
            List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            for (int i = lista.size() - 1; i >= 0; i--) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) return c;
            }
        }
        return null;
    }

    private List<Canhao> coletarCanhoesAtivos(Lado lado) {
        List<Canhao> canhoes;
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            canhoes = new ArrayList<>(Math.max(4, origem.size()));
            for (int i = 0; i < origem.size(); i++) {
                Canhao c = origem.get(i);
                if (c != null && c.isAtivo()) canhoes.add(c);
            }
        }
        return canhoes;
    }

    private boolean validarPrecondicoesReconciliacao(Lado lado,
                                                     List<Canhao> canhoesLado,
                                                     float[][] mediaD,
                                                     float[][] varD) {
        if (sensorThread.getHistoricoCount(lado) < SensorThread.getHistoricoMinimoReconciliacao()) {
            Log.d(TAG, "Pulando reconciliação: histórico insuficiente para lado " + lado.name());
            return false;
        }
        if (canhoesLado == null || canhoesLado.isEmpty()) return false;
        if (mediaD == null || varD == null || mediaD.length == 0 || mediaD[0].length == 0) return false;
        if (mediaD[0].length != canhoesLado.size()) {
            Log.d(TAG, "Pulando reconciliação: tamanho de canhões mudou durante janela");
            return false;
        }
        return true;
    }

    private float calcularPosicaoEstrategica(Lado lado, int canhoesExistentes) {
        float[] colunas = lado == Lado.DIREITO
                ? new float[]{0.6f, 0.75f, 0.9f}
                : new float[]{0.1f, 0.25f, 0.4f};
        return escolherCyclic(colunas, canhoesExistentes, larguraTela);
    }

    private float calcularAlturaEstrategica(int canhoesExistentes) {
        float[] alturas = {0.25f, 0.5f, 0.75f, 0.15f, 0.85f, 0.4f, 0.6f, 0.35f};
        float y = escolherCyclic(alturas, canhoesExistentes, alturaTela);
        return Math.max(90, Math.min(y, alturaTela - 90));
    }

    private float escolherCyclic(float[] ratios, int index, float escala) {
        if (ratios == null || ratios.length == 0) return 0f;
        int idx = Math.abs(index) % ratios.length;
        return escala * ratios[idx];
    }

    private void avaliarCustoBeneficio(Lado lado,
                                       List<Canhao> canhoesLado,
                                       DataReconciliation.ReconciliationResult[] resultados) {
        OnReconciliacaoListener l = getListener();
        if (l == null) return;

        com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);

        int nLado = canhoesLado.size();
        if (nLado == 0) return;

        List<float[]> distanciasLado = new ArrayList<>();
        List<DataReconciliation.ReconciliationResult> alvosLado = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (r == null) continue;
            if (geom.determineLado(r.x) == lado) {
                distanciasLado.add(r.distanciasReconciliadas);
                alvosLado.add(r);
            }
        }

        if (distanciasLado.isEmpty()) {
            if (nLado > 1) {
                Canhao maisOcioso = selecionarCanhaoMaisOcioso(canhoesLado, new ArrayList<>());
                if (maisOcioso != null) jogo.removerCanhao(maisOcioso);
            }
            return;
        }

        float[][] distMatrix = distanciasLado.toArray(new float[0][]);
        float energia = jogo.getEnergia(lado);

        double uAtual = DataReconciliation.calcularUtilidade(
                distMatrix, nLado,
                Canhao.getLimiarPenalidade(),
                Canhao.getAlphaPenalidade(), BETA,
                Canhao.getIntervaloDisparoBase());

        Double uMais1 = null;
        if (nLado < MAX_CANHOES_POR_LADO) {
            uMais1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado + 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());
        }
        Double uMenos1 = null;
        if (nLado > 1) {
            uMenos1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado - 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());
        }
        ReconciliationLog.getInstance().logUtilityComparison(
                lado.name(), nLado, uAtual, uMais1, uMenos1, LIMIAR_GANHO, energia);

        if (nLado < MAX_CANHOES_POR_LADO && energia > ENERGIA_SEGURA_MINIMA) {
            if (uMais1 == null) return;

            double ganhoMarginal = uMais1 - uAtual;
            if (ganhoMarginal > LIMIAR_GANHO) {
                float[] pos = calcularCentroidePonderado(lado, canhoesLado, alvosLado);
                ReconciliationLog.getInstance().logAIDecision(
                    "ADICIONAR",
                    "Greedy " + lado.name() + " U(N+1)-U(N)=" + String.format(Locale.US, "%.3f", ganhoMarginal),
                    pos[0], pos[1], uAtual, uMais1);
                if (l != null) l.onSugestaoAdicionarCanhao(lado, pos[0], pos[1]);
                return;
            }
        }

        if (nLado > 1) {
            if (uMenos1 == null) return;

            double perdaMarginal = uAtual - uMenos1;
            boolean energiaCritica = energia < ENERGIA_CRITICA;
            if (perdaMarginal < LIMIAR_GANHO * 0.5 || energiaCritica) {
                Canhao maisOcioso = selecionarCanhaoMaisOcioso(canhoesLado, alvosLado);
                if (maisOcioso != null) {
                    ReconciliationLog.getInstance().logAIDecision(
                            "REMOVER",
                            "Greedy " + lado.name() + " U(N)-U(N-1)=" + String.format(Locale.US, "%.3f", perdaMarginal),
                            maisOcioso.getX(), maisOcioso.getY(), uAtual, uMenos1);
                    jogo.removerCanhao(maisOcioso);
                }
            }
        }
    }

    private float[] calcularCentroidePonderado(Lado lado,
                                               List<Canhao> canhoesLado,
                                               List<DataReconciliation.ReconciliationResult> alvosLado) {
        float cx = 0, cy = 0, totalPeso = 0;
        for (DataReconciliation.ReconciliationResult r : alvosLado) {
            float minDist = Float.MAX_VALUE;
            for (Canhao c : canhoesLado) {
                float d = Alvo.calcularDistancia(c.getX(), c.getY(), r.x, r.y);
                if (d < minDist) minDist = d;
            }
            float peso = 1f / Math.max(minDist, 1f);
            cx += r.x * peso;
            cy += r.y * peso;
            totalPeso += peso;
        }
        if (totalPeso > 0) {
            cx /= totalPeso;
            cy /= totalPeso;
        }
        return clampParaLado(lado, cx, cy);
    }

    private Canhao selecionarCanhaoMaisOcioso(
            List<Canhao> canhoesDoLado,
            List<DataReconciliation.ReconciliationResult> alvosLado) {

        if (canhoesDoLado.isEmpty()) return null;
        if (alvosLado.isEmpty()) return canhoesDoLado.get(canhoesDoLado.size() - 1);

        Canhao maisOcioso = null;
        double menorTaxa = Double.MAX_VALUE;

        for (Canhao c : canhoesDoLado) {
            double taxa = 0;
            for (DataReconciliation.ReconciliationResult r : alvosLado) {
                float dist = Alvo.calcularDistancia(c.getX(), c.getY(), r.x, r.y);
                taxa += Math.exp(-BETA * dist);
            }
            if (taxa < menorTaxa) {
                menorTaxa = taxa;
                maisOcioso = c;
            }
        }
        return maisOcioso;
    }

    private void realocarCanhoes(Lado lado,
                                 List<Canhao> canhoesLado,
                                 DataReconciliation.ReconciliationResult[] resultados) {
        if (canhoesLado.isEmpty()) return;
        OnReconciliacaoListener l = getListener();

        com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);
        List<DataReconciliation.ReconciliationResult> alvosDoLado = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (r == null) continue;
            if (geom.determineLado(r.x) == lado) {
                alvosDoLado.add(r);
            }
        }
        if (alvosDoLado.isEmpty()) return;

        int kTotal = canhoesLado.size();
        @SuppressWarnings("unchecked")
        List<DataReconciliation.ReconciliationResult>[] clusters = new List[kTotal];
        for (int k = 0; k < kTotal; k++) clusters[k] = new ArrayList<>();

        for (DataReconciliation.ReconciliationResult alvo : alvosDoLado) {
            float menorDist = Float.MAX_VALUE;
            int melhorK = 0;
            for (int k = 0; k < kTotal; k++) {
                Canhao c = canhoesLado.get(k);
                float dist = Alvo.calcularDistancia(c.getX(), c.getY(), alvo.x, alvo.y);
                if (dist < menorDist) {
                    menorDist = dist;
                    melhorK = k;
                }
            }
            clusters[melhorK].add(alvo);
        }

        for (int k = 0; k < kTotal; k++) {
            Canhao canhao = canhoesLado.get(k);
            List<DataReconciliation.ReconciliationResult> cluster = clusters[k];
            if (cluster.isEmpty()) continue;

            float sumWX = 0, sumWY = 0, sumW = 0;
            for (DataReconciliation.ReconciliationResult r : cluster) {
                float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(), r.x, r.y);
                float w = 1f / Math.max(dist, 1f);
                sumWX += w * r.x;
                sumWY += w * r.y;
                sumW += w;
            }
            if (sumW <= 0) continue;

            float novoX = sumWX / sumW;
            float novoY = sumWY / sumW;
            float[] clamped = clampParaLado(lado, novoX, novoY);
            float distMov = Alvo.calcularDistancia(canhao.getX(), canhao.getY(), clamped[0], clamped[1]);
            if (distMov > 20) {
                float distanciaMediaAntes = distanciaMediaACluster(canhao.getX(), canhao.getY(), cluster);
                float distanciaMediaDepois = distanciaMediaACluster(clamped[0], clamped[1], cluster);
                float ganhoEsperado = distanciaMediaAntes - distanciaMediaDepois;
                ReconciliationLog.getInstance().logAIDecision(
                        "REALOCAR",
                        "distMediaAntes=" + String.format(Locale.US, "%.2f", distanciaMediaAntes)
                                + " distMediaDepois=" + String.format(Locale.US, "%.2f", distanciaMediaDepois)
                                + " ganho=" + String.format(Locale.US, "%.2f", ganhoEsperado),
                        clamped[0], clamped[1], distanciaMediaAntes, distanciaMediaDepois);
                canhao.moverPara(clamped[0], clamped[1]);
                if (l != null) {
                    l.onRealocarCanhao(canhao, clamped[0], clamped[1]);
                }
            }
        }
    }

    private float distanciaMediaACluster(float x, float y,
                                         List<DataReconciliation.ReconciliationResult> cluster) {
        if (cluster.isEmpty()) return 0;
        float soma = 0;
        for (DataReconciliation.ReconciliationResult r : cluster) {
            soma += Alvo.calcularDistancia(x, y, r.x, r.y);
        }
        return soma / cluster.size();
    }

    private float[] clampParaLado(Lado lado, float x, float y) {
        float metade = larguraTela / 2f;
        float clampedY = Math.max(90, Math.min(y, alturaTela - 90));
        float clampedX;
        if (lado == Lado.ESQUERDO) {
            clampedX = Math.max(30, Math.min(x, metade - 30));
        } else {
            clampedX = Math.max(metade + 30, Math.min(x, larguraTela - 30));
        }
        return new float[]{clampedX, clampedY};
    }

    private void semearCanhoesIniciais(Lado lado) {
        OnReconciliacaoListener l = getListener();
        if (l == null || larguraTela <= 0 || alturaTela <= 0) return;
        float energia = jogo.getEnergia(lado);
        if (energia < 5f) return;

        float x1 = lado == Lado.DIREITO ? larguraTela * 0.75f : larguraTela * 0.25f;
        float y1 = alturaTela * 0.3f;
        l.onSugestaoAdicionarCanhao(lado, x1, y1);

        if (energia > 10f) {
            float x2 = lado == Lado.DIREITO ? larguraTela * 0.65f : larguraTela * 0.35f;
            float y2 = alturaTela * 0.7f;
            l.onSugestaoAdicionarCanhao(lado, x2, y2);
        }
    }

    private void logReconciliationResults(
            DataReconciliation.ReconciliationResult[] resultados,
            float[] canhoesX, float[] canhoesY,
            float[][] mediaD,
            float[] verdadeiroX, float[] verdadeiroY,
            int nCanhoes,
            Lado lado) {

        boolean usouEJML = (nCanhoes >= 4);
        double somaErroAntes = 0, somaErroDepois = 0, somaReducao = 0;
        double somaVarAntes = 0, somaVarDepois = 0;
        
        for (int i = 0; i < resultados.length; i++) {
            DataReconciliation.ReconciliationResult r = resultados[i];
            if (r == null) continue;
            float[] brutas = (i < mediaD.length) ? mediaD[i] : new float[0];

            double[] brutasDouble = floatArrayToDouble(brutas);
            double[] reconDouble = floatArrayToDouble(r.distanciasReconciliadas);

            // Calcular erro RMS para este alvo
            double[] erros = DataReconciliation.calcularErroRMS(brutasDouble, reconDouble);
            somaErroAntes += erros[0];
            somaErroDepois += erros[1];
            somaReducao += erros[2];

            // Calcular Variância para log de auditoria
            double varAntes = DataReconciliation.calcularVariancia(brutasDouble);
            double varDepois = DataReconciliation.calcularVariancia(reconDouble);
            somaVarAntes += varAntes;
            somaVarDepois += varDepois;

            // Registrar para visualização em dashboard/relatório
            ReconciliationVisualizer.registrarPonto(i, brutas, r.distanciasReconciliadas,
                    erros[0], erros[1], erros[2], nCanhoes, lado.name());

            float realX = r.x;
            float realY = r.y;
            if (verdadeiroX != null && verdadeiroY != null
                    && i < verdadeiroX.length && i < verdadeiroY.length) {
                realX = verdadeiroX[i];
                realY = verdadeiroY[i];
            }

            ReconciliationLog.getInstance().logReconciliation(
                    nCanhoes, resultados.length,
                    brutas, r.distanciasReconciliadas,
                    r.x, r.y, realX, realY,
                    canhoesX, canhoesY,
                    r.normA_yHat, usouEJML, lado.name());
        }
        
        // Log estruturado com métrica de erro e variância
        if (resultados.length > 0) {
            double mediaReducao = somaReducao / resultados.length;
            Log.i(TAG, String.format(Locale.US,
                    "RECONCILIACAO_RMS lado=%s, alvos=%d, erroAntes=%.2f, erroDepois=%.2f, reducao=%.1f%% | varAntes=%.2f, varDepois=%.2f",
                    lado.name(), resultados.length, 
                    somaErroAntes / resultados.length,
                    somaErroDepois / resultados.length,
                    mediaReducao,
                    somaVarAntes / resultados.length,
                    somaVarDepois / resultados.length));
            
            // Log estruturado para profiling/plotter
            Log.i("AUTOTARGET_METRICS", String.format(Locale.US,
                    "Lado:%s,Alvos:%d,ErroRMS_Antes:%.2f,ErroRMS_Depois:%.2f,Reducao:%.1f%%,VarAntes:%.2f,VarDepois:%.2f",
                    lado.name(), resultados.length,
                    somaErroAntes / resultados.length,
                    somaErroDepois / resultados.length,
                    mediaReducao,
                    somaVarAntes / resultados.length,
                    somaVarDepois / resultados.length));
        }
    }

    private double[] floatArrayToDouble(float[] arr) {
        if (arr == null) return new double[0];
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }

    public int getReconciliacoesRealizadas() { return reconciliacoesRealizadas; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
    public void setListener(OnReconciliacaoListener listener) {
        this.listener = listener;
    }
    public void setLarguraTela(int largura) { this.larguraTela = largura; }
    public void setAlturaTela(int altura) { this.alturaTela = altura; }
}
