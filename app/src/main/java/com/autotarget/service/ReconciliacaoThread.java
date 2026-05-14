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
 *   EVOLUÇÃO (implementação atual):
 *   - Fase 2: Decisão de Instanciar/Remover via U(N), U(N+1), U(N-1)
 *     com verificação de energia segura e seleção do canhão mais ocioso.
 *   - Fase 3: Posicionamento via K-Means Clustering — cada canhão calcula
 *     centroide ponderado APENAS dos alvos do seu cluster (Voronoi partition),
 *     forçando espalhamento inteligente pela tela.
 *
 * RECONCILIAÇÃO (AV2 §6.2.2-d):
 *   A cada 10s: ler buffer → reconciliar → estimar posições → otimizar
 *
 * OTIMIZAÇÃO (AV2 §6.2.2-e):
 *   Função de utilidade: U(N) = Σ r(N) · Σ exp(-β·d̂_ij)
 *   Decisão: se ΔU > custo → adicionar; se utilidade marginal < 0 → remover
 *   Realocação: centroide ponderado p_j = Σ w_ij·(X̂,Ŷ) / Σ w_ij
 *     onde w_ij ≠ 0 somente para alvos no cluster j (K-Means assignment)
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
import com.autotarget.util.ReconciliationLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread dedicada para reconciliação de dados e otimização via EJML.
 * Implementa K-Means clustering para posicionamento espacial e
 * Greedy Search com função de utilidade U(N) para gerência de frota.
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

    /** Constante de dissipação para função de utilidade. */
    private static final double BETA = 0.005;

    /** Limiar para decisão de adicionar canhão (ganho marginal mínimo). */
    private static final double LIMIAR_GANHO = 0.01;

    /** Energia mínima segura para a IA considerar adicionar um canhão. */
    private static final float ENERGIA_SEGURA_MINIMA = 3f;

    /** Energia crítica — abaixo disso a IA prioriza remoção. */
    private static final float ENERGIA_CRITICA = 5f;

    /**
     * Razão mínima de alvos por canhão. Se alvos/canhões > esse valor,
     * a IA é FORÇADA a adicionar mais canhões (Pressão Tática).
     * 1.5 = se há 3 alvos e 2 canhões (ratio=1.5), já adiciona.
     */
    private static final float RAZAO_PRESSAO_TATICA = 1.5f;

    /** Número máximo de canhões da IA. */
    private static final int MAX_CANHOES_IA = 8;

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

    /** Intervalo entre ciclos táticos da IA (ms) — rápido para reagir. */
    private static final int INTERVALO_TATICO = 3000; // 3s

    /** Intervalo entre reconciliações EJML completas (ms). */
    private static final int INTERVALO_RECONCILIACAO = 10000; // 10s

    /** Contador de ciclos táticos para decidir quando rodar EJML. */
    private volatile int ciclosTaticos = 0;

    @Override
    public void run() {
        // ── SEMEADURA IMEDIATA: não esperar 10s para criar canhões ──
        try { Thread.sleep(500); } catch (InterruptedException e) { return; }
        synchronized (sensorLock) {
            avaliarPressaoTatica();
        }

        while (ativo) {
            long startNs = System.nanoTime();
            try {
                Thread.sleep(INTERVALO_TATICO);
                if (!ativo) break;

                synchronized (sensorLock) {
                    // Pressão tática roda SEMPRE (a cada 3s)
                    avaliarPressaoTatica();

                    // Reconciliação EJML completa roda a cada ~10s
                    ciclosTaticos++;
                    if (ciclosTaticos % (INTERVALO_RECONCILIACAO / INTERVALO_TATICO) == 0) {
                        executarReconciliacao();
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

    /**
     * Executa reconciliação completa:
     * 1. Pressão Tática: avaliar proporção alvos/canhões (SEMPRE roda)
     * 2. Ler buffer histórico → calcular média/variância
     * 3. Reconciliar via EJML
     * 4. Avaliar custo-benefício U(N) (Greedy Search)
     */
    private void executarReconciliacao() {
        try {
            // ══════════════════════════════════════════════════════
            //  PRESSÃO TÁTICA — Roda SEMPRE, mesmo sem dados EJML.
            //  Se há mais alvos que canhões, a IA é forçada a agir.
            // ══════════════════════════════════════════════════════
            avaliarPressaoTatica();

            // Verificar dados suficientes para reconciliação EJML
            if (sensorThread.getHistoricoCount() < 3) {
                Log.d(TAG, "Buffer insuficiente para reconciliação EJML");
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

            // ── LOG DE AUDITORIA ────────────────────────────────
            float[] verdadeiroX = sensorThread.getVerdadeiroPosX();
            float[] verdadeiroY = sensorThread.getVerdadeiroPosY();
            logReconciliationResults(resultados, canhoesX, canhoesY, mediaD, verdadeiroX, verdadeiroY, N);

            // ── OTIMIZAÇÃO GREEDY (refino fino via U(N)) ────────
            avaliarCustoBeneficio(canhoesSnapshot, resultados);

            // ── REALOCAÇÃO K-MEANS (centroide por cluster) ──────
            // A IA reposiciona canhões com base nos dados RECONCILIADOS.
            // Sem a reconciliação, as posições seriam ruidosas e os
            // canhões se moveriam erraticamente. Com EJML, o movimento
            // é preciso → abates mais rápidos → mais pontos (bônus temporal).
            realocarCanhoes(canhoesSnapshot, resultados);

            // Notificar Jogo
            if (listener != null) {
                listener.onReconciliacaoConcluida(reconciliacoesRealizadas);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro na reconciliação", e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PRESSÃO TÁTICA — Sistema de penalidade que FORÇA a IA a
    //  criar/remover canhões baseado na situação real do campo.
    // ══════════════════════════════════════════════════════════════

    /**
     * Avalia a pressão do campo e força a IA a reagir:
     *
     * 1. Se não tem canhões → semear 2 canhões iniciais
     * 2. Se ratio alvos/canhões > RAZAO_PRESSAO → adicionar canhão
     * 3. Se tem canhões mas 0 alvos e muitos canhões → remover excesso
     *
     * Isso garante que a IA escala sua frota proporcionalmente
     * à ameaça, independente da reconciliação EJML.
     */
    private void avaliarPressaoTatica() {
        if (listener == null || larguraTela <= 0 || alturaTela <= 0) return;

        int nCanhoesDir = jogo.contarCanhoesAtivos(Lado.DIREITO);
        int nAlvosDir = jogo.getAlvosDireito().size();
        float energiaDir = jogo.getEnergia(Lado.DIREITO);

        // ── Sem canhões → semeadura de emergência ──
        if (nCanhoesDir == 0) {
            semearCanhoesIniciais();
            return;
        }

        // ── Pressão: muitos alvos, poucos canhões → adicionar ──
        if (nAlvosDir > 0 && nCanhoesDir < MAX_CANHOES_IA && energiaDir > ENERGIA_SEGURA_MINIMA) {
            float ratio = (float) nAlvosDir / nCanhoesDir;

            if (ratio > RAZAO_PRESSAO_TATICA) {
                // Calcular posição: região do lado direito com menos cobertura
                float novoX = calcularPosicaoEstrategica(nCanhoesDir);
                float novoY = calcularAlturaEstrategica(nCanhoesDir);

                Log.i(TAG, String.format(
                        "PRESSÃO TÁTICA: %d alvos / %d canhões (ratio=%.1f) → ADICIONANDO em (%.0f,%.0f)",
                        nAlvosDir, nCanhoesDir, ratio, novoX, novoY));
                ReconciliationLog.getInstance().logAIDecision(
                        "ADICIONAR", "Pressão tática (ratio=" + String.format("%.1f", ratio) + ")",
                        novoX, novoY, 0, 0);
                listener.onSugestaoAdicionarCanhao(Lado.DIREITO, novoX, novoY);
            }
        }

        // ── Excesso: 0 alvos e muitos canhões → remover para economizar ──
        if (nAlvosDir == 0 && nCanhoesDir > 2) {
            // Manter pelo menos 2 canhões de sentinela
            List<Canhao> canhoesDoLado = new ArrayList<>();
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesDoLado.add(c);
            }
            if (canhoesDoLado.size() > 2) {
                Canhao ultimo = canhoesDoLado.get(canhoesDoLado.size() - 1);
                Log.i(TAG, "EXCESSO: 0 alvos, removendo canhão excedente");
                ReconciliationLog.getInstance().logAIDecision(
                        "REMOVER", "Excesso: 0 alvos ativos",
                        ultimo.getX(), ultimo.getY(), 0, 0);
                jogo.removerCanhao(ultimo);
            }
        }

        // ── Energia crítica → remover para sobreviver ──
        if (energiaDir < ENERGIA_CRITICA && nCanhoesDir > 1) {
            List<Canhao> canhoesDoLado = new ArrayList<>();
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesDoLado.add(c);
            }
            if (!canhoesDoLado.isEmpty()) {
                Canhao ultimo = canhoesDoLado.get(canhoesDoLado.size() - 1);
                Log.i(TAG, String.format("ENERGIA CRÍTICA (%.0f) → removendo canhão", energiaDir));
                ReconciliationLog.getInstance().logAIDecision(
                        "REMOVER", "Energia crítica (" + String.format("%.0f", energiaDir) + ")",
                        ultimo.getX(), ultimo.getY(), 0, 0);
                jogo.removerCanhao(ultimo);
            }
        }
    }

    /**
     * Calcula posição X estratégica para novo canhão da IA.
     * Distribui canhões uniformemente pela metade direita.
     */
    private float calcularPosicaoEstrategica(int canhoesExistentes) {
        float metade = larguraTela / 2f;
        float larguraDir = larguraTela - metade;
        // Distribuir em colunas: 60%, 75%, 90% da largura total
        float[] colunas = {0.6f, 0.75f, 0.9f};
        int col = canhoesExistentes % colunas.length;
        return larguraTela * colunas[col];
    }

    /**
     * Calcula posição Y estratégica.
     * Alterna entre topo, meio e base para espalhamento vertical.
     */
    private float calcularAlturaEstrategica(int canhoesExistentes) {
        float[] alturas = {0.25f, 0.5f, 0.75f, 0.15f, 0.85f, 0.4f, 0.6f, 0.35f};
        int idx = canhoesExistentes % alturas.length;
        float y = alturaTela * alturas[idx];
        return Math.max(90, Math.min(y, alturaTela - 90));
    }

    // ══════════════════════════════════════════════════════════════
    //  FASE 2: DECISÃO DE INSTANCIAR/REMOVER CANHÕES
    //  Função de Utilidade U(N) com verificação de energia segura
    // ══════════════════════════════════════════════════════════════

    /**
     * Avalia custo-benefício para adicionar/remover canhões do Lado.DIREITO.
     * 
     * Lógica:
     * - Calcula U(N), U(N+1) e U(N-1) usando distâncias reconciliadas
     * - Adição: ganho marginal > LIMIAR_GANHO E energia > ENERGIA_SEGURA_MINIMA
     * - Remoção: perda marginal < limiar OU energia < ENERGIA_CRITICA
     * - Remoção inteligente: seleciona o canhão com menor taxa de abate
     *   (o mais longe de qualquer alvo → menor Σ p_ij)
     */
    private void avaliarCustoBeneficio(List<Canhao> canhoesSnap,
                                        DataReconciliation.ReconciliationResult[] resultados) {
        if (listener == null) return;

        // ── Processar apenas o Lado.DIREITO (IA) ──
        Lado lado = Lado.DIREITO;

        // Contar canhões ativos deste lado
        int nLado = 0;
        List<Canhao> canhoesDoLado = new ArrayList<>();
        for (Canhao c : canhoesSnap) {
            if (c.getLado() == lado && c.isAtivo()) {
                nLado++;
                canhoesDoLado.add(c);
            }
        }

        // Filtrar alvos reconciliados que estão no lado direito
        List<float[]> distanciasLado = new ArrayList<>();
        List<DataReconciliation.ReconciliationResult> alvosLado = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            Lado ladoAlvo = Lado.determinar(r.x, larguraTela);
            if (ladoAlvo == lado) {
                distanciasLado.add(r.distanciasReconciliadas);
                alvosLado.add(r);
            }
        }

        if (distanciasLado.isEmpty()) {
            // Sem alvos no lado direito — considerar remoção se houver muitos canhões
            if (nLado > 1) {
                Canhao maisOcioso = selecionarCanhaoMaisOcioso(canhoesDoLado, new ArrayList<>());
                if (maisOcioso != null) {
                    Log.i(TAG, "Sem alvos no DIR — removendo canhão ocioso");
                    jogo.removerCanhao(maisOcioso);
                }
            }
            return;
        }

        float[][] distMatrix = distanciasLado.toArray(new float[0][]);
        float energiaDir = jogo.getEnergia(Lado.DIREITO);

        // ── U(N) atual ──
        double uAtual = DataReconciliation.calcularUtilidade(
                distMatrix, nLado,
                Canhao.getLimiarPenalidade(),
                Canhao.getAlphaPenalidade(), BETA,
                Canhao.getIntervaloDisparoBase());

        // ── DECISÃO DE ADIÇÃO ──
        if (nLado < 10 && energiaDir > ENERGIA_SEGURA_MINIMA) {
            double uMais1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado + 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            double ganhoMarginal = uMais1 - uAtual;

            if (ganhoMarginal > LIMIAR_GANHO) {
                // Calcular a melhor posição: centroide dos alvos menos cobertos
                float cx = 0, cy = 0;
                float totalPeso = 0;

                for (int i = 0; i < alvosLado.size(); i++) {
                    DataReconciliation.ReconciliationResult r = alvosLado.get(i);
                    // Peso inversamente proporcional à cobertura atual
                    float minDist = Float.MAX_VALUE;
                    for (Canhao c : canhoesDoLado) {
                        float d = Alvo.calcularDistancia(c.getX(), c.getY(), r.x, r.y);
                        if (d < minDist) minDist = d;
                    }
                    float peso = minDist; // Alvos longe dos canhões existentes pesam mais
                    cx += r.x * peso;
                    cy += r.y * peso;
                    totalPeso += peso;
                }

                if (totalPeso > 0) {
                    cx /= totalPeso;
                    cy /= totalPeso;
                }

                // Garantir que fica no lado direito
                float metade = larguraTela / 2f;
                cx = Math.max(cx, metade + 30);
                cy = Math.max(90, Math.min(cy, alturaTela - 90));

                Log.i(TAG, String.format(
                        "IA ADICIONAR canhão DIR em (%.0f, %.0f) ΔU=%.3f energia=%.0f",
                        cx, cy, ganhoMarginal, energiaDir));
                ReconciliationLog.getInstance().logAIDecision(
                        "ADICIONAR", "Greedy U(N+1)-U(N)=" + String.format("%.3f", ganhoMarginal),
                        cx, cy, uAtual, uMais1);
                listener.onSugestaoAdicionarCanhao(lado, cx, cy);
                return; // Não remover no mesmo ciclo
            }
        }

        // ── DECISÃO DE REMOÇÃO ──
        if (nLado > 1) {
            double uMenos1 = DataReconciliation.calcularUtilidade(
                    distMatrix, nLado - 1,
                    Canhao.getLimiarPenalidade(),
                    Canhao.getAlphaPenalidade(), BETA,
                    Canhao.getIntervaloDisparoBase());

            double perdaMarginal = uAtual - uMenos1;
            boolean energiaCritica = energiaDir < ENERGIA_CRITICA;

            if (perdaMarginal < LIMIAR_GANHO * 0.5 || energiaCritica) {
                // Selecionar o canhão mais ocioso (menor contribuição)
                Canhao maisOcioso = selecionarCanhaoMaisOcioso(canhoesDoLado, alvosLado);
                if (maisOcioso != null) {
                    Log.i(TAG, String.format(
                            "IA REMOVER canhão DIR (perda=%.3f, energia=%.0f, critica=%s)",
                            perdaMarginal, energiaDir, energiaCritica));
                    ReconciliationLog.getInstance().logAIDecision(
                            "REMOVER", "Greedy U(N)-U(N-1)=" + String.format("%.3f", perdaMarginal),
                            maisOcioso.getX(), maisOcioso.getY(), uAtual, uMenos1);
                    jogo.removerCanhao(maisOcioso);
                }
            }
        }
    }

    /**
     * Seleciona o canhão com menor taxa de abate esperada.
     * Taxa = Σ_i exp(-β · dist(canhão_j, alvo_i))
     * O canhão com menor soma está mais longe de todos os alvos (mais ocioso).
     */
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

    // ══════════════════════════════════════════════════════════════
    //  FASE 3: POSICIONAMENTO ESPACIAL VIA K-MEANS CLUSTERING
    //  Cada canhão é o "centroide" do seu setor de Voronoi.
    // ══════════════════════════════════════════════════════════════

    /**
     * Realoca canhões da IA usando K-Means Assignment:
     *
     * 1. Para cada alvo reconciliado do Lado.DIREITO, encontra o canhão
     *    mais próximo → esse alvo é "atribuído" ao cluster desse canhão.
     * 2. Para cada canhão, calcula o centroide ponderado dos alvos do
     *    seu cluster: p_j = Σ w_ij·(X̂,Ŷ) / Σ w_ij onde w_ij = exp(-β·d̂_ij)
     * 3. Define moverPara(centroide) para glide gradual a 60Hz.
     *
     * Isso força a IA a ESPALHAR os canhões pelo campo em vez de
     * aglomerá-los todos no centro (o problema clássico do centroide global).
     */
    private void realocarCanhoes(List<Canhao> canhoesSnap,
                                  DataReconciliation.ReconciliationResult[] resultados) {

        // Filtrar apenas canhões da IA (Lado.DIREITO) ativos
        List<Canhao> canhoesIA = new ArrayList<>();
        for (Canhao c : canhoesSnap) {
            if (c.getLado() == Lado.DIREITO && c.isAtivo()) {
                canhoesIA.add(c);
            }
        }
        if (canhoesIA.isEmpty()) return;

        // Filtrar alvos reconciliados do lado direito
        List<DataReconciliation.ReconciliationResult> alvosDir = new ArrayList<>();
        for (DataReconciliation.ReconciliationResult r : resultados) {
            if (Lado.determinar(r.x, larguraTela) == Lado.DIREITO) {
                alvosDir.add(r);
            }
        }
        if (alvosDir.isEmpty()) return;

        int K = canhoesIA.size(); // Número de clusters = número de canhões

        // ── Passo 1: K-Means Assignment (Partição de Voronoi) ────
        // Para cada alvo, encontrar o canhão mais próximo (o "dono" do cluster)
        @SuppressWarnings("unchecked")
        List<DataReconciliation.ReconciliationResult>[] clusters = new List[K];
        for (int k = 0; k < K; k++) {
            clusters[k] = new ArrayList<>();
        }

        for (DataReconciliation.ReconciliationResult alvo : alvosDir) {
            float menorDist = Float.MAX_VALUE;
            int melhorK = 0;

            for (int k = 0; k < K; k++) {
                Canhao c = canhoesIA.get(k);
                float dist = Alvo.calcularDistancia(c.getX(), c.getY(), alvo.x, alvo.y);
                if (dist < menorDist) {
                    menorDist = dist;
                    melhorK = k;
                }
            }

            clusters[melhorK].add(alvo);
        }

        // ── Passo 2: Calcular centroide ponderado de cada cluster ──
        float metade = larguraTela / 2f;

        for (int k = 0; k < K; k++) {
            Canhao canhao = canhoesIA.get(k);
            List<DataReconciliation.ReconciliationResult> cluster = clusters[k];

            if (cluster.isEmpty()) {
                // Canhão sem alvos no cluster → mover para região menos coberta
                // (centroide global dos alvos que NÃO estão em nenhum cluster vizinho)
                continue;
            }

            float sumWX = 0, sumWY = 0, sumW = 0;

            for (DataReconciliation.ReconciliationResult r : cluster) {
                float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(), r.x, r.y);
                if (dist < 1f) dist = 1f; // Evitar divisão por zero

                // Peso = probabilidade de acerto p_ij = exp(-β·d̂_ij)
                float w = (float) Math.exp(-BETA * dist);
                sumWX += w * r.x;
                sumWY += w * r.y;
                sumW += w;
            }

            if (sumW > 0) {
                float novoX = sumWX / sumW;
                float novoY = sumWY / sumW;

                // Garantir que o canhão fica no lado direito
                novoX = Math.max(novoX, metade + 30);
                novoY = Math.max(90, Math.min(novoY, alturaTela - 90));

                float distMov = Alvo.calcularDistancia(canhao.getX(), canhao.getY(),
                        novoX, novoY);
                if (distMov > 20) { // Só mover se diferença significativa
                    canhao.moverPara(novoX, novoY);
                    Log.d(TAG, String.format(
                            "K-Means: Canhão %d → cluster(%d alvos) centroide(%.0f,%.0f)",
                            k, cluster.size(), novoX, novoY));
                    if (listener != null) {
                        listener.onRealocarCanhao(canhao, novoX, novoY);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SEMEADURA INICIAL: Garantir que a IA começa com pelo menos
    //  2 canhões espalhados no lado direito.
    // ══════════════════════════════════════════════════════════════

    /**
     * Semeia 2 canhões da IA em posições espalhadas no lado direito.
     * Um no terço superior e outro no terço inferior, garantindo
     * cobertura vertical desde o início da partida.
     */
    private void semearCanhoesIniciais() {
        if (listener == null || larguraTela <= 0 || alturaTela <= 0) return;
        float energiaDir = jogo.getEnergia(Lado.DIREITO);
        if (energiaDir < 5f) return;

        // Canhão 1: centro-superior do lado direito
        float x1 = larguraTela * 0.75f;
        float y1 = alturaTela * 0.3f;
        Log.i(TAG, String.format("IA semeando canhão #1 em (%.0f, %.0f)", x1, y1));
        listener.onSugestaoAdicionarCanhao(Lado.DIREITO, x1, y1);

        // Canhão 2: centro-inferior do lado direito
        if (energiaDir > 10f) {
            float x2 = larguraTela * 0.65f;
            float y2 = alturaTela * 0.7f;
            Log.i(TAG, String.format("IA semeando canhão #2 em (%.0f, %.0f)", x2, y2));
            listener.onSugestaoAdicionarCanhao(Lado.DIREITO, x2, y2);
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

    // ── LOG DE AUDITORIA PARA PROVA MATEMÁTICA ───────────────────

    /**
     * Registra os resultados de uma reconciliação EJML no ReconciliationLog.
     * Compara distâncias brutas vs. reconciliadas, posição estimada vs. real,
     * e calcula a norma do resíduo da restrição (||Aŷ|| ≈ 0).
     */
    private void logReconciliationResults(
            DataReconciliation.ReconciliationResult[] resultados,
            float[] canhoesX, float[] canhoesY,
            float[][] mediaD,
            float[] verdadeiroX, float[] verdadeiroY,
            int N) {

        boolean usouEJML = (N >= 4);

        for (int i = 0; i < resultados.length; i++) {
            DataReconciliation.ReconciliationResult r = resultados[i];

            // Distâncias brutas (média)
            float[] brutas = (i < mediaD.length) ? mediaD[i] : new float[0];

            // Posição real sincronizada do momento do burst do radar
            float realX = r.x, realY = r.y; // fallback
            if (verdadeiroX != null && verdadeiroY != null && i < verdadeiroX.length && i < verdadeiroY.length) {
                realX = verdadeiroX[i];
                realY = verdadeiroY[i];
            }

            ReconciliationLog.getInstance().logReconciliation(
                    N, resultados.length,
                    brutas, r.distanciasReconciliadas,
                    r.x, r.y, realX, realY,
                    canhoesX, canhoesY,
                    r.normA_yHat, usouEJML);
        }
    }
}
