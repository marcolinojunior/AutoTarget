package com.autotarget.model;

import java.util.List;

/**
 * Projétil disparado por um canhão.
 */
// Concorrência: Assim como os alvos e canhões, cada Projétil é uma Thread.
// Isso permite que centenas de projéteis se movam ao mesmo tempo de forma assíncrona.
public class Projetil extends Thread {

    private float x;
    private float y;
    private float direcaoX;
    private float direcaoY;
    private float velocidade;
    private volatile boolean ativo;

    private static final float RAIO = 5f;
    private static final int INTERVALO_ATUALIZACAO = 16;

    private final int larguraTela;
    private final int alturaTela;

    /**
     * Instancia um novo Projetil, que é disparado por um Canhão.
     *
     * @param x Posição inicial X
     * @param y Posição inicial Y
     * @param direcaoX Componente X da direção
     * @param direcaoY Componente Y da direção
     * @param velocidade Velocidade de movimento
     * @param larguraTela Limite da tela no eixo X
     * @param alturaTela Limite da tela no eixo Y
     */
    public Projetil(float x, float y, float direcaoX, float direcaoY,
                    float velocidade, int larguraTela, int alturaTela) {
        this.x = x;
        this.y = y;
        this.direcaoX = direcaoX;
        this.direcaoY = direcaoY;
        this.velocidade = velocidade;
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
                // REGRA 4: Projétil apenas anda em linha reta (a colisão agora é responsabilidade do Alvo)
                // Concorrência: Pausa a Thread do projétil por INTERVALO_ATUALIZACAO milissegundos (~60 vezes por segundo).
                // Evita que o projétil "teleporte" e libera a CPU para outras Threads (como a UI e os outros projéteis).
                Thread.sleep(INTERVALO_ATUALIZACAO);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }

    /**
     * Aplica o movimento do projétil baseado na direção e velocidade atuais.
     */
    public void mover() {
        this.x += direcaoX * velocidade;
        this.y += direcaoY * velocidade;
    }

    private boolean foraDosTela() {
        return x < -RAIO || x > larguraTela + RAIO
            || y < -RAIO || y > alturaTela + RAIO;
    }

    /**
     * Verifica colisão física de contato entre o projétil atual e um determinado alvo.
     *
     * @param alvo Instância de Alvo para testar a colisão
     * @return true se ocorrer intersecção de raios, false caso contrário
     */
    public boolean collide(Alvo alvo) {
        float distancia = Alvo.calcularDistancia(this.x, this.y, alvo.getX(), alvo.getY());
        return distancia <= (RAIO + alvo.getRaio());
    }

    /**
     * Calcula a distância euclidiana entre dois pontos.
     *
     * @param x1 coordenada X do ponto 1
     * @param y1 coordenada Y do ponto 1
     * @param x2 coordenada X do ponto 2
     * @param y2 coordenada Y do ponto 2
     * @return a distância entre os pontos
     */
    public static float calcularDistancia(float x1, float y1, float x2, float y2) {
        return Alvo.calcularDistancia(x1, y1, x2, y2);
    }

    /**
     * Recupera a posição X.
     *
     * @return a posição X
     */
    public float getX() { return x; }

    /**
     * Recupera a posição Y.
     *
     * @return a posição Y
     */
    public float getY() { return y; }

    /**
     * Recupera a velocidade atual.
     *
     * @return a velocidade
     */
    public float getVelocidade() { return velocidade; }

    /**
     * Verifica se o projétil está ativo.
     *
     * @return true se ativo, false caso inativo ou fora da tela
     */
    public boolean isAtivo() { return ativo; }

    /**
     * Define o estado ativo do projétil.
     *
     * @param ativo novo estado de ativação
     */
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    /**
     * Recupera a constante fixa de raio do projétil.
     *
     * @return raio constante
     */
    public static float getRaio() { return RAIO; }
}
