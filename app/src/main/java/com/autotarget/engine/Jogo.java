/*
 * ============================================================================
 * Arquivo: Jogo.java
 * Pacote:  com.autotarget.engine
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Controlador central (engine) do jogo AutoTarget. Gerencia o ciclo de vida
 *   completo da partida: estados (PARADO → RODANDO → ENCERRADO), spawn de alvos,
 *   controle de canhões por lado, sistema de energia, penalidades, verificação
 *   de colisões, contagem regressiva, e orquestração de todas as threads da
 *   arquitetura (Sensor, Reconciliação, Firebase I/O).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads e funcionamento (6.1.4):
 *     - Gerencia listas CopyOnWriteArrayList de Alvo e Canhao.
 *     - Inicia e para threads: SensorThread, ReconciliacaoThread, FirebaseIOThread.
 *     - Timer "SpawnAlvoTimer" → spawna alvos aleatoriamente (AlvoComum ou AlvoRapido).
 *     - Timer "GameTimer" → contagem regressiva de 60s, atualiza energia, penalidades.
 *     - Timer "PhysicsTimer" → verificação de colisões a 16ms (~60Hz), desacoplada
 *       da renderização. Único responsável por contabilizar pontos e remover alvos.
 *     - Cada canhão adicionado é iniciado como thread independente.
 *
 *   ► Sincronização e região crítica (6.1.5):
 *     - collisionLock (Object) — lock global para verificação de colisões,
 *       compartilhado entre Projetil, SensorThread e Jogo (região crítica).
 *     - sensorLock (Object) — lock para sincronização entre SensorThread e
 *       ReconciliacaoThread (produtor-consumidor).
 *     - CopyOnWriteArrayList para alvos e canhões — estrutura thread-safe.
 *     - Método iniciar() e pararJogo() são synchronized.
 *     - volatile em estado, pontuação, energia e tempoRestante.
 *     - Defensive copy em getAlvos()/getCanhoes() para proteger referências internas.
 *
 *   LOCK ORDERING (regra global — nunca adquirir na ordem inversa):
 *     collisionLock → sensorLock
 *     collisionLock → projeteis (dentro de Projetil.verificarColisoes)
 *     canhoes (externo) → projeteis (interno, dentro de GameSurfaceView)
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - adicionarCanhao() lança JogoException para: canhão fora dos limites,
 *       máximo de canhões por lado atingido, energia insuficiente.
 *     - iniciar() lança JogoException se o jogo já estiver rodando.
 *     - Validações preventivas com mensagens úteis.
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - spawnarAlvo() cria aleatoriamente AlvoComum ou AlvoRapido (subclasses de Alvo),
 *       demonstrando polimorfismo ao iterar a lista como List<Alvo>.
 *     - Enum Estado (PARADO, RODANDO, ENCERRADO) modela transições de estado.
 *
 *   ► Cenário competitivo (seção 4.2):
 *     - Tela dividida em dois campos (ESQUERDO / DIREITO).
 *     - Energia independente por lado, consumida por canhão ativo por segundo.
 *     - Penalidade de taxa de disparo para canhões além de LIMIAR_PENALIDADE.
 *     - Pontuação atribuída ao lado onde o alvo estava quando foi destruído.
 *     - Determinação de vencedor ao final de 60 segundos.
 *
 *   ► Reconciliação de dados (seção 4.2):
 *     - Instancia DataReconciliation, SensorThread e ReconciliacaoThread.
 *     - A cada reconciliação bem-sucedida, restaura +10 de energia por lado.
 *     - Contador reconciliacoesRealizadas é reportado ao encerrar a partida.
 *
 *   ► Persistência (Firebase):
 *     - Instancia FirestoreRepository e Cryptography.
 *     - Ao encerrar, enfileira dados criptografados na FirebaseIOThread.
 *
 * CICLO DO JOGO (Loop Principal):
 *   Estado PARADO → iniciar() → Estado RODANDO
 *   ↓ (a cada segundo via GameTimer)
 *   PhysicsTimer verifica colisões a 16ms → Render thread só desenha a 30 FPS
 *   → atualizar placar/energia → a cada 10s reconciliação → fim do tempo?
 *   → Se sim: encerrar → mostrar resultados → salvar no Firestore → ENCERRADO
 *
 * ============================================================================
 */
package com.autotarget.engine;

import android.content.Context;
import android.util.Log;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;
import com.autotarget.network.FirestoreRepository;
import com.autotarget.util.Cryptography;
import com.autotarget.util.DataReconciliation;
import com.autotarget.util.DataStarvationController;
import com.autotarget.util.QuadTree;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ReconciliationLog;
import com.autotarget.service.FirebaseIOThread;
import com.autotarget.service.ReconciliacaoThread;
import com.autotarget.service.SensorThread;
import com.autotarget.service.ThermalSensorService;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador central do jogo AutoTarget.
 * <p>
 * <b>Cenário (seção 4.2):</b>
 * A tela é dividida verticalmente em duas áreas (campo esquerdo e direito).
 * Cada área possui seu próprio conjunto de canhões e orçamento de energia.
 * Alvos surgem em posições aleatórias e pertencem ao lado onde estão;
 * ao cruzar a linha divisória, passam a pertencer ao outro lado.
 * Canhões só podem abater alvos do seu lado.
 * Canhões acima do limiar sofrem penalidade na taxa de disparo.
 */
public class Jogo {

    // ── Enumeração de estados ────────────────────────────────────

    public enum Estado {
        PARADO,
        RODANDO,
        ENCERRADO
    }

    // ── Constantes ───────────────────────────────────────────────

    private static final int MAX_CANHOES_POR_LADO = 10;
    private static final int LIMIAR_PENALIDADE = 5;
    private static final int INTERVALO_SPAWN_ALVO = 3000;
    private static final float RAIO_ALVO = 30f;
    private static final float VELOCIDADE_ALVO = 3f;
    public static final int DURACAO_PARTIDA_SEGUNDOS = 60;
    private static final float ENERGIA_MAXIMA = 100f;
    private static final float CUSTO_ENERGIA_POR_CANHAO = 1f;

    // ── Atributos ────────────────────────────────────────────────

    private volatile Estado estado;

    /** Listas por lado (exigência AV2) */
    private final List<Alvo> alvosEsquerdo;
    private final List<Alvo> alvosDireito;
    private final List<Canhao> canhoesEsquerdo;
    private final List<Canhao> canhoesDireito;
    
    private final Object listLock = new Object();
    private final Object canhoesLock = new Object();

    private final Object collisionLock = new Object();

    /**
     * Sistema de Reserva de Alvos (AV2 — Coordenação de Disparos).
     * Mapeia Alvo → Canhao. Cada alvo só pode ser mirando por um canhão por vez.
     * Quando o tiro erra (projétil sai da tela), a reserva é liberada.
     * Quando o alvo é destruído, a reserva é limpa automaticamente.
     */
    private final ConcurrentHashMap<Alvo, Canhao> reservasAlvos = new ConcurrentHashMap<>();

    /** Pontuação de cada lado. */
    private final AtomicInteger pontuacaoEsquerdo = new AtomicInteger(0);
    private final AtomicInteger pontuacaoDireito = new AtomicInteger(0);

    /** Energia de cada lado. */
    private final com.autotarget.util.EnergyManager energyManagerEsquerdo;
    private final com.autotarget.util.EnergyManager energyManagerDireito;

    private volatile int tempoRestante;
    private long timestampInicio;
    private int larguraTela;
    private int alturaTela;

    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> spawnTask;
    private ScheduledFuture<?> gameTask;

    /**
     * Executor dedicado para verificação de colisões a cada 16ms (~60Hz).
     * Único responsável por contabilizar pontos e remover alvos inativos,
     * desacoplando completamente a lógica de colisão da renderização.
     */
    private ScheduledFuture<?> physicsTask;

    // Buffers para evitar GC Churn na transferência de alvos
    private final List<Alvo> transferBufferEsquerda = new ArrayList<>();
    private final List<Alvo> transferBufferDireita = new ArrayList<>();

    // Buffers para evitar GC Churn no QuadTree
    private RectF boundsEsquerdo;
    private RectF boundsDireito;

    private final Random random = new Random();

    // ── Threads da arquitetura ────────────────────────────────────
    private SensorThread sensorThread;
    private ReconciliacaoThread reconciliacaoThread;
    private DataStarvationController starvationController;
    private FirebaseIOThread firebaseIOThread;
    private final Object sensorLock = new Object();

    private final DataReconciliation dataReconciliation;
    private final FirestoreRepository firestoreRepository;
    private final Cryptography cryptography;
    private ThermalSensorService thermalSensorService;
    private Context context;

    /** QuadTree para otimização espacial (AV4). Toggle para benchmark. */
    private volatile boolean useQuadTree = false;
    private QuadTree quadTreeEsquerdo;
    private QuadTree quadTreeDireito;
    private volatile int reconciliacoesRealizadas;

    private OnJogoListener listener;

    // ── Interface de callback ────────────────────────────────────

    public interface OnJogoListener {
        void onPontuacaoAtualizada(int pontuacaoEsq, int pontuacaoDir);
        void onEstadoAlterado(Estado estado);
        void onAlvosAtivosAtualizado(int count);
        void onTempoAtualizado(int segundosRestantes);
        void onEnergiaAtualizada(float energiaEsq, float energiaDir);
        void onPartidaEncerrada(int pontEsq, int pontDir, int tempoTotal,
                                int reconciliacoes, Lado vencedor);
        void onRelatorioReconciliacao(String relatorio);
    }

    // ── Construtor ───────────────────────────────────────────────

    public Jogo(Context context) {
        this.context = context;
        this.alvosEsquerdo = new CopyOnWriteArrayList<>();
        this.alvosDireito = new CopyOnWriteArrayList<>();
        this.canhoesEsquerdo = new CopyOnWriteArrayList<>();
        this.canhoesDireito = new CopyOnWriteArrayList<>();
        this.estado = Estado.PARADO;
        this.pontuacaoEsquerdo.set(0);
        this.pontuacaoDireito.set(0);
        // FIX: Vulnerabilidade TOCTOU na Gestão de Energia (Uso exclusivo do EnergyManager)
        this.energyManagerEsquerdo = new com.autotarget.util.EnergyManager(ENERGIA_MAXIMA, ENERGIA_MAXIMA);
        this.energyManagerDireito = new com.autotarget.util.EnergyManager(ENERGIA_MAXIMA, ENERGIA_MAXIMA);
        this.tempoRestante = DURACAO_PARTIDA_SEGUNDOS;
        this.reconciliacoesRealizadas = 0;
        this.dataReconciliation = new DataReconciliation();
        this.firestoreRepository = new FirestoreRepository();
        this.cryptography = new Cryptography();
    }

    /** Construtor sem Context (para testes). */
    public Jogo() {
        this(null);
    }

    // ── Controle do jogo ─────────────────────────────────────────

    public synchronized void iniciar() throws JogoException {
        if (estado == Estado.RODANDO) {
            throw new JogoException("O jogo já está em execução!");
        }

        estado = Estado.RODANDO;
        Log.i("DEBUG_JOGO", "Iniciando partida...");
        pontuacaoEsquerdo.set(0);
        pontuacaoDireito.set(0);
        // FIX: Vulnerabilidade TOCTOU na Gestão de Energia
        energyManagerEsquerdo.set(ENERGIA_MAXIMA);
        energyManagerDireito.set(ENERGIA_MAXIMA);
        tempoRestante = DURACAO_PARTIDA_SEGUNDOS;
        reconciliacoesRealizadas = 0;
        timestampInicio = System.currentTimeMillis();
        ReconciliationLog.getInstance().reset();

        // Substituindo Timers por ScheduledThreadPoolExecutor para Hard Real-Time
        executorService = Executors.newScheduledThreadPool(3);

        // Agendador de spawn de alvos
        spawnTask = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                if (estado == Estado.RODANDO) {
                    spawnarAlvo();
                    agendarProximoSpawn();
                }
            }
        }, 0, TimeUnit.MILLISECONDS);

        // Agendador principal: contagem regressiva + energia
        gameTask = executorService.scheduleAtFixedRate(new Runnable() {
            private int segundosDecorridos = 0;

            @Override
            public void run() {
                if (estado != Estado.RODANDO) return;
                segundosDecorridos++;
                tempoRestante = DURACAO_PARTIDA_SEGUNDOS - segundosDecorridos;

                atualizarEnergia();
                aplicarPenalidades();
                if (starvationController != null) {
                    starvationController.monitorarEAtuar();
                }
                registrarMetricasEnergiaPenalidade();
                registrarMetricasEstruturadas();
                logMemoria();
                notificarTempo();
                notificarEnergia();

                if (tempoRestante <= 0) encerrarPartida();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        // Physics Executor — verificação de colisões a cada 16ms (~60Hz)
        // T1: PhysicsTask P=16ms C=2-4ms D=16ms Prio=1 (Máxima)
        physicsTask = executorService.scheduleAtFixedRate(new Runnable() {
            private boolean affinitySet = false;

            @Override
            public void run() {
                if (!affinitySet) {
                    com.autotarget.util.ThreadAffinityHelper.setAffinityForCriticalTask(android.os.Process.myTid());
                    affinitySet = true;
                }

                if (estado == Estado.RODANDO) {
                    long startNs = System.nanoTime();
                    
                    // Atualiza a física de movimentação dos canhões (Deslize a 60Hz)
                    synchronized (canhoesLock) {
                        for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                            Canhao c = canhoesEsquerdo.get(i);
                            if (c.isAtivo() && c.isMovendo()) {
                                c.atualizarMovimento();
                            }
                        }
                        for (int i = 0; i < canhoesDireito.size(); i++) {
                            Canhao c = canhoesDireito.get(i);
                            if (c.isAtivo() && c.isMovendo()) {
                                c.atualizarMovimento();
                            }
                        }
                    }

                    transferirAlvosCruzados();
                    verificarColisoes();
                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    RMAAnalysis.checkDeadline("T1-Physics", elapsedMs, 16);
                }
            }
        }, 0, 16, TimeUnit.MILLISECONDS);

        // Thread Sensores/Coleta (com ref canhões para distâncias)
        sensorThread = new SensorThread(this, sensorLock, collisionLock);
        starvationController = new DataStarvationController(sensorThread);
        sensorThread.start();

        // Thread Reconciliação+Otimização (com EJML)
        reconciliacaoThread = new ReconciliacaoThread(
                dataReconciliation, sensorThread, this,
                sensorLock, collisionLock);
        reconciliacaoThread.setLarguraTela(larguraTela);
        reconciliacaoThread.setAlturaTela(alturaTela);
        reconciliacaoThread.setListener(new ReconciliacaoThread.OnReconciliacaoListener() {
            @Override
            public void onReconciliacaoConcluida(int totalRec) {
                reconciliacoesRealizadas = totalRec;
                // FIX: Vulnerabilidade TOCTOU na Gestão de Energia
                energyManagerEsquerdo.add(10f);
                energyManagerDireito.add(10f);
                notificarEnergia();
            }
            @Override
            public void onSugestaoAdicionarCanhao(Lado lado, float x, float y) {
                try { adicionarCanhao(x, y, lado); } catch (JogoException e) {
                    Log.w("Jogo", "Reconciliação: não pôde adicionar canhão", e);
                }
            }
            @Override
            public void onSugestaoRemoverCanhao(Lado lado, Canhao canhao) {
                removerCanhao(canhao);
            }
            @Override
            public void onRealocarCanhao(Canhao canhao, float novoX, float novoY) {
                // Já tratado dentro do ReconciliacaoThread via canhao.moverPara()
            }
        });
        reconciliacaoThread.start();

        // Thread Firebase I/O
        firebaseIOThread = new FirebaseIOThread(firestoreRepository, cryptography);
        firebaseIOThread.start();

        // Serviço térmico ciberfísico (AV3)
        if (context != null) {
            thermalSensorService = new ThermalSensorService(
                    context, getAllCanhoes(), firestoreRepository);
            thermalSensorService.iniciar();
        }

        // Análise RMA no início do jogo
        RMAAnalysis.executarAnalise();

        // Iniciar threads dos canhões existentes
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                Canhao c = canhoesEsquerdo.get(i);
                if (!c.isAlive()) c.start();
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                Canhao c = canhoesDireito.get(i);
                if (!c.isAlive()) c.start();
            }
        }

        notificarEstado();
        notificarTempo();
        notificarEnergia();
        notificarPontuacao();
    }

    public synchronized void pararJogo() {
        if (estado != Estado.RODANDO) return;
        estado = Estado.ENCERRADO;
        pararTimers();
        pararTodasThreads();
        notificarEstado();
    }

    private synchronized void encerrarPartida() {
        if (estado != Estado.RODANDO) return;
        estado = Estado.ENCERRADO;
        tempoRestante = 0;
        pararTimers();
        pararTodasThreads();

        long tempoTotal = (System.currentTimeMillis() - timestampInicio) / 1000;

        // Determinar vencedor
        int pEsq = pontuacaoEsquerdo.get();
        int pDir = pontuacaoDireito.get();
        Lado vencedor;
        if (pEsq > pDir) {
            vencedor = Lado.ESQUERDO;
        } else if (pDir > pEsq) {
            vencedor = Lado.DIREITO;
        } else {
            vencedor = null; // Empate
        }

        // Salvar via Thread Firebase I/O (DTO completo + criptografia)
        if (firebaseIOThread != null && firebaseIOThread.isAlive()) {
            String dados = "esquerdo=" + pEsq
                    + ";direito=" + pDir
                    + ";tempo=" + tempoTotal
                    + ";reconciliacoes=" + reconciliacoesRealizadas;
            String vencedorStr = vencedor != null ? vencedor.name() : "EMPATE";
            firebaseIOThread.salvarAsync(
                    pEsq, pDir, tempoTotal,
                    reconciliacoesRealizadas, vencedorStr, dados);
        }

        // Gerar relatório de reconciliação
        String relatorio = ReconciliationLog.getInstance().gerarRelatorio();

        notificarEstado();
        notificarTempo();

        if (listener != null) {
            listener.onPartidaEncerrada(pEsq, pDir,
                    (int) tempoTotal, reconciliacoesRealizadas, vencedor);
            listener.onRelatorioReconciliacao(relatorio);
        }
    }

    // ── Sistema de energia (por lado) ────────────────────────────

    private void atualizarEnergia() {
        int canhoesEsq = 0, canhoesDir = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                if (canhoesEsquerdo.get(i).isAtivo()) canhoesEsq++;
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                if (canhoesDireito.get(i).isAtivo()) canhoesDir++;
            }
        }
        // FIX: Vulnerabilidade TOCTOU na Gestão de Energia
        // O débito precisa ocorrer por canhão ativo; um único remove() em lote pode falhar
        // quando a energia restante é menor que o total pedido, deixando saldo artificial.
        for (int i = 0; i < canhoesEsq; i++) {
            if (!energyManagerEsquerdo.tryRemove(CUSTO_ENERGIA_POR_CANHAO)) {
                energyManagerEsquerdo.set(0f);
                break;
            }
        }
        for (int i = 0; i < canhoesDir; i++) {
            if (!energyManagerDireito.tryRemove(CUSTO_ENERGIA_POR_CANHAO)) {
                energyManagerDireito.set(0f);
                break;
            }
        }

        // Se energia de um lado acabou, desativar todos os canhões desse lado
        if (energyManagerEsquerdo.get() <= 0f && canhoesEsq > 0) {
            desativarTodosCanhoes(Lado.ESQUERDO);
            energyManagerEsquerdo.set(0f);
        }
        if (energyManagerDireito.get() <= 0f && canhoesDir > 0) {
            desativarTodosCanhoes(Lado.DIREITO);
            energyManagerDireito.set(0f);
        }
    }

    // Exposição dos dados via broadcast para ferramentas de profiling:
    private void registrarMetricasEnergiaPenalidade() {
        // FIX 4: Snapshot atômico dos valores de energia para evitar desync no relatório consolidado
        float energiaEsq = energyManagerEsquerdo.get();
        float energiaDir = energyManagerDireito.get();
        
        int canhoesEsq = contarCanhoesAtivos(Lado.ESQUERDO);
        int canhoesDir = contarCanhoesAtivos(Lado.DIREITO);
        double intervaloEsq = calcularIntervaloMedio(canhoesEsquerdo);
        double intervaloDir = calcularIntervaloMedio(canhoesDireito);

        Log.i("Autotarget-Plotter", "EnergiaEsq:" + energiaEsq + ",EnergiaDir:" + energiaDir + ",Intervalo:" + intervaloEsq);

        ReconciliationLog.getInstance().logEnergyPenalty(
                energiaEsq, energiaDir, canhoesEsq, canhoesDir, intervaloEsq, intervaloDir);
    }

    private double calcularIntervaloMedio(List<Canhao> canhoes) {
        if (canhoes == null || canhoes.isEmpty()) return 0;
        double soma = 0;
        int ativos = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoes.size(); i++) {
                Canhao c = canhoes.get(i);
                if (!c.isAtivo()) continue;
                soma += c.getIntervaloDisparo();
                ativos++;
            }
        }
        return ativos == 0 ? 0 : (soma / ativos);
    }

    private void desativarTodosCanhoes(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        List<Canhao> canhoesParaParar = new ArrayList<>();
        synchronized (canhoesLock) {
            for (int i = 0; i < lista.size(); i++) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) {
                    canhoesParaParar.add(c);
                }
            }
        }

        // Atribuição atômica/imediata da flag de parada para todos os canhões simultaneamente
        for (Canhao c : canhoesParaParar) {
            c.setAtivo(false);
        }

        // Parar (join) os canhões fora do bloco synchronized para evitar deadlock
        for (Canhao c : canhoesParaParar) {
            pararCanhaoComJoin(c, 300);
        }
    }

    private void desativarUltimoCanhao(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        Canhao cParaParar = null;
        synchronized (canhoesLock) {
            for (int i = lista.size() - 1; i >= 0; i--) {
                Canhao c = lista.get(i);
                if (c.isAtivo()) {
                    cParaParar = c;
                    break;
                }
            }
        }
        if (cParaParar != null) {
            pararCanhaoComJoin(cParaParar, 300);
        }
    }

    /**
     * Conta quantos canhões ativos existem num dado lado.
     * Usado pelo Canhao.run() para recalcular penalidade dinâmica
     * antes de cada disparo (Fase 1 — AV2 §6.2.2-b).
     *
     * @param lado lado a consultar
     * @return número de canhões ativos nesse lado
     */
    public int contarCanhoesAtivos(Lado lado) {
        List<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        int count = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).isAtivo()) count++;
            }
        }
        return count;
    }

    /**
     * Remove um canhão específico de forma atômica e segura.
     * Usado pela ReconciliacaoThread (IA) para desmontar canhões
     * de baixa utilidade marginal (Fase 2 — AV2 §6.2.2-e).
     *
     * @param canhao o canhão a ser removido
     */
    public void removerCanhao(Canhao canhao) {
        if (canhao == null) return;
        pararCanhaoComJoin(canhao, 500);
        synchronized (canhoesLock) {
            if (canhao.getLado() == Lado.ESQUERDO) {
                canhoesEsquerdo.remove(canhao);
            } else {
                canhoesDireito.remove(canhao);
            }
        }
        Log.i("Jogo", "Canhão removido do lado " + canhao.getLado()
                + " — restam " + contarCanhoesAtivos(canhao.getLado()) + " ativos");
    }

    /**
     * Retorna a energia atual de um dado lado.
     * @param lado o lado a consultar
     * @return energia atual
     */
    public float getEnergia(Lado lado) {
        // FIX: Vulnerabilidade TOCTOU na Gestão de Energia
        return (lado == Lado.ESQUERDO) ? energyManagerEsquerdo.get() : energyManagerDireito.get();
    }

    /**
     * Retorna o gerenciador de energia para um dado lado.
     * @param lado o lado a consultar
     * @return EnergyManager associado ao lado
     */
    public com.autotarget.util.EnergyManager getEnergyManager(Lado lado) {
        return (lado == Lado.ESQUERDO) ? energyManagerEsquerdo : energyManagerDireito;
    }

    // ── Sistema de Reserva de Alvos (Coordenação de Disparos) ────

    /**
     * Reserva o alvo mais próximo disponível (não reservado por outro canhão)
     * para o canhão solicitante. Retorna o alvo reservado, ou null se não
     * houver alvos livres.
     *
     * Regra: cada alvo só pode ser alvo de UM canhão por vez.
     * Somente quando o tiro erra (projétil sai da tela) a reserva é liberada
     * e outro canhão pode mirar nesse alvo.
     *
     * @param canhao o canhão que está solicitando um alvo
     * @return o alvo reservado, ou null
     */
    public Alvo reservarAlvo(Canhao canhao) {
        // Limpar reservas de alvos já destruídos
        limparReservasInvalidas();

        Alvo melhor = null;
        float menorDist = Float.MAX_VALUE;

        synchronized(listLock) {
            List<Alvo> listaAlvos =
                    (canhao.getLado() == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;

            for (int i = 0; i < listaAlvos.size(); i++) {
                Alvo alvo = listaAlvos.get(i);
                if (!alvo.isAtivo()) continue;

                // Verificar se já está reservado por OUTRO canhão
                Canhao dono = reservasAlvos.get(alvo);
                if (dono != null && dono != canhao && dono.isAtivo()) {
                    continue; // Alvo já comprometido por outro canhão
                }

                float dist = Alvo.calcularDistancia(canhao.getX(), canhao.getY(),
                        alvo.getX(), alvo.getY());
                if (dist < menorDist) {
                    menorDist = dist;
                    melhor = alvo;
                }
            }
        }

        if (melhor != null) {
            reservasAlvos.put(melhor, canhao);
        }

        return melhor;
    }

    /**
     * Libera a reserva de um alvo específico, permitindo que outro
     * canhão possa mirá-lo. Chamado pelo Projétil quando ele erra
     * (sai da tela sem acertar).
     *
     * @param alvo o alvo cuja reserva deve ser liberada
     */
    public void liberarAlvo(Alvo alvo) {
        if (alvo != null) {
            reservasAlvos.remove(alvo);
        }
    }

    /**
     * Remove reservas de alvos que já foram destruídos ou cujo canhão
     * dono já não está ativo. Chamado automaticamente antes de cada
     * nova reserva para manter a tabela limpa.
     */
    private void limparReservasInvalidas() {
        reservasAlvos.entrySet().removeIf(entry ->
                !entry.getKey().isAtivo() || !entry.getValue().isAtivo());
    }

    // ── Penalidade de canhões ────────────────────────────────────

    /**
     * Aplica ou remove penalidade nos canhões acima do limiar por lado.
     * Canhões além de LIMIAR_PENALIDADE no mesmo lado têm taxa de disparo 2x.
     */
    /**
     * Aplica penalidade exponencial: I = I_base × (1 + max(0, N-L) × α)
     */
    private void aplicarPenalidades() {
        int contEsq = 0, contDir = 0;
        synchronized (canhoesLock) {
            for (int i = 0; i < canhoesEsquerdo.size(); i++) { if (canhoesEsquerdo.get(i).isAtivo()) contEsq++; }
            for (int i = 0; i < canhoesDireito.size(); i++) { if (canhoesDireito.get(i).isAtivo()) contDir++; }

            for (int i = 0; i < canhoesEsquerdo.size(); i++) {
                Canhao c = canhoesEsquerdo.get(i);
                if (c.isAtivo()) c.aplicarPenalidade(contEsq);
            }
            for (int i = 0; i < canhoesDireito.size(); i++) {
                Canhao c = canhoesDireito.get(i);
                if (c.isAtivo()) c.aplicarPenalidade(contDir);
            }
        }
    }

    // ── Gerenciamento de entidades ───────────────────────────────

    /**
     * Adiciona um canhão no lado especificado.
     *
     * @param x    posição X
     * @param y    posição Y
     * @param lado lado (ESQUERDO ou DIREITO)
     */
    public void adicionarCanhao(float x, float y, Lado lado) throws JogoException {
        if (x < 0 || x > larguraTela || y < 0 || y > alturaTela) {
            throw new JogoException(
                    "Canhão fora dos limites da tela! Posição: (" + x + ", " + y + ")");
        }

        List<Canhao> listaCanhoes = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        synchronized (canhoesLock) {
            int countLado = listaCanhoes.size();

            if (countLado >= MAX_CANHOES_POR_LADO) {
                throw new JogoException(
                        "Máximo de canhões no lado " + lado + " atingido (" + MAX_CANHOES_POR_LADO + ")!");
            }

            // Verificar energia do lado
            float energiaLado = getEnergia(lado);
            if (estado == Estado.RODANDO && energiaLado < CUSTO_ENERGIA_POR_CANHAO) {
                throw new JogoException("Energia insuficiente no lado " + lado + "!");
            }

            List<Alvo> listaAlvos = (lado == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;
            Canhao canhao = new Canhao(x, y, lado, listaAlvos, collisionLock,
                    larguraTela, alturaTela, this);

            // Penalty check
            canhao.aplicarPenalidade(countLado + 1);

            listaCanhoes.add(canhao);

            if (estado == Estado.RODANDO) {
                canhao.start();
            }
        }
    }

    private void spawnarAlvo() {
        if (larguraTela <= 0 || alturaTela <= 0) return;

        // Aumentado para 4 spawns desde o início para maior intensidade e dados de reconciliação
        int quantidadeSpawns = 4;

        for (int i = 0; i < quantidadeSpawns; i++) {
            float x = RAIO_ALVO + random.nextFloat() * (larguraTela - 2 * RAIO_ALVO);
            float y = RAIO_ALVO + random.nextFloat() * (alturaTela - 2 * RAIO_ALVO);

            Alvo alvo;
            if (random.nextBoolean()) {
                alvo = new AlvoComum(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela);
            } else {
                alvo = new AlvoRapido(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela);
            }

            com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);
            Lado lado = geom.determineLado(x);
            synchronized(listLock) {
                if (lado == Lado.ESQUERDO) {
                    alvosEsquerdo.add(alvo);
                } else {
                    alvosDireito.add(alvo);
                }
            }

            ReconciliationLog.getInstance().logSpawn(x, y,
                    alvo.getClass().getSimpleName(), lado.name());

            alvo.start();
        }
    }

    private void agendarProximoSpawn() {
        if (estado != Estado.RODANDO || executorService == null || executorService.isShutdown()) return;

        // Calcula o intervalo baseado no tempo restante: 
        // Vai de INTERVALO_SPAWN_ALVO (ex: 3000ms) até 500ms no final da partida
        float proporcao = Math.max(0, (float) tempoRestante / DURACAO_PARTIDA_SEGUNDOS);
        long intervalo = 500 + (long) ((INTERVALO_SPAWN_ALVO - 500) * proporcao);

        try {
            spawnTask = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    if (estado == Estado.RODANDO) {
                        spawnarAlvo();
                        agendarProximoSpawn();
                    }
                }
            }, intervalo, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.e("Jogo", "Erro ao agendar spawn de alvo", e);
        }
    }

    // ── Verificação de colisões (por lado) ───────────────────────

    /**
     * Implementa a exigência da rubrica de transferência atômica de alvos
     * que cruzam a linha central da tela.
     */
    private void transferirAlvosCruzados() {
        if (larguraTela <= 0) return;

        com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(larguraTela, alturaTela);

        synchronized (listLock) {
            // FIX: Melhoria Técnica: Preemption Leak nos Buffers
            transferBufferDireita.clear();
            transferBufferEsquerda.clear();
            for (int i = 0; i < alvosEsquerdo.size(); i++) {
                Alvo alvo = alvosEsquerdo.get(i);
                if (geom.determineLado(alvo.getX()) == Lado.DIREITO) {
                    transferBufferDireita.add(alvo);
                }
            }
            for (int i = 0; i < alvosDireito.size(); i++) {
                Alvo alvo = alvosDireito.get(i);
                if (geom.determineLado(alvo.getX()) == Lado.ESQUERDO) {
                    transferBufferEsquerda.add(alvo);
                }
            }

            if (!transferBufferDireita.isEmpty()) {
                alvosEsquerdo.removeAll(transferBufferDireita);
                alvosDireito.addAll(transferBufferDireita);
                for (int i = 0; i < transferBufferDireita.size(); i++) liberarAlvo(transferBufferDireita.get(i));
            }
            if (!transferBufferEsquerda.isEmpty()) {
                alvosDireito.removeAll(transferBufferEsquerda);
                alvosEsquerdo.addAll(transferBufferEsquerda);
                for (int i = 0; i < transferBufferEsquerda.size(); i++) liberarAlvo(transferBufferEsquerda.get(i));
            }
        }

        if (!transferBufferDireita.isEmpty() || !transferBufferEsquerda.isEmpty()) {
            Log.d("Jogo", "Transferência de alvos: ESQ->DIR=" + transferBufferDireita.size()
                    + " DIR->ESQ=" + transferBufferEsquerda.size());
        }
    }

    public int verificarColisoes() {
        // Reconstruir QuadTree (Otimização AV4)
        if (useQuadTree && larguraTela > 0 && alturaTela > 0 && boundsEsquerdo != null) {
            synchronized (collisionLock) {
                synchronized (listLock) {
                    quadTreeEsquerdo = new QuadTree(0, boundsEsquerdo);
                    quadTreeDireito = new QuadTree(0, boundsDireito);
                    for (int i = 0; i < alvosEsquerdo.size(); i++) {
                        Alvo alvo = alvosEsquerdo.get(i);
                        if (alvo.isAtivo()) quadTreeEsquerdo.insert(alvo);
                    }
                    for (int i = 0; i < alvosDireito.size(); i++) {
                        Alvo alvo = alvosDireito.get(i);
                        if (alvo.isAtivo()) quadTreeDireito.insert(alvo);
                    }
                }
            }
        } else {
            quadTreeEsquerdo = null;
            quadTreeDireito = null;
        }

        int destruidos = 0;
        synchronized (collisionLock) {
            // FIX: Correção Crítica: Race Condition na Verificação de Colisões
            synchronized (listLock) {
                if (!alvosEsquerdo.isEmpty() || !alvosDireito.isEmpty()) {
                    destruidos += processarAlvosInativos(alvosEsquerdo, Lado.ESQUERDO);
                    destruidos += processarAlvosInativos(alvosDireito, Lado.DIREITO);
                }
            }
        }

        if (destruidos > 0) {
            notificarPontuacao();
            notificarEnergia();
        }

        notificarAlvosAtivos();
        return destruidos;
    }

    private int processarAlvosInativos(List<Alvo> lista, Lado ladoSendoProcessado) {
        if (lista.isEmpty()) return 0;
        List<Alvo> removidos = new ArrayList<>();
        int abatesConfirmadosLado = 0;

        for (int i = 0; i < lista.size(); i++) {
            Alvo alvo = lista.get(i);
            if (!alvo.isAtivo()) {
                // REGRA DE DETERMINISMO (AV2): Somente contabiliza o ponto se o
                // ladoAbate registrado no alvo for o mesmo que estamos processando.
                // Isso evita dupla contagem ou atribuição ao lado errado durante cruzamento.
                if (alvo.getLadoAbate() == ladoSendoProcessado) {
                    int pontos = calcularPontosAbate(alvo);
                    float energiaRegenerada = calcularEnergiaRegenerada(alvo);
                    
                    if (ladoSendoProcessado == Lado.ESQUERDO) {
                        pontuacaoEsquerdo.addAndGet(pontos);
                        energyManagerEsquerdo.add(energiaRegenerada);
                    } else {
                        pontuacaoDireito.addAndGet(pontos);
                        energyManagerDireito.add(energiaRegenerada);
                    }
                    abatesConfirmadosLado++;
                }
                removidos.add(alvo);
                liberarAlvo(alvo);
            }
        }

        if (!removidos.isEmpty()) {
            lista.removeAll(removidos);
        }
        return abatesConfirmadosLado;
    }

    // ── Pontuação com Penalidade Temporal ─────────────────────────

    /**
     * Calcula pontos de abate com bônus/penalidade temporal.
     * 
     * Abate rápido (<2s)  → 5 pontos (posicionamento perfeito)
     * Abate médio  (<4s)  → 3 pontos (posicionamento aceitável)
     * Abate lento  (<7s)  → 1 ponto  (penalidade por demora)
     * Abate muito lento (>7s) → 0 pontos (falha total)
     *
     * @param alvo o alvo destruído
     * @return pontos a adicionar
     */
    private int calcularPontosAbate(Alvo alvo) {
        long idadeMs = alvo.getIdadeMs();
        if (idadeMs < 2000) return 5;
        if (idadeMs < 4000) return 3;
        if (idadeMs < 7000) return 1;
        return 0; // Penalidade máxima: 0 pontos
    }

    /**
     * Calcula energia regenerada ao abater um alvo.
     * Quanto mais rápido o abate, mais energia é devolvida.
     * Isso incentiva a IA a se arriscar adicionando mais canhões:
     * mais canhões → abates mais rápidos → mais energia regenerada →
     * compensa o custo extra dos canhões.
     *
     * @param alvo o alvo destruído
     * @return energia a regenerar
     */
    private float calcularEnergiaRegenerada(Alvo alvo) {
        long idadeMs = alvo.getIdadeMs();
        if (idadeMs < 2000) return 15f; // Abate ultra-rápido: compensa ~15s de 1 canhão
        if (idadeMs < 4000) return 10f; // Abate médio: compensa ~10s de 1 canhão
        if (idadeMs < 7000) return 5f;  // Abate lento: compensa ~5s de 1 canhão
        return 2f;                      // Muito lento: recuperação mínima de segurança
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void pararTimers() {
        if (spawnTask != null) { spawnTask.cancel(true); spawnTask = null; }
        if (gameTask != null) { gameTask.cancel(true); gameTask = null; }
        if (physicsTask != null) { physicsTask.cancel(true); physicsTask = null; }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void pararTodasThreads() {
        List<Alvo> alvosParaParar = new ArrayList<>();
        synchronized(listLock) {
            alvosParaParar.addAll(alvosEsquerdo);
            alvosParaParar.addAll(alvosDireito);
        }
        for (Alvo a : alvosParaParar) {
            a.setAtivo(false);
            interromperEEsperar(a, 200);
        }
        List<Canhao> canhoesParaParar = new ArrayList<>();
        synchronized (canhoesLock) {
            canhoesParaParar.addAll(canhoesEsquerdo);
            canhoesParaParar.addAll(canhoesDireito);
        }
        for (Canhao c : canhoesParaParar) {
            pararCanhaoComJoin(c, 400);
        }
        if (sensorThread != null) {
            sensorThread.setAtivo(false);
            interromperEEsperar(sensorThread, 500);
        }
        if (reconciliacaoThread != null) {
            // Evitar vazamento de referência ao Jogo/Activity via listener
            try { reconciliacaoThread.setListener(null); } catch (Exception ignored) {}
            reconciliacaoThread.setAtivo(false);
            interromperEEsperar(reconciliacaoThread, 500);
        }
        if (thermalSensorService != null) { thermalSensorService.parar(); }
        if (firebaseIOThread != null) {
            firebaseIOThread.setAtivo(false);
            interromperEEsperar(firebaseIOThread, 1000);
        }
    }

    private void pararCanhaoComJoin(Canhao canhao, long timeoutMs) {
        if (canhao == null) return;
        canhao.pararCanhao();
        interromperEEsperar(canhao, timeoutMs);
    }

    private void interromperEEsperar(Thread thread, long timeoutMs) {
        if (thread == null) return;
        thread.interrupt();
        if (!thread.isAlive() || Thread.currentThread() == thread) return;
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Notificações ─────────────────────────────────────────────

    private void notificarPontuacao() {
        if (listener != null) listener.onPontuacaoAtualizada(pontuacaoEsquerdo.get(), pontuacaoDireito.get());
    }

    private void notificarEstado() {
        if (listener != null) listener.onEstadoAlterado(estado);
    }

    private void notificarAlvosAtivos() {
        if (listener != null) {
            int ativos = 0;
            for (int i = 0; i < alvosEsquerdo.size(); i++) { if (alvosEsquerdo.get(i).isAtivo()) ativos++; }
            for (int i = 0; i < alvosDireito.size(); i++) { if (alvosDireito.get(i).isAtivo()) ativos++; }
            listener.onAlvosAtivosAtualizado(ativos);
        }
    }

    private void notificarTempo() {
        if (listener != null) listener.onTempoAtualizado(tempoRestante);
    }

    private void notificarEnergia() {
        if (listener != null) listener.onEnergiaAtualizada(energyManagerEsquerdo.get(), energyManagerDireito.get());
    }

    // ── Getters / Setters ────────────────────────────────────────

    public Estado getEstado() { return estado; }
    public int getPontuacaoEsquerdo() { return pontuacaoEsquerdo.get(); }
    public int getPontuacaoDireito() { return pontuacaoDireito.get(); }
    public float getEnergiaEsquerdo() { return energyManagerEsquerdo.get(); }
    public float getEnergiaDireito() { return energyManagerDireito.get(); }
    public int getTempoRestante() { return tempoRestante; }
    /**
     * Retorna defensive copy da lista de alvos.
     * Protege a referência interna contra modificação externa indevida.
     */
    public List<Alvo> getAlvos() { return getAllAlvos(); }

    /**
     * Retorna defensive copy da lista de canhões.
     * Protege a referência interna contra modificação externa indevida.
     */
    public List<Canhao> getCanhoes() { return getAllCanhoes(); }
    public Object getCollisionLock() { return collisionLock; }
    public Object getCanhoesLock() { return canhoesLock; }

    public void setDimensoesTela(int largura, int altura) {
        this.larguraTela = largura;
        this.alturaTela = altura;
        if (largura > 0 && altura > 0) {
            this.boundsEsquerdo = new RectF(0, 0, largura / 2f, altura);
            this.boundsDireito = new RectF(largura / 2f, 0, largura, altura);
        }
        if (reconciliacaoThread != null) {
            reconciliacaoThread.setLarguraTela(largura);
            reconciliacaoThread.setAlturaTela(altura);
        }
    }

    public int getLarguraTela() { return larguraTela; }
    public int getAlturaTela() { return alturaTela; }

    public void setListener(OnJogoListener listener) { this.listener = listener; }

    public static float getEnergiaMaxima() { return ENERGIA_MAXIMA; }
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }

    private void logMemoria() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        Log.i("DEBUG_MEM", String.format(Locale.US, "Memória (MB) - Usada: %d, Total: %d, Max: %d",
                usedMemory / (1024 * 1024), totalMemory / (1024 * 1024), maxMemory / (1024 * 1024)));
    }

    /**
     * Registra métricas estruturadas para profiling e análise de desempenho.
     * Formato: chave=valor|chave=valor (compatível com ferramentas de plotagem).
     */
    public void registrarMetricasEstruturadas() {
        int nCanhoesEsq;
        int nCanhoeDir;
        synchronized (canhoesLock) {
            nCanhoesEsq = canhoesEsquerdo.size();
            nCanhoeDir = canhoesDireito.size();
        }
        float fatorEsq = 1.0f + Math.max(0, nCanhoesEsq - LIMIAR_PENALIDADE) * 0.2f;
        float fatorDir = 1.0f + Math.max(0, nCanhoeDir - LIMIAR_PENALIDADE) * 0.2f;
        
        Log.i("AUTOTARGET_DASHBOARD", String.format(Locale.US,
                "Tempo:%d|EsqEnergia:%.0f|DirEnergia:%.0f|EsqCanhoes:%d|DirCanhoes:%d|EsqFator:%.2f|DirFator:%.2f|EsqPts:%d|DirPts:%d",
                tempoRestante, energyManagerEsquerdo.get(), energyManagerDireito.get(),
                nCanhoesEsq, nCanhoeDir, fatorEsq, fatorDir,
                pontuacaoEsquerdo.get(), pontuacaoDireito.get()));
    }

    public void setUseQuadTree(boolean use) { this.useQuadTree = use; }
    public boolean isUseQuadTree() { return useQuadTree; }
    public QuadTree getQuadTree(Lado lado) { 
        return lado == Lado.ESQUERDO ? quadTreeEsquerdo : quadTreeDireito; 
    }
    public FirestoreRepository getFirestoreRepository() { return firestoreRepository; }
    public Cryptography getCryptography() { return cryptography; }
    public DataStarvationController getStarvationController() { return starvationController; }
    public List<Canhao> getCanhoesEsquerdo() { return canhoesEsquerdo; }
    public List<Canhao> getCanhoesDireito() { return canhoesDireito; }
    public List<Alvo> getAlvosEsquerdo() { return alvosEsquerdo; }
    public List<Alvo> getAlvosDireito() { return alvosDireito; }

    public List<Alvo> getAllAlvos() {
        List<Alvo> list = new ArrayList<>();
        synchronized(listLock) {
            list.addAll(alvosEsquerdo);
            list.addAll(alvosDireito);
        }
        return list;
    }

    public List<Canhao> getAllCanhoes() {
        List<Canhao> list = new ArrayList<>();
        synchronized (canhoesLock) {
            list.addAll(canhoesEsquerdo);
            list.addAll(canhoesDireito);
        }
        return list;
    }
}
