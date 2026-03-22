package com.autotarget.model;

import java.util.Random;
import java.util.List; // ADDED LIST IMPORT

/**
 * Classe abstrata que representa um alvo no jogo AutoTarget.
 * <p>
 * Cada alvo opera em sua própria thread, movendo-se continuamente
 * pela tela. Subclasses devem implementar {@link #mover()} para
 * definir o padrão de movimento específico.
 * <p>
 * Herança: Alvo → AlvoComum, AlvoRapido (polimorfismo).
 */
// OOP: Esta classe abstrata serve como base (Herança) para os diferentes tipos de alvos.
// Concorrência: Estende Thread para que cada alvo tenha seu próprio fluxo de execução independente, permitindo movimento paralelo.
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
     * @param canhoes lista com referências dos canhões em jogo
     * @param collisionLock mecanismo de trava de concorrência global de colisões
     */
    protected final List<Canhao> canhoes;
    protected final Object collisionLock;

    public Alvo(float x, float y, float raio, float velocidade,
                int larguraTela, int alturaTela, List<Canhao> canhoes, Object collisionLock) {
        this.x = x;
        this.y = y;
        this.raio = raio;
        this.velocidade = velocidade;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.canhoes = canhoes;
        this.collisionLock = collisionLock;
        this.ativo = true;

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
                verificarColisoes(); // REGRA 4: Alvo verifica colisão
                // Concorrência: Pausa a thread atual por um curto período (INTERVALO_ATUALIZACAO).
                // Isso dita a taxa de atualização da física do alvo e evita consumo excessivo de CPU.
                Thread.sleep(INTERVALO_ATUALIZACAO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    private void verificarColisoes() {
        // REGRA 4: O método run() do Alvo itera sobre a lista de projéteis e verifica colisão
        // Concorrência: Aqui criamos uma Região Crítica (usando collisionLock) para evitar Race Conditions.
        // Isso garante que a checagem e a eventual marcação de destruição sejam seguras contra outras threads.
        synchronized (collisionLock) {
            if (!this.ativo) return;

            for (Canhao canhao : canhoes) {
                synchronized (canhao.getProjeteis()) {
                    java.util.Iterator<Projetil> it = canhao.getProjeteis().iterator();
                    while (it.hasNext()) {
                        Projetil p = it.next();
                        if (p.isAtivo() && p.collide(this)) {
                            this.ativo = false;
                            p.setAtivo(false);
                            it.remove(); // Remove o projétil da lista do canhão
                            return; // Alvo destruído, encerra verificação
                        }
                    }
                }
            }
        }
    }

    /**
     * Move o alvo de acordo com seu padrão de movimento.
     * Implementado pelas subclasses (polimorfismo).
     */
    // OOP: Método abstrato que força as subclasses a implementarem suas próprias lógicas de movimento.
    // Isso é a base do Polimorfismo neste projeto, permitindo tratar AlvoComum e AlvoRapido de forma genérica.
    public abstract void mover();

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
     * @param x1 coordenada X do primeiro ponto
     * @param y1 coordenada Y do primeiro ponto
     * @param x2 coordenada X do segundo ponto
     * @param y2 coordenada Y do segundo ponto
     * @return distância euclidiana em pixels
     */
    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ── Getters / Setters ────────────────────────────────────────

    /**
     * @return Coordenada X
     */
    public float getX() { return x; }

    /**
     * @return Coordenada Y
     */
    public float getY() { return y; }

    /**
     * @return Raio deste alvo
     */
    public float getRaio() { return raio; }

    /**
     * @return Velocidade de deslocamento
     */
    public float getVelocidade() { return velocidade; }

    /**
     * @return Flag booleana que indica se o alvo está ativo
     */
    public boolean isAtivo() { return ativo; }

    /**
     * @param ativo Modificador do estado de ativação
     */
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    /**
     * @param larguraTela Novo limite da tela para o eixo X
     */
    public void setLarguraTela(int larguraTela) { this.larguraTela = larguraTela; }

    /**
     * @param alturaTela Novo limite da tela para o eixo Y
     */
    public void setAlturaTela(int alturaTela) { this.alturaTela = alturaTela; }
}
