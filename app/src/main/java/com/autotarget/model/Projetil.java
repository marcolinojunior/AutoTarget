package com.autotarget.model;

import java.util.List;

/**
 * Projétil disparado por um canhão.
 */
public class Projetil extends Thread {

    private float x;
    private float y;
    private float direcaoX;
    private float direcaoY;
    private float velocidade;
    private volatile boolean ativo;

    private static final float RAIO = 5f;
    private static final int INTERVALO_ATUALIZACAO = 16;

    private final List<Alvo> alvos;
    private final Object collisionLock;

    private final int larguraTela;
    private final int alturaTela;

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

    public void mover() {
        this.x += direcaoX * velocidade;
        this.y += direcaoY * velocidade;
    }

    private boolean foraDosTela() {
        return x < -RAIO || x > larguraTela + RAIO
            || y < -RAIO || y > alturaTela + RAIO;
    }

    /**
     * Verifica colisão com cada alvo ativo na lista compartilhada.
     */
    private void verificarColisoes() {
        // CORREÇÃO 4: Região crítica explícita usando o collisionLock.
        // Garante que a leitura da iteração e a exclusão lógicas (ativo=false) ocorram perfeitamente atômicas 
        // e sem lançar ConcurrentModificationException.
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

    public boolean collide(Alvo alvo) {
        float distancia = Alvo.calcularDistancia(this.x, this.y, alvo.getX(), alvo.getY());
        return distancia <= (RAIO + alvo.getRaio());
    }

    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        return Alvo.calcularDistancia(x1, y1, x2, y2);
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getVelocidade() { return velocidade; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public static float getRaio() { return RAIO; }
}
