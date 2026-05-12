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
import com.autotarget.util.QuadTree;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.service.FirebaseIOThread;
import com.autotarget.service.ReconciliacaoThread;
import com.autotarget.service.SensorThread;
import com.autotarget.service.ThermalSensorService;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final CopyOnWriteArrayList<Alvo> alvosEsquerdo;
    private final CopyOnWriteArrayList<Alvo> alvosDireito;
    private final CopyOnWriteArrayList<Canhao> canhoesEsquerdo;
    private final CopyOnWriteArrayList<Canhao> canhoesDireito;
    
    private final Object listLock = new Object();

    private final Object collisionLock = new Object();

    /**
     * Sistema de Reserva de Alvos (AV2 — Coordenação de Disparos).
     * Mapeia Alvo → Canhao. Cada alvo só pode ser mirando por um canhão por vez.
     * Quando o tiro erra (projétil sai da tela), a reserva é liberada.
     * Quando o alvo é destruído, a reserva é limpa automaticamente.
     */
    private final ConcurrentHashMap<Alvo, Canhao> reservasAlvos = new ConcurrentHashMap<>();

    /** Pontuação de cada lado. */
    private volatile int pontuacaoEsquerdo;
    private volatile int pontuacaoDireito;

    /** Energia de cada lado. */
    private volatile float energiaEsquerdo;
    private volatile float energiaDireito;

    private volatile int tempoRestante;
    private long timestampInicio;
    private int larguraTela;
    private int alturaTela;

    private Timer spawnTimer;
    private Timer gameTimer;

    /**
     * Timer dedicado para verificação de colisões a cada 16ms (~60Hz).
     * Único responsável por contabilizar pontos e remover alvos inativos,
     * desacoplando completamente a lógica de colisão da renderização.
     */
    private Timer physicsTimer;

    private final Random random = new Random();

    // ── Threads da arquitetura ────────────────────────────────────
    private SensorThread sensorThread;
    private ReconciliacaoThread reconciliacaoThread;
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
    }

    // ── Construtor ───────────────────────────────────────────────

    public Jogo(Context context) {
        this.context = context;
        this.alvosEsquerdo = new CopyOnWriteArrayList<>();
        this.alvosDireito = new CopyOnWriteArrayList<>();
        this.canhoesEsquerdo = new CopyOnWriteArrayList<>();
        this.canhoesDireito = new CopyOnWriteArrayList<>();
        this.estado = Estado.PARADO;
        this.pontuacaoEsquerdo = 0;
        this.pontuacaoDireito = 0;
        this.energiaEsquerdo = ENERGIA_MAXIMA;
        this.energiaDireito = ENERGIA_MAXIMA;
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
        pontuacaoEsquerdo = 0;
        pontuacaoDireito = 0;
        energiaEsquerdo = ENERGIA_MAXIMA;
        energiaDireito = ENERGIA_MAXIMA;
        tempoRestante = DURACAO_PARTIDA_SEGUNDOS;
        reconciliacoesRealizadas = 0;
        timestampInicio = System.currentTimeMillis();

        // Timer de spawn de alvos
        spawnTimer = new Timer("SpawnAlvoTimer", true);
        spawnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estado == Estado.RODANDO) spawnarAlvo();
            }
        }, 0, INTERVALO_SPAWN_ALVO);

        // Timer principal: contagem regressiva + energia
        gameTimer = new Timer("GameTimer", true);
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            private int segundosDecorridos = 0;

            @Override
            public void run() {
                if (estado != Estado.RODANDO) return;
                segundosDecorridos++;
                tempoRestante = DURACAO_PARTIDA_SEGUNDOS - segundosDecorridos;

                atualizarEnergia();
                aplicarPenalidades();
                notificarTempo();
                notificarEnergia();

                if (tempoRestante <= 0) encerrarPartida();
            }
        }, 1000, 1000);

        // Physics Timer — verificação de colisões a cada 16ms (~60Hz)
        // T1: PhysicsTimer P=16ms C=2-4ms D=16ms Prio=1 (Máxima)
        physicsTimer = new Timer("PhysicsTimer", true);
        physicsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estado == Estado.RODANDO) {
                    long startNs = System.nanoTime();
                    
                    // Atualiza a física de movimentação dos canhões (Deslize a 60Hz)
                    for (Canhao c : getAllCanhoes()) {
                        if (c.isAtivo() && c.isMovendo()) {
                            c.atualizarMovimento();
                        }
                    }

                    transferirAlvosCruzados();
                    verificarColisoes();
                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    RMAAnalysis.checkDeadline("T1-Physics", elapsedMs, 16);
                }
            }
        }, 0, 16);

        // Thread Sensores/Coleta (com ref canhões para distâncias)
        sensorThread = new SensorThread(this, sensorLock, collisionLock);
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
                energiaEsquerdo = Math.min(ENERGIA_MAXIMA, energiaEsquerdo + 10f);
                energiaDireito = Math.min(ENERGIA_MAXIMA, energiaDireito + 10f);
                notificarEnergia();
            }
            @Override
            public void onSugestaoAdicionarCanhao(Lado lado, float x, float y) {
                try { adicionarCanhao(x, y, lado); } catch (JogoException e) {
                    Log.w("Jogo", "Reconciliação: não pôde adicionar canhão", e);
                }
            }
            @Override
            public void onSugestaoRemoverCanhao(Lado lado) {
                desativarUltimoCanhao(lado);
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
        for (Canhao c : getAllCanhoes()) {
            if (!c.isAlive()) c.start();
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
        Lado vencedor;
        if (pontuacaoEsquerdo > pontuacaoDireito) {
            vencedor = Lado.ESQUERDO;
        } else if (pontuacaoDireito > pontuacaoEsquerdo) {
            vencedor = Lado.DIREITO;
        } else {
            vencedor = null; // Empate
        }

        // Salvar via Thread Firebase I/O (DTO completo + criptografia)
        if (firebaseIOThread != null && firebaseIOThread.isAlive()) {
            String dados = "esquerdo=" + pontuacaoEsquerdo
                    + ";direito=" + pontuacaoDireito
                    + ";tempo=" + tempoTotal
                    + ";reconciliacoes=" + reconciliacoesRealizadas;
            String vencedorStr = vencedor != null ? vencedor.name() : "EMPATE";
            firebaseIOThread.salvarAsync(
                    pontuacaoEsquerdo, pontuacaoDireito, tempoTotal,
                    reconciliacoesRealizadas, vencedorStr, dados);
        }

        notificarEstado();
        notificarTempo();

        if (listener != null) {
            listener.onPartidaEncerrada(pontuacaoEsquerdo, pontuacaoDireito,
                    (int) tempoTotal, reconciliacoesRealizadas, vencedor);
        }
    }

    // ── Sistema de energia (por lado) ────────────────────────────

    private void atualizarEnergia() {
        int canhoesEsq = 0, canhoesDir = 0;
        for (Canhao c : canhoesEsquerdo) {
            if (c.isAtivo()) canhoesEsq++;
        }
        for (Canhao c : canhoesDireito) {
            if (c.isAtivo()) canhoesDir++;
        }

        energiaEsquerdo = Math.max(0f, energiaEsquerdo - canhoesEsq * CUSTO_ENERGIA_POR_CANHAO);
        energiaDireito = Math.max(0f, energiaDireito - canhoesDir * CUSTO_ENERGIA_POR_CANHAO);

        // Se energia de um lado acabou, desativar último canhão desse lado
        if (energiaEsquerdo <= 0f && canhoesEsq > 0) {
            desativarUltimoCanhao(Lado.ESQUERDO);
            energiaEsquerdo = 0f;
        }
        if (energiaDireito <= 0f && canhoesDir > 0) {
            desativarUltimoCanhao(Lado.DIREITO);
            energiaDireito = 0f;
        }
    }

    private void desativarUltimoCanhao(Lado lado) {
        CopyOnWriteArrayList<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        for (int i = lista.size() - 1; i >= 0; i--) {
            Canhao c = lista.get(i);
            if (c.isAtivo()) {
                c.pararCanhao();
                break;
            }
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
        CopyOnWriteArrayList<Canhao> lista = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        int count = 0;
        for (Canhao c : lista) {
            if (c.isAtivo()) count++;
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
        canhao.pararCanhao();
        canhao.interrupt();
        synchronized (listLock) {
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
        return (lado == Lado.ESQUERDO) ? energiaEsquerdo : energiaDireito;
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

        CopyOnWriteArrayList<Alvo> listaAlvos =
                (canhao.getLado() == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;

        Alvo melhor = null;
        float menorDist = Float.MAX_VALUE;

        for (Alvo alvo : listaAlvos) {
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
        for (Canhao c : canhoesEsquerdo) { if (c.isAtivo()) contEsq++; }
        for (Canhao c : canhoesDireito) { if (c.isAtivo()) contDir++; }

        for (Canhao c : canhoesEsquerdo) {
            if (c.isAtivo()) c.aplicarPenalidade(contEsq);
        }
        for (Canhao c : canhoesDireito) {
            if (c.isAtivo()) c.aplicarPenalidade(contDir);
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

        CopyOnWriteArrayList<Canhao> listaCanhoes = (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
        int countLado = listaCanhoes.size();

        if (countLado >= MAX_CANHOES_POR_LADO) {
            throw new JogoException(
                    "Máximo de canhões no lado " + lado + " atingido (" + MAX_CANHOES_POR_LADO + ")!");
        }

        // Verificar energia do lado
        float energiaLado = (lado == Lado.ESQUERDO) ? energiaEsquerdo : energiaDireito;
        if (estado == Estado.RODANDO && energiaLado < CUSTO_ENERGIA_POR_CANHAO) {
            throw new JogoException("Energia insuficiente no lado " + lado + "!");
        }

        CopyOnWriteArrayList<Alvo> listaAlvos = (lado == Lado.ESQUERDO) ? alvosEsquerdo : alvosDireito;
        Canhao canhao = new Canhao(x, y, lado, listaAlvos, collisionLock,
                larguraTela, alturaTela, this);

        // Penalty check
        if (countLado >= LIMIAR_PENALIDADE) {
            canhao.aplicarPenalidade(true);
        }

        listaCanhoes.add(canhao);

        if (estado == Estado.RODANDO) {
            canhao.start();
        }
    }

    private void spawnarAlvo() {
        if (larguraTela <= 0 || alturaTela <= 0) return;

        float x = RAIO_ALVO + random.nextFloat() * (larguraTela - 2 * RAIO_ALVO);
        float y = RAIO_ALVO + random.nextFloat() * (alturaTela - 2 * RAIO_ALVO);

        Alvo alvo;
        if (random.nextBoolean()) {
            alvo = new AlvoComum(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela);
        } else {
            alvo = new AlvoRapido(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela);
        }

        Lado lado = Lado.determinar(x, larguraTela);
        if (lado == Lado.ESQUERDO) {
            alvosEsquerdo.add(alvo);
        } else {
            alvosDireito.add(alvo);
        }

        alvo.start();
    }

    // ── Verificação de colisões (por lado) ───────────────────────

    /**
     * Implementa a exigência da rubrica de transferência atômica de alvos
     * que cruzam a linha central da tela.
     */
    private void transferirAlvosCruzados() {
        synchronized (listLock) {
            for (Alvo alvo : alvosEsquerdo) {
                if (Lado.determinar(alvo.getX(), larguraTela) == Lado.DIREITO) {
                    alvosEsquerdo.remove(alvo);
                    alvosDireito.add(alvo);
                }
            }
            for (Alvo alvo : alvosDireito) {
                if (Lado.determinar(alvo.getX(), larguraTela) == Lado.ESQUERDO) {
                    alvosDireito.remove(alvo);
                    alvosEsquerdo.add(alvo);
                }
            }
        }
    }

    public int verificarColisoes() {
        // Reconstruir QuadTree (Otimização AV4)
        if (useQuadTree && larguraTela > 0 && alturaTela > 0) {
            synchronized (collisionLock) {
                quadTreeEsquerdo = new QuadTree(0, new RectF(0, 0, larguraTela / 2f, alturaTela));
                quadTreeDireito = new QuadTree(0, new RectF(larguraTela / 2f, 0, larguraTela, alturaTela));
                for (Alvo alvo : alvosEsquerdo) {
                    if (alvo.isAtivo()) quadTreeEsquerdo.insert(alvo);
                }
                for (Alvo alvo : alvosDireito) {
                    if (alvo.isAtivo()) quadTreeDireito.insert(alvo);
                }
            }
        } else {
            quadTreeEsquerdo = null;
            quadTreeDireito = null;
        }

        int destruidos = 0;
        synchronized (collisionLock) {
            for (Alvo alvo : getAllAlvos()) {
                if (!alvo.isAtivo()) destruidos++;
            }
        }

        if (destruidos > 0) {
            synchronized (listLock) {
                for (Alvo alvo : alvosEsquerdo) {
                    if (!alvo.isAtivo()) {
                        pontuacaoEsquerdo += calcularPontosAbate(alvo);
                        energiaEsquerdo = Math.min(ENERGIA_MAXIMA,
                                energiaEsquerdo + calcularEnergiaRegenerada(alvo));
                        alvosEsquerdo.remove(alvo);
                    }
                }
                for (Alvo alvo : alvosDireito) {
                    if (!alvo.isAtivo()) {
                        pontuacaoDireito += calcularPontosAbate(alvo);
                        energiaDireito = Math.min(ENERGIA_MAXIMA,
                                energiaDireito + calcularEnergiaRegenerada(alvo));
                        alvosDireito.remove(alvo);
                    }
                }
            }
            notificarPontuacao();
            notificarEnergia();
        }

        notificarAlvosAtivos();
        return destruidos;
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
        if (idadeMs < 2000) return 5f;  // Abate ultra-rápido: +5 energia
        if (idadeMs < 4000) return 3f;  // Abate médio: +3 energia
        if (idadeMs < 7000) return 1f;  // Abate lento: +1 energia
        return 0f;                      // Muito lento: sem regen
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void pararTimers() {
        if (spawnTimer != null) { spawnTimer.cancel(); spawnTimer = null; }
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        if (physicsTimer != null) { physicsTimer.cancel(); physicsTimer = null; }
    }

    private void pararTodasThreads() {
        for (Alvo a : getAllAlvos()) { a.setAtivo(false); a.interrupt(); }
        for (Canhao c : getAllCanhoes()) { c.pararCanhao(); c.interrupt(); }
        if (sensorThread != null) { sensorThread.setAtivo(false); sensorThread.interrupt(); }
        if (reconciliacaoThread != null) { reconciliacaoThread.setAtivo(false); reconciliacaoThread.interrupt(); }
        if (thermalSensorService != null) { thermalSensorService.parar(); }
        if (firebaseIOThread != null) {
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                firebaseIOThread.setAtivo(false);
                firebaseIOThread.interrupt();
            }, "FirebaseShutdown").start();
        }
    }

    // ── Notificações ─────────────────────────────────────────────

    private void notificarPontuacao() {
        if (listener != null) listener.onPontuacaoAtualizada(pontuacaoEsquerdo, pontuacaoDireito);
    }

    private void notificarEstado() {
        if (listener != null) listener.onEstadoAlterado(estado);
    }

    private void notificarAlvosAtivos() {
        if (listener != null) {
            int ativos = 0;
            for (Alvo a : getAllAlvos()) { if (a.isAtivo()) ativos++; }
            listener.onAlvosAtivosAtualizado(ativos);
        }
    }

    private void notificarTempo() {
        if (listener != null) listener.onTempoAtualizado(tempoRestante);
    }

    private void notificarEnergia() {
        if (listener != null) listener.onEnergiaAtualizada(energiaEsquerdo, energiaDireito);
    }

    // ── Getters / Setters ────────────────────────────────────────

    public Estado getEstado() { return estado; }
    public int getPontuacaoEsquerdo() { return pontuacaoEsquerdo; }
    public int getPontuacaoDireito() { return pontuacaoDireito; }
    public float getEnergiaEsquerdo() { return energiaEsquerdo; }
    public float getEnergiaDireito() { return energiaDireito; }
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

    public void setDimensoesTela(int largura, int altura) {
        this.larguraTela = largura;
        this.alturaTela = altura;
    }

    public int getLarguraTela() { return larguraTela; }
    public int getAlturaTela() { return alturaTela; }

    public void setListener(OnJogoListener listener) { this.listener = listener; }

    public static float getEnergiaMaxima() { return ENERGIA_MAXIMA; }
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }

    public void setUseQuadTree(boolean use) { this.useQuadTree = use; }
    public boolean isUseQuadTree() { return useQuadTree; }
    public QuadTree getQuadTree(Lado lado) { 
        return lado == Lado.ESQUERDO ? quadTreeEsquerdo : quadTreeDireito; 
    }
    public FirestoreRepository getFirestoreRepository() { return firestoreRepository; }
    public Cryptography getCryptography() { return cryptography; }
    public CopyOnWriteArrayList<Canhao> getCanhoesEsquerdo() { return canhoesEsquerdo; }
    public CopyOnWriteArrayList<Canhao> getCanhoesDireito() { return canhoesDireito; }
    public CopyOnWriteArrayList<Alvo> getAlvosEsquerdo() { return alvosEsquerdo; }
    public CopyOnWriteArrayList<Alvo> getAlvosDireito() { return alvosDireito; }

    public List<Alvo> getAllAlvos() {
        List<Alvo> list = new ArrayList<>();
        list.addAll(alvosEsquerdo);
        list.addAll(alvosDireito);
        return list;
    }

    public List<Canhao> getAllCanhoes() {
        List<Canhao> list = new ArrayList<>();
        list.addAll(canhoesEsquerdo);
        list.addAll(canhoesDireito);
        return list;
    }
}
