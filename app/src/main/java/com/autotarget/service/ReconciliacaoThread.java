package com.autotarget.service;

import android.util.Log;

import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.util.DataReconciliation;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ReconciliationLog;

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
    private static final float RAZAO_PRESSAO_TATICA = 1.5f;
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
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        synchronized (sensorLock) {
            avaliarPressaoTatica(Lado.ESQUERDO);
            avaliarPressaoTatica(Lado.DIREITO);
        }

        while (ativo) {
            long startNs = System.nanoTime();
            try {
                Thread.sleep(INTERVALO_TATICO);
                if (!ativo) break;

                synchronized (sensorLock) {
                    avaliarPressaoTatica(Lado.ESQUERDO);
                    avaliarPressaoTatica(Lado.DIREITO);
                    ciclosTaticos++;
                    if (ciclosTaticos % (INTERVALO_RECONCILIACAO / INTERVALO_TATICO) == 0) {
                        executarReconciliacaoCompleta();
                    }
                }

                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                RMAAnalysis.checkDeadline("T8-Reconciliacao", elapsedMs, INTERVALO_TATICO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
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
                if (listener != null) {
                    listener.onReconciliacaoConcluida(reconciliacoesRealizadas);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação", e);
        }
    }

    private boolean executarReconciliacaoPorLado(Lado lado) {
        if (sensorThread.getHistoricoCount(lado) < 3) return false;

        List<Canhao> canhoesLado = new ArrayList<>();
        synchronized (collisionLock) {
            List<Canhao> origem = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
            for (Canhao c : origem) {
                if (c.isAtivo()) canhoesLado.add(c);
            }
        }
        if (canhoesLado.isEmpty()) return false;

        float[][] mediaD = sensorThread.getMediaDistancias(lado);
        float[][] varD = sensorThread.getVarianciaDistancias(lado);
        if (mediaD == null || varD == null || mediaD.length == 0 || mediaD[0].length == 0) return false;
        if (mediaD[0].length != canhoesLado.size()) {
            Log.d(TAG, "Pulando reconciliação: tamanho de canhões mudou durante janela");
            return false;
        }

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
        if (listener == null || larguraTela <= 0 || alturaTela <= 0) return;

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
                listener.onSugestaoAdicionarCanhao(lado, novoX, novoY);
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
        List<Canhao> lista = lado == Lado.ESQUERDO ? jogo.getCanhoesEsquerdo() : jogo.getCanhoesDireito();
        for (int i = lista.size() - 1; i >= 0; i--) {
            Canhao c = lista.get(i);
            if (c.isAtivo()) return c;
        }
        return null;
    }

    private float calcularPosicaoEstrategica(Lado lado, int canhoesExistentes) {
        float[] colunas = lado == Lado.DIREITO
                ? new float[]{0.6f, 0.75f, 0.9f}
                : new float[]{0.1f, 0.25f, 0.4f};
        int idx = canhoesExistentes % colunas.length;
        return larguraTela * colunas[idx];
    }

    private float calcularAlturaEstrategica(int canhoesExistentes) {
        float[] alturas = {0.25f, 0.5f, 0.75f, 0.15f, 0.85f, 0.4f, 0.6f, 0.35f};
        int idx = canhoesExistentes % alturas.length;
        float y = alturaTela * alturas[idx];
        return Math.max(90, Math.min(y, alturaTela - 90));
    }

    private void avaliarCustoBeneficio(Lado lado,
                                       List<Canhao> canhoesLado,
                                       DataReconciliation.ReconciliationResult[] resultados) {
        if (listener == null) return;

        int nLado = canhoesLado.size();
        if (nLado == 0) return;

        List<float[]> distanciasLado = new ArrayList<>();
        List<DataReconciliation.ReconciliationResult> alvosLado = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (Lado.determinar(r.x, larguraTela) == lado) {
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

        if (nLado < MAX_CANHOES_POR_LADO && energia > ENERGIA_SEGURA_MINIMA) {
            double uMais1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado + 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            double ganhoMarginal = uMais1 - uAtual;
            if (ganhoMarginal > LIMIAR_GANHO) {
                float[] pos = calcularCentroidePonderado(lado, canhoesLado, alvosLado);
                ReconciliationLog.getInstance().logAIDecision(
                        "ADICIONAR",
                        "Greedy " + lado.name() + " U(N+1)-U(N)=" + String.format(Locale.US, "%.3f", ganhoMarginal),
                        pos[0], pos[1], uAtual, uMais1);
                listener.onSugestaoAdicionarCanhao(lado, pos[0], pos[1]);
                return;
            }
        }

        if (nLado > 1) {
            double uMenos1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado - 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

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
            float peso = minDist;
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

        List<DataReconciliation.ReconciliationResult> alvosDoLado = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (Lado.determinar(r.x, larguraTela) == lado) {
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
                float w = (float) Math.exp(-BETA * Math.max(dist, 1f));
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
                canhao.moverPara(clamped[0], clamped[1]);
                if (listener != null) {
                    listener.onRealocarCanhao(canhao, clamped[0], clamped[1]);
                }
            }
        }
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
        if (listener == null || larguraTela <= 0 || alturaTela <= 0) return;
        float energia = jogo.getEnergia(lado);
        if (energia < 5f) return;

        float x1 = lado == Lado.DIREITO ? larguraTela * 0.75f : larguraTela * 0.25f;
        float y1 = alturaTela * 0.3f;
        listener.onSugestaoAdicionarCanhao(lado, x1, y1);

        if (energia > 10f) {
            float x2 = lado == Lado.DIREITO ? larguraTela * 0.65f : larguraTela * 0.35f;
            float y2 = alturaTela * 0.7f;
            listener.onSugestaoAdicionarCanhao(lado, x2, y2);
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
        for (int i = 0; i < resultados.length; i++) {
            DataReconciliation.ReconciliationResult r = resultados[i];
            float[] brutas = (i < mediaD.length) ? mediaD[i] : new float[0];

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
    }

    public int getReconciliacoesRealizadas() { return reconciliacoesRealizadas; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isAtivo() { return ativo; }
    public void setListener(OnReconciliacaoListener listener) { this.listener = listener; }
    public void setLarguraTela(int largura) { this.larguraTela = largura; }
    public void setAlturaTela(int altura) { this.alturaTela = altura; }
}
