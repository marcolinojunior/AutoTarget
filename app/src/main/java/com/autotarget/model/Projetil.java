package com.autotarget.model;

import java.util.List;

/**
 * Projétil disparado por um canhão.
 * <p>
 * Cada projétil opera em sua própria thread, movendo-se em linha reta
 * na direção definida no momento do disparo. Verifica colisão contra
 * a lista compartilhada de alvos usando sincronização explícita.
 */
public class Projetil extends Thread {

    // ── Atributos ────────────────────────────────────────────────
    private float x;
    private float y;
    private float direcaoX;
    private float direcaoY;
    private float velocidade;
    private volatile boolean ativo;

    /** Raio visual do projétil para detecção de colisão. */
    private static final float RAIO = 5f;

    /** Intervalo entre atualizações de posição (ms). */
    private static final int INTERVALO_ATUALIZACAO = 16;

    /** Referência à lista compartilhada de alvos (região crítica). */
    private final List<Alvo> alvos;

    /** Lock para região crítica de colisão. */
    private final Object collisionLock;

    /** Limites da tela. */
    private final int larguraTela;
    private final int alturaTela;

    // ── Construtor ───────────────────────────────────────────────

    /**
     * Cria um projétil.
     *
     * @param x             posição X de origem (posição do canhão)
     * @param y             posição Y de origem
     * @param direcaoX      componente X da direção normalizada
     * @param direcaoY      componente Y da direção normalizada
     * @param velocidade    velocidade do projétil (px/atualização)
     * @param alvos         lista compartilhada de alvos
     * @param collisionLock objeto de lock para região crítica
     * @param larguraTela   largura do canvas
     * @param alturaTela    altura do canvas
     */
    public Projetil(float x, float y, float direcaoX, float direcaoY,
                    float velocidade, List<Alvo> alvos, Object collisionLock,
                    int larguraTela, int alturaTela) {
        this.x = x;
        this.y = y;
        this.direcaoX = direcaoX;
        this.direcaoY = direcaoY;
        this.velocidade = velocidade;
        this.alvos = alvos;
        this.collisionLock = collisionLock;
        this.larguraTela = larguraTela;
        this.alturaTela = alturaTela;
        this.ativo = true;
    }

    // ── Thread ───────────────────────────────────────────────────

    @Override
    public void run() {
        while (ativo) {
            try {
                mover();
                if (foraDosTela()) {
                    ativo = false;
                    break;
                }
                verificarColisoes();
                Thread.sleep(INTERVALO_ATUALIZACAO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    // ── Movimento ────────────────────────────────────────────────

    /**
     * Desloca o projétil na direção definida.
     */
    public void mover() {
        this.x += direcaoX * velocidade;
        this.y += direcaoY * velocidade;
    }

    /**
     * Verifica se o projétil saiu dos limites da tela.
     */
    private boolean foraDosTela() {
        return x < -RAIO || x > larguraTela + RAIO
            || y < -RAIO || y > alturaTela + RAIO;
    }

    // ── Colisão ──────────────────────────────────────────────────

    /**
     * Verifica colisão com cada alvo ativo na lista compartilhada.
     * <p>
     * Região crítica: apenas um projétil pode verificar colisão
     * com um alvo por vez, evitando condições de corrida.
     */
    private void verificarColisoes() {
        synchronized (collisionLock) {
            for (Alvo alvo : alvos) {
                if (alvo.isAtivo() && collide(alvo)) {
                    alvo.setAtivo(false);
                    this.ativo = false;
                    break;
                }
            }
        }
    }

    /**
     * Verifica se este projétil colidiu com o dado alvo
     * usando a distância euclidiana.
     *
     * @param alvo o alvo a verificar
     * @return true se houve colisão
     */
    public boolean collide(Alvo alvo) {
        float distancia = Alvo.calcularDistancia(this.x, this.y, alvo.getX(), alvo.getY());
        return distancia <= (RAIO + alvo.getRaio());
    }

    /**
     * Calcula distância euclidiana — método público estático para testes.
     */
    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        return Alvo.calcularDistancia(x1, y1, x2, y2);
    }

    // ── Getters ──────────────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getVelocidade() { return velocidade; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public static float getRaio() { return RAIO; }
}
