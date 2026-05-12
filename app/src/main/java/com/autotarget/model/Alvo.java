/*
 * ============================================================================
 * Arquivo: Alvo.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe abstrata que representa um alvo (target) no jogo AutoTarget.
 *   Cada instância de Alvo é uma Thread independente — ao ser iniciada
 *   (start()), executa seu loop em run(): invoca mover() → verificarLimites()
 *   → sleep(30ms) continuamente até ser desativada. Subclasses concretas
 *   (AlvoComum, AlvoRapido) implementam mover() com padrões de movimento
 *   distintos, demonstrando polimorfismo.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Classes/threads (6.1.4):
 *     - Alvo extends Thread — cada alvo opera em thread independente.
 *     - Atributos: posição (x, y), raio, velocidade, direcaoX/Y, ativo.
 *     - Método run() → loop while(ativo) { mover(); verificarLimites(); sleep(); }
 *     - Atributo 'ativo' é volatile para visibilidade entre threads.
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - Classe abstrata base da hierarquia: Alvo → AlvoComum, AlvoRapido.
 *     - Método abstrato mover() — sobrescrito pelas subclasses (polimorfismo).
 *     - O Jogo itera a lista como List<Alvo> e chama mover() polimorficamente.
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - InterruptedException capturada no run() — seta interrupt flag e desativa.
 *
 *   ► Testes unitários (6.1.7):
 *     - Método estático calcularDistancia() é testado em AlvoTest e ProjetilTest
 *       (distância euclidiana entre dois pontos — base para detecção de colisão).
 *
 * ATRIBUTOS PRINCIPAIS:
 *   - x, y: posição atual no canvas (float)
 *   - raio: raio do círculo visual (float)
 *   - velocidade: velocidade base de deslocamento em px/atualização (float)
 *   - direcaoX, direcaoY: vetor de direção normalizado (float)
 *   - ativo: flag volatile de controle do loop da thread (boolean)
 *   - larguraTela, alturaTela: limites do canvas para bouncing (int)
 *
 * ESCALONAMENTO RMA (Rate Monotonic Analysis):
 *   Tarefa: T3 — Alvo.run (Trajetórias)
 *   Período P₃ = 30ms, Execução C₃ = 1-3ms, Deadline D₃ = 30ms
 *   Prioridade RM: 2 (Alta)
 *
 * ============================================================================
 */
package com.autotarget.model;

import java.util.Random;

/**
 * Classe abstrata que representa um alvo no jogo AutoTarget.
 * <p>
 * Cada alvo opera em sua própria thread, movendo-se continuamente
 * pela tela. Subclasses devem implementar {@link #mover()} para
 * definir o padrão de movimento específico.
 * <p>
 * Herança: Alvo → AlvoComum, AlvoRapido (polimorfismo).
 */
public abstract class Alvo extends Thread {

    // ── Atributos ────────────────────────────────────────────────
    protected float x;
    protected float y;
    protected float raio;
    protected float velocidade;
    protected float direcaoX;
    protected float direcaoY;
    protected volatile boolean ativo;

    /** Limites da tela (definidos externamente pelo Jogo). */
    protected int larguraTela;
    protected int alturaTela;

    protected final Random random = new Random();

    /** Timestamp de criação do alvo (ms). Usado para penalidade temporal. */
    private final long timestampNascimento;

    /** Intervalo entre atualizações de posição (ms). */
    protected static final int INTERVALO_ATUALIZACAO = 30;

    // ── Construtor ───────────────────────────────────────────────

    /**
     * Cria um novo alvo.
     *
     * @param x          posição X inicial
     * @param y          posição Y inicial
     * @param raio       raio do círculo que representa o alvo
     * @param velocidade velocidade base de deslocamento (px/atualização)
     * @param larguraTela largura máxima do canvas
     * @param alturaTela  altura máxima do canvas
     */
    public Alvo(float x, float y, float raio, float velocidade,
                int larguraTela, int alturaTela) {
        this.x = x;
        this.y = y;
        this.raio = raio;
        this.velocidade = velocidade;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.ativo = true;
        this.timestampNascimento = System.currentTimeMillis();

        // Direção inicial aleatória normalizada
        double angulo = random.nextDouble() * 2 * Math.PI;
        this.direcaoX = (float) Math.cos(angulo);
        this.direcaoY = (float) Math.sin(angulo);
    }

    // ── Thread ───────────────────────────────────────────────────

    @Override
    public void run() {
        while (ativo) {
            try {
                mover();
                verificarLimites();
                Thread.sleep(INTERVALO_ATUALIZACAO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Move o alvo de acordo com seu padrão de movimento.
     * Implementado pelas subclasses (polimorfismo).
     */
    public abstract void mover();

    /**
     * Retorna o identificador de cor (ARGB int) para renderização polimórfica.
     * Cada subclasse define sua própria cor, eliminando a necessidade de
     * instanceof na GameSurfaceView — design extensível para novos tipos de alvo.
     *
     * @return cor no formato 0xAARRGGBB
     */
    public abstract int getCorId();

    // ── Lógica de limites ────────────────────────────────────────

    /**
     * Verifica se o alvo ultrapassou os limites da tela e,
     * em caso positivo, inverte a direção correspondente.
     */
    protected void verificarLimites() {
        if (x - raio < 0) {
            x = raio;
            direcaoX = Math.abs(direcaoX);
        } else if (x + raio > larguraTela) {
            x = larguraTela - raio;
            direcaoX = -Math.abs(direcaoX);
        }

        if (y - raio < 0) {
            y = raio;
            direcaoY = Math.abs(direcaoY);
        } else if (y + raio > alturaTela) {
            y = alturaTela - raio;
            direcaoY = -Math.abs(direcaoY);
        }
    }

    // ── Utilidades ───────────────────────────────────────────────

    /**
     * Calcula a distância euclidiana entre dois pontos.
     *
     * @return distância em pixels
     */
    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ── Getters / Setters ────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getRaio() { return raio; }
    public float getVelocidade() { return velocidade; }
    public boolean isAtivo() { return ativo; }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public void setLarguraTela(int larguraTela) { this.larguraTela = larguraTela; }
    public void setAlturaTela(int alturaTela) { this.alturaTela = alturaTela; }

    /** Retorna a idade do alvo em milissegundos (desde a criação). */
    public long getIdadeMs() {
        return System.currentTimeMillis() - timestampNascimento;
    }

    /** Retorna o timestamp de nascimento. */
    public long getTimestampNascimento() { return timestampNascimento; }
}
