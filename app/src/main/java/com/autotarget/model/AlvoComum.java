package com.autotarget.model;

/**
 * Alvo comum com movimento linear padrão.
 * <p>
 * Desloca-se em linha reta, mudando de direção ao colidir com
 * as bordas da tela. Velocidade base sem multiplicador.
 */
public class AlvoComum extends Alvo {

    /**
     * Cria um AlvoComum.
     *
     * @param x           posição X inicial
     * @param y           posição Y inicial
     * @param raio        raio do circle
     * @param velocidade  velocidade base (px/atualização)
     * @param larguraTela largura do canvas
     * @param alturaTela  altura do canvas
     */
    public AlvoComum(float x, float y, float raio, float velocidade,
                     int larguraTela, int alturaTela) {
        super(x, y, raio, velocidade, larguraTela, alturaTela);
    }

    /**
     * Movimento linear simples: desloca x e y pela direção * velocidade.
     */
    @Override
    public void mover() {
        this.x += direcaoX * velocidade;
        this.y += direcaoY * velocidade;
    }
}
