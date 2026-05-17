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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread periódica de reconciliação e otimização tática para ambos os lados.
 */
public class ReconciliacaoThread extends Thread {

    private static final Map<Long, DataReconciliation.ReconciliationResult> posicoesReconciliadas = new ConcurrentHashMap<>();

    public static DataReconciliation.ReconciliationResult getPosicaoReconciliada(long targetId) {
        return posicoesReconciliadas.get(targetId);
    }

    private static final String TAG = "ReconciliacaoThread";
    private static final double BETA = 0.005;
    private static final double LIMIAR_GANHO = 0.01;
    private static final float ENERGIA_SEGURA_MINIMA = 3f;
    private static final float ENERGIA_CRITICA = 5f;
    private static final float RAZAO_PRESSAO_TATICA = 1.0f;
    private static final int MAX_CANHOES_POR_LADO = 10;
    private static final double LIMIAR_SOBREVIDA_SEGUNDOS = 12.0;
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

    public interface OnReconciliacaoListener {
        void onReconciliacaoConcluida(int totalReconciliacoes);
        void onSugestaoAdicionarCanhao(Lado lado, float x, float y);
        void onSugestaoRemoverCanhao(Lado lado, Canhao canhao);
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

    private OnReconciliacaoListener getListener() {
        return listener;
    }

    @Override
    public void run() {
        com.autotarget.util.ThreadAffinityHelper.setAffinityForBackgroundTask(android.os.Process.myTid());
        try {
            Thread.sleep(500);
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
                        executarCiclo();
                        ultimoCicloReconciliacaoMs = agora;
                    }

                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    RMAAnalysis.checkDeadline("T8-Reconciliacao", elapsedMs, INTERVALO_TATICO);
                } catch (InterruptedException e) {
                    ativo = false;
                } catch (Exception e) {
                    Log.e(TAG, "Erro no loop tático", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERRO FATAL ReconciliacaoThread", e);
        }
    }

    private void executarCiclo() {
        executarReconciliacaoPorLado(Lado.ESQUERDO);
        executarReconciliacaoPorLado(Lado.DIREITO);
    }

    private boolean executarReconciliacaoPorLado(Lado lado) {
        if (sensorThread.getHistoricoCount(lado) < SensorThread.getHistoricoMinimoReconciliacao()) {
            return false;
        }

        List<SensorThread.TargetSnapshot> targets = sensorThread.getSnapshotsParaReconciliacao(lado);
        if (targets == null || targets.isEmpty()) return false;

        int reconciliacoesNoCiclo = 0;
        List<DataReconciliation.ReconciliationResult> todosResultados = new ArrayList<>();

        for (SensorThread.TargetSnapshot snap : targets) {
            DataReconciliation.ReconciliationResult[] results = 
                    dataReconciliation.reconciliar(snap.canhoesX, snap.canhoesY, snap.mediaD, snap.varD);
            
            if (results != null && results.length > 0) {
                DataReconciliation.ReconciliationResult r = results[0];
                posicoesReconciliadas.put(snap.id, r);
                todosResultados.add(r);
                reconciliacoesNoCiclo++;
                
                logReconciliationResults(results, snap.canhoesX, snap.canhoesY, snap.mediaD, 
                        new float[]{snap.verdadeiroX}, new float[]{snap.verdadeiroY}, snap.canhoesX.length, lado);
            }
        }

        if (reconciliacoesNoCiclo > 0) {
            reconciliacoesRealizadas += reconciliacoesNoCiclo;
            OnReconciliacaoListener l = getListener();
            if (l != null) l.onReconciliacaoConcluida(reconciliacoesRealizadas);
            
            DataReconciliation.ReconciliationResult[] resultsArr = todosResultados.toArray(new DataReconciliation.ReconciliationResult[0]);
            avaliarCustoBeneficio(lado, coletarCanhoesAtivos(lado), resultsArr);
            realocarCanhoes(lado, coletarCanhoesAtivos(lado), resultsArr);
        }
        sensorThread.limparHistoricoInativo(lado, lado == Lado.ESQUERDO ? jogo.getAlvosEsquerdo() : jogo.getAlvosDireito());
        return reconciliacoesNoCiclo > 0;
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
                ReconciliationLog.getInstance().logAIDecision("ADICIONAR", "Pressão tática " + lado.name(), novoX, novoY, 0, 0);
                l.onSugestaoAdicionarCanhao(lado, novoX, novoY);
            }
        }
    }

    private List<Canhao> coletarCanhoesAtivos(Lado lado) {
        List<Canhao> canhoes = new ArrayList<>();
        synchronized(jogo.getCanhoesLock()) {
            java.util.List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            for (Canhao c : origem) if (c != null && c.isAtivo()) canhoes.add(c);
        }
        return canhoes;
    }

    private float calcularPosicaoEstrategica(Lado lado, int index) {
        float[] cols = lado == Lado.DIREITO ? new float[]{0.6f, 0.85f, 0.72f, 0.95f} : new float[]{0.05f, 0.4f, 0.28f, 0.15f};
        return larguraTela * cols[Math.abs(index) % cols.length];
    }

    private float calcularAlturaEstrategica(int index) {
        float[] heights = {0.25f, 0.5f, 0.75f, 0.15f, 0.85f, 0.4f, 0.6f, 0.35f};
        return Math.max(90, Math.min(alturaTela * heights[Math.abs(index) % heights.length], alturaTela - 90));
    }

    private void avaliarCustoBeneficio(Lado lado, List<Canhao> canhoes, DataReconciliation.ReconciliationResult[] resultados) {
        if (canhoes == null || canhoes.isEmpty() || resultados == null || resultados.length == 0) {
            return;
        }

        float energiaAtual = jogo.getEnergia(lado);
        int nCanhoes = canhoes.size();
        float[][] distancias = montarMatrizDistancias(canhoes, resultados);
        if (distancias.length == 0 || distancias[0].length == 0) {
            return;
        }

        double uAtual = DataReconciliation.calcularUtilidade(
                distancias, nCanhoes,
                Canhao.getLimiarPenalidade(), Canhao.getAlphaPenalidade(), BETA,
                Canhao.getIntervaloDisparoBase());

        Double uMais1 = null;
        Double uMenos1 = null;

        if (nCanhoes < MAX_CANHOES_POR_LADO && energiaAtual > ENERGIA_SEGURA_MINIMA) {
            float[] posicaoHipotetica = estimarPosicaoAdicao(resultados, lado);
            float[][] distanciasMais1 = adicionarCandidato(distancias, resultados, posicaoHipotetica[0], posicaoHipotetica[1]);
            uMais1 = DataReconciliation.calcularUtilidade(
                    distanciasMais1, nCanhoes + 1,
                    Canhao.getLimiarPenalidade(), Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            double sobrevidaSegundos = energiaAtual / Math.max(1.0, (nCanhoes + 1) * 1.0);
            if ((uMais1 - uAtual) > LIMIAR_GANHO && sobrevidaSegundos >= LIMIAR_SOBREVIDA_SEGUNDOS) {
                OnReconciliacaoListener l = getListener();
                if (l != null) {
                    ReconciliationLog.getInstance().logAIDecision(
                            "ADICIONAR",
                            String.format(Locale.US, "DeltaU=%.4f sobrevida=%.1fs", (uMais1 - uAtual), sobrevidaSegundos),
                            posicaoHipotetica[0], posicaoHipotetica[1], uAtual, uMais1);
                    l.onSugestaoAdicionarCanhao(lado, posicaoHipotetica[0], posicaoHipotetica[1]);
                }
            }
        }

        if (nCanhoes > 1) {
            int indiceRemocao = selecionarCanhaoMenorContribuicao(distancias);
            if (indiceRemocao >= 0) {
                float[][] distanciasMenos1 = removerCanhaoDaMatriz(distancias, indiceRemocao);
                uMenos1 = DataReconciliation.calcularUtilidade(
                        distanciasMenos1, nCanhoes - 1,
                        Canhao.getLimiarPenalidade(), Canhao.getAlphaPenalidade(), BETA,
                        Canhao.getIntervaloDisparoBase());

                if ((uMenos1 - uAtual) > LIMIAR_GANHO || energiaAtual <= ENERGIA_CRITICA) {
                    Canhao canhaoRemover = canhoes.get(indiceRemocao);
                    OnReconciliacaoListener l = getListener();
                    if (l != null) {
                        ReconciliationLog.getInstance().logAIDecision(
                                "REMOVER",
                                String.format(Locale.US, "DeltaU=%.4f energia=%.1f", (uMenos1 - uAtual), energiaAtual),
                                canhaoRemover.getX(), canhaoRemover.getY(), uAtual, uMenos1);
                        l.onSugestaoRemoverCanhao(lado, canhaoRemover);
                    }
                }
            }
        }

        ReconciliationLog.getInstance().logUtilityComparison(
                lado.name(), nCanhoes, uAtual, uMais1, uMenos1, LIMIAR_GANHO, energiaAtual);
    }

    private float[][] montarMatrizDistancias(List<Canhao> canhoes, DataReconciliation.ReconciliationResult[] resultados) {
        float[][] distancias = new float[resultados.length][canhoes.size()];
        for (int i = 0; i < resultados.length; i++) {
            DataReconciliation.ReconciliationResult r = resultados[i];
            if (r == null || r.distanciasReconciliadas == null) {
                continue;
            }
            int limite = Math.min(canhoes.size(), r.distanciasReconciliadas.length);
            for (int j = 0; j < limite; j++) {
                distancias[i][j] = r.distanciasReconciliadas[j];
            }
        }
        return distancias;
    }

    private float[] estimarPosicaoAdicao(DataReconciliation.ReconciliationResult[] resultados, Lado lado) {
        double somaPesos = 0.0;
        double somaX = 0.0;
        double somaY = 0.0;

        for (DataReconciliation.ReconciliationResult resultado : resultados) {
            if (resultado == null || resultado.distanciasReconciliadas == null || resultado.distanciasReconciliadas.length == 0) {
                continue;
            }
            double mediaDist = 0.0;
            for (float dist : resultado.distanciasReconciliadas) {
                mediaDist += dist;
            }
            mediaDist /= resultado.distanciasReconciliadas.length;
            double peso = Math.max(1.0, mediaDist);
            somaPesos += peso;
            somaX += resultado.x * peso;
            somaY += resultado.y * peso;
        }

        if (somaPesos <= 0.0) {
            return clampParaLado(lado, larguraTela * 0.5f, alturaTela * 0.5f);
        }

        return clampParaLado(lado, (float) (somaX / somaPesos), (float) (somaY / somaPesos));
    }

    private int selecionarCanhaoMenorContribuicao(float[][] distancias) {
        if (distancias.length == 0 || distancias[0].length == 0) {
            return -1;
        }

        int indiceMenor = -1;
        double menorContribuicao = Double.POSITIVE_INFINITY;

        for (int j = 0; j < distancias[0].length; j++) {
            double contribuicao = 0.0;
            for (float[] distancia : distancias) {
                if (j < distancia.length) {
                    contribuicao += Math.exp(-BETA * distancia[j]);
                }
            }
            if (contribuicao < menorContribuicao) {
                menorContribuicao = contribuicao;
                indiceMenor = j;
            }
        }

        return indiceMenor;
    }

    private float[][] adicionarCandidato(float[][] distancias, DataReconciliation.ReconciliationResult[] resultados,
                                         float candidatoX, float candidatoY) {
        float[][] expandida = new float[resultados.length][distancias[0].length + 1];
        for (int i = 0; i < resultados.length; i++) {
            for (int j = 0; j < distancias[i].length; j++) {
                expandida[i][j] = distancias[i][j];
            }
            DataReconciliation.ReconciliationResult resultado = resultados[i];
            if (resultado != null) {
                expandida[i][distancias[0].length] = Alvo.calcularDistancia(candidatoX, candidatoY, resultado.x, resultado.y);
            }
        }
        return expandida;
    }

    private float[][] removerCanhaoDaMatriz(float[][] distancias, int indiceRemocao) {
        if (indiceRemocao < 0 || distancias.length == 0 || indiceRemocao >= distancias[0].length) {
            return distancias;
        }

        float[][] reduzida = new float[distancias.length][distancias[0].length - 1];
        for (int i = 0; i < distancias.length; i++) {
            int destino = 0;
            for (int j = 0; j < distancias[i].length; j++) {
                if (j == indiceRemocao) {
                    continue;
                }
                reduzida[i][destino++] = distancias[i][j];
            }
        }
        return reduzida;
    }

    private void realocarCanhoes(Lado lado, List<Canhao> canhoes, DataReconciliation.ReconciliationResult[] resultados) {
        if (canhoes.isEmpty() || resultados.length == 0) return;

        // 1. Clusterização: Atribuir cada alvo ao canhão mais próximo
        Map<Canhao, List<DataReconciliation.ReconciliationResult>> clusters = new java.util.HashMap<>();
        for (Canhao c : canhoes) clusters.put(c, new ArrayList<>());

        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (r == null) continue;
            Canhao melhor = null;
            float minDist = Float.MAX_VALUE;
            for (Canhao c : canhoes) {
                float d = Alvo.calcularDistancia(c.getX(), c.getY(), r.x, r.y);
                if (d < minDist) {
                    minDist = d;
                    melhor = c;
                }
            }
            if (melhor != null) {
                List<DataReconciliation.ReconciliationResult> list = clusters.get(melhor);
                if (list != null) list.add(r);
            }
        }

        // 2. Mover cada canhão para o centroide do seu cluster
        for (Canhao c : canhoes) {
            List<DataReconciliation.ReconciliationResult> alvosCluster = clusters.get(c);
            if (alvosCluster == null) continue;
            
            float targetX, targetY;
            if (!alvosCluster.isEmpty()) {
                float sumX = 0, sumY = 0;
                for (DataReconciliation.ReconciliationResult r : alvosCluster) {
                    sumX += r.x; sumY += r.y;
                }
                targetX = sumX / alvosCluster.size();
                targetY = sumY / alvosCluster.size();
            } else {
                continue;
            }

            float[] clamped = clampParaLado(lado, targetX, targetY);

            // FIX: Manter distância mínima entre canhões para evitar singularidade matricial
            for (Canhao outro : canhoes) {
                if (outro == c) continue;
                float d = Alvo.calcularDistancia(clamped[0], clamped[1], outro.getX(), outro.getY());
                if (d < 40) {
                    clamped[0] += (c.getX() < outro.getX() ? -60 : 60); // Empurra para o lado
                }
            }
            
            float[] finalClamped = clampParaLado(lado, clamped[0], clamped[1]);

            if (Alvo.calcularDistancia(c.getX(), c.getY(), finalClamped[0], finalClamped[1]) > 10) {
                c.moverPara(finalClamped[0], finalClamped[1]);
                if (listener != null) listener.onRealocarCanhao(c, finalClamped[0], finalClamped[1]);
            }
        }
    }

    private float[] clampParaLado(Lado lado, float x, float y) {
        float metade = larguraTela / 2f;
        float cy = Math.max(90, Math.min(y, alturaTela - 90));
        float cx = (lado == Lado.ESQUERDO) ? Math.max(30, Math.min(x, metade - 30)) : Math.max(metade + 30, Math.min(x, larguraTela - 30));
        return new float[]{cx, cy};
    }

    private void semearCanhoesIniciais(Lado lado) {
        float x = lado == Lado.DIREITO ? larguraTela * 0.75f : larguraTela * 0.25f;
        if (listener != null) listener.onSugestaoAdicionarCanhao(lado, x, alturaTela * 0.5f);
    }

    private void logReconciliationResults(DataReconciliation.ReconciliationResult[] results, float[] cX, float[] cY, float[][] mediaD, float[] vX, float[] vY, int nC, Lado lado) {
        for (int i = 0; i < results.length; i++) {
            DataReconciliation.ReconciliationResult r = results[i];
            double[] brutas = new double[mediaD[i].length];
            for (int j=0; j<brutas.length; j++) brutas[j] = mediaD[i][j];
            double[] recon = new double[r.distanciasReconciliadas.length];
            for (int j=0; j<recon.length; j++) recon[j] = r.distanciasReconciliadas[j];
            
            double[] erros = DataReconciliation.calcularErroRMS(brutas, recon);
            ReconciliationVisualizer.registrarPonto(i, mediaD[i], r.distanciasReconciliadas, erros[0], erros[1], erros[2], nC, lado.name());
            
            ReconciliationLog.getInstance().logReconciliation(nC, results.length, mediaD[i], r.distanciasReconciliadas, r.x, r.y, vX[0], vY[0], cX, cY, r.normA_yHat, nC>=4, lado.name());
        }
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setListener(OnReconciliacaoListener listener) { this.listener = listener; }
    public void setLarguraTela(int largura) { this.larguraTela = largura; }
    public void setAlturaTela(int altura) { this.alturaTela = altura; }
}
