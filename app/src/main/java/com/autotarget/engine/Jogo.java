package com.autotarget.engine;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;
import com.autotarget.service.Cryptography;
import com.autotarget.service.DataReconciliation;
import com.autotarget.service.FirebaseIOThread;
import com.autotarget.service.FirestoreRepository;
import com.autotarget.service.ReconciliacaoThread;
import com.autotarget.service.SensorThread;

import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList; // CORREÇÃO 1: Uso de ArrayList padrão ao invés de CopyOnWriteArrayList

/**
 * Controlador central do jogo AutoTarget.
 */
public class Jogo {

    public enum Estado {
        PARADO,
        RODANDO,
        ENCERRADO
    }

    private static final int MAX_CANHOES_POR_LADO = 10;
    private static final int LIMIAR_PENALIDADE = 5;
    private static final int INTERVALO_SPAWN_ALVO = 3000;
    private static final float RAIO_ALVO = 30f;
    private static final float VELOCIDADE_ALVO = 3f;
    public static final int DURACAO_PARTIDA_SEGUNDOS = 60;
    private static final float ENERGIA_MAXIMA = 100f;
    private static final float CUSTO_ENERGIA_POR_CANHAO = 5f;

    private volatile Estado estado;

    /** Lista global de alvos (alvos se movem livremente pela tela). */
    private final ArrayList<Alvo> alvos; // CORREÇÃO 1: Troca para ArrayList padrão

    /** Lista global de canhões (cada um tem seu Lado). */
    private final ArrayList<Canhao> canhoes; // CORREÇÃO 1: Troca para ArrayList padrão

    private final Object collisionLock = new Object();

    private volatile int pontuacaoEsquerdo;
    private volatile int pontuacaoDireito;
    private volatile float energiaEsquerdo;
    private volatile float energiaDireito;

    private volatile int tempoRestante;
    private long timestampInicio;
    private int larguraTela;
    private int alturaTela;

    private Timer spawnTimer;
    private Timer gameTimer;
    private Timer physicsTimer; // CORREÇÃO 3: Timer dedicado para a lógica de negócio (física e colisões)

    private final Random random = new Random();

    private SensorThread sensorThread;
    private ReconciliacaoThread reconciliacaoThread;
    private FirebaseIOThread firebaseIOThread;
    private final Object sensorLock = new Object();

    private final DataReconciliation dataReconciliation;
    private final FirestoreRepository firestoreRepository;
    private final Cryptography cryptography;
    private volatile int reconciliacoesRealizadas;

    private OnJogoListener listener;

    public interface OnJogoListener {
        void onPontuacaoAtualizada(int pontuacaoEsq, int pontuacaoDir);
        void onEstadoAlterado(Estado estado);
        void onAlvosAtivosAtualizado(int count);
        void onTempoAtualizado(int segundosRestantes);
        void onEnergiaAtualizada(float energiaEsq, float energiaDir);
        void onPartidaEncerrada(int pontEsq, int pontDir, int tempoTotal,
                                int reconciliacoes, Lado vencedor);
    }

    /**
     * Construtor da classe Jogo.
     * Inicializa as listas e o estado da partida.
     */
    public Jogo() {
        this.alvos = new ArrayList<>(); // CORREÇÃO 1
        this.canhoes = new ArrayList<>(); // CORREÇÃO 1
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

    /**
     * Inicia a partida.
     * Configura os temporizadores para geração de alvos, física e regras de negócio.
     *
     * @throws JogoException se o jogo já estiver em andamento.
     */
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

        spawnTimer = new Timer("SpawnAlvoTimer", true);
        spawnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estado == Estado.RODANDO) spawnarAlvo();
            }
        }, 0, INTERVALO_SPAWN_ALVO);

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

        // CORREÇÃO 3: Lógica de Negócio removida da View e movida para Timer dedicado (Rodando a ~60 FPS)
        physicsTimer = new Timer("PhysicsTimer", true);
        physicsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estado == Estado.RODANDO) verificarColisoes();
            }
        }, 0, 16); 

        sensorThread = new SensorThread(alvos, sensorLock);
        sensorThread.start();

        reconciliacaoThread = new ReconciliacaoThread(
                dataReconciliation, sensorThread, alvos, sensorLock);
        reconciliacaoThread.setListener(totalRec -> {
            reconciliacoesRealizadas = totalRec;
            energiaEsquerdo = Math.min(ENERGIA_MAXIMA, energiaEsquerdo + 10f);
            energiaDireito = Math.min(ENERGIA_MAXIMA, energiaDireito + 10f);
            notificarEnergia();
        });
        reconciliacaoThread.start();

        firebaseIOThread = new FirebaseIOThread(firestoreRepository, cryptography);
        firebaseIOThread.start();

        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita
            for (Canhao c : canhoes) {
                if (!c.isAlive()) c.start();
            }
        }

        notificarEstado();
        notificarTempo();
        notificarEnergia();
        notificarPontuacao();
    }

    /**
     * Interrompe imediatamente o jogo em andamento.
     */
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

        Lado vencedor;
        if (pontuacaoEsquerdo > pontuacaoDireito) {
            vencedor = Lado.ESQUERDO;
        } else if (pontuacaoDireito > pontuacaoEsquerdo) {
            vencedor = Lado.DIREITO;
        } else {
            vencedor = null;
        }

        if (firebaseIOThread != null && firebaseIOThread.isAlive()) {
            String dados = "esquerdo=" + pontuacaoEsquerdo
                    + ";direito=" + pontuacaoDireito
                    + ";tempo=" + tempoTotal;
            firebaseIOThread.salvarAsync(
                    pontuacaoEsquerdo + pontuacaoDireito, tempoTotal, dados);
        }

        notificarEstado();
        notificarTempo();

        if (listener != null) {
            listener.onPartidaEncerrada(pontuacaoEsquerdo, pontuacaoDireito,
                    (int) tempoTotal, reconciliacoesRealizadas, vencedor);
        }
    }

    private void atualizarEnergia() {
        int canhoesEsq = 0, canhoesDir = 0;
        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita na iteração
            for (Canhao c : canhoes) {
                if (!c.isAtivo()) continue;
                if (c.getLado() == Lado.ESQUERDO) canhoesEsq++;
                else canhoesDir++;
            }
        }

        energiaEsquerdo = Math.max(0f, energiaEsquerdo - canhoesEsq * CUSTO_ENERGIA_POR_CANHAO);
        energiaDireito = Math.max(0f, energiaDireito - canhoesDir * CUSTO_ENERGIA_POR_CANHAO);

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
        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita na iteração
            for (int i = canhoes.size() - 1; i >= 0; i--) {
                Canhao c = canhoes.get(i);
                if (c.isAtivo() && c.getLado() == lado) {
                    c.pararCanhao();
                    break;
                }
            }
        }
    }

    private void aplicarPenalidades() {
        int contEsq = 0, contDir = 0;
        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita na iteração
            for (Canhao c : canhoes) {
                if (!c.isAtivo()) continue;
                if (c.getLado() == Lado.ESQUERDO) contEsq++;
                else contDir++;
            }

            for (Canhao c : canhoes) {
                if (!c.isAtivo()) continue;
                if (c.getLado() == Lado.ESQUERDO) {
                    c.aplicarPenalidade(contEsq > LIMIAR_PENALIDADE);
                } else {
                    c.aplicarPenalidade(contDir > LIMIAR_PENALIDADE);
                }
            }
        }
    }

    /**
     * Adiciona um novo canhão na partida.
     *
     * @param x a coordenada X inicial do canhão
     * @param y a coordenada Y inicial do canhão
     * @param lado o lado do canhão (ESQUERDO ou DIREITO)
     * @throws JogoException se os limites de canhões forem excedidos ou não houver energia.
     */
    public void adicionarCanhao(float x, float y, Lado lado) throws JogoException {
        if (x < 0 || x > larguraTela || y < 0 || y > alturaTela) {
            throw new JogoException(
                    "Canhão fora dos limites da tela! Posição: (" + x + ", " + y + ")");
        }

        int countLado = 0;
        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita na iteração
            for (Canhao c : canhoes) {
                if (c.getLado() == lado) countLado++;
            }
        }

        if (countLado >= MAX_CANHOES_POR_LADO) {
            throw new JogoException(
                    "Máximo de canhões no lado " + lado + " atingido (" + MAX_CANHOES_POR_LADO + ")!");
        }

        float energiaLado = (lado == Lado.ESQUERDO) ? energiaEsquerdo : energiaDireito;
        if (estado == Estado.RODANDO && energiaLado < CUSTO_ENERGIA_POR_CANHAO) {
            throw new JogoException("Energia insuficiente no lado " + lado + "!");
        }

        Canhao canhao = new Canhao(x, y, lado, alvos, collisionLock,
                larguraTela, alturaTela);

        if (countLado >= LIMIAR_PENALIDADE) {
            canhao.aplicarPenalidade(true);
        }

        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita na adição
            canhoes.add(canhao);
        }

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
            alvo = new AlvoComum(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela, canhoes, collisionLock);
        } else {
            alvo = new AlvoRapido(x, y, RAIO_ALVO, VELOCIDADE_ALVO, larguraTela, alturaTela, canhoes, collisionLock);
        }

        synchronized (collisionLock) { // CORREÇÃO 1: Sincronização explícita na adição da lista de alvos
            alvos.add(alvo);
        }
        alvo.start();
    }

    /**
     * Verifica colisões ativas e atribui pontos ao lado correspondente.
     *
     * @return a quantidade de alvos destruídos na verificação.
     */
    public int verificarColisoes() {
        int destruidos = 0;

        // CORREÇÃO 2: Race Condition na Pontuação resolvida.
        // Toda a lógica (verificação, marcação de pontos e remoção da lista)
        // Ocorre de forma ATÔMICA dentro da região crítica.
        synchronized (collisionLock) {
            java.util.Iterator<Alvo> iterator = alvos.iterator();
            while (iterator.hasNext()) {
                Alvo alvo = iterator.next();
                if (!alvo.isAtivo()) {
                    destruidos++;
                    // Ponto para o lado onde o alvo estava
                    Lado ladoAlvo = Lado.determinar(alvo.getX(), larguraTela);
                    if (ladoAlvo == Lado.ESQUERDO) {
                        pontuacaoEsquerdo++;
                    } else {
                        pontuacaoDireito++;
                    }
                    iterator.remove(); // Remoção segura usando iterator nativo do ArrayList
                }
            }
        }

        if (destruidos > 0) {
            notificarPontuacao();
        }

        notificarAlvosAtivos();
        return destruidos;
    }

    private void pararTimers() {
        if (spawnTimer != null) { spawnTimer.cancel(); spawnTimer = null; }
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        if (physicsTimer != null) { physicsTimer.cancel(); physicsTimer = null; }
    }

    private void pararTodasThreads() {
        synchronized (collisionLock) { // CORREÇÃO 1: Sincronização explícita
            for (Alvo a : alvos) { a.setAtivo(false); a.interrupt(); }
        }
        synchronized (canhoes) { // CORREÇÃO 1: Sincronização explícita
            for (Canhao c : canhoes) { c.pararCanhao(); c.interrupt(); }
        }
        if (sensorThread != null) { sensorThread.setAtivo(false); sensorThread.interrupt(); }
        if (reconciliacaoThread != null) { reconciliacaoThread.setAtivo(false); reconciliacaoThread.interrupt(); }
        if (firebaseIOThread != null) {
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                firebaseIOThread.setAtivo(false);
                firebaseIOThread.interrupt();
            }, "FirebaseShutdown").start();
        }
    }

    private void notificarPontuacao() {
        if (listener != null) listener.onPontuacaoAtualizada(pontuacaoEsquerdo, pontuacaoDireito);
    }

    private void notificarEstado() {
        if (listener != null) listener.onEstadoAlterado(estado);
    }

    private void notificarAlvosAtivos() {
        if (listener != null) {
            int ativos = 0;
            synchronized (collisionLock) { // CORREÇÃO 1
                for (Alvo a : alvos) { if (a.isAtivo()) ativos++; }
            }
            listener.onAlvosAtivosAtualizado(ativos);
        }
    }

    private void notificarTempo() {
        if (listener != null) listener.onTempoAtualizado(tempoRestante);
    }

    private void notificarEnergia() {
        if (listener != null) listener.onEnergiaAtualizada(energiaEsquerdo, energiaDireito);
    }

    public Estado getEstado() { return estado; }
    public int getPontuacaoEsquerdo() { return pontuacaoEsquerdo; }
    public int getPontuacaoDireito() { return pontuacaoDireito; }
    public float getEnergiaEsquerdo() { return energiaEsquerdo; }
    public float getEnergiaDireito() { return energiaDireito; }
    public int getTempoRestante() { return tempoRestante; }
    public List<Alvo> getAlvos() { return alvos; }
    public List<Canhao> getCanhoes() { return canhoes; }
    public Object getCollisionLock() { return collisionLock; }

    /**
     * Define as dimensões da tela de renderização.
     *
     * @param largura largura da tela
     * @param altura altura da tela
     */
    public void setDimensoesTela(int largura, int altura) {
        this.larguraTela = largura;
        this.alturaTela = altura;
    }

    public int getLarguraTela() { return larguraTela; }
    public int getAlturaTela() { return alturaTela; }

    /**
     * Configura um listener para receber eventos do jogo.
     *
     * @param listener o listener para escutar
     */
    public void setListener(OnJogoListener listener) { this.listener = listener; }

    /**
     * Recupera a energia máxima.
     *
     * @return a energia máxima
     */
    public static float getEnergiaMaxima() { return ENERGIA_MAXIMA; }

    /**
     * Recupera o limiar de penalidade de canhões.
     *
     * @return o limiar de penalidade
     */
    public static int getLimiarPenalidade() { return LIMIAR_PENALIDADE; }
}
