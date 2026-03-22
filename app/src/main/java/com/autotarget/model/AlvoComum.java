package com.autotarget.model;

import java.util.List;

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
     * @param canhoes lista com referências dos canhões em jogo
     * @param collisionLock mecanismo de trava de concorrência global de colisões
     */
    public AlvoComum(float x, float y, float raio, float velocidade,
                     int larguraTela, int alturaTela, List<Canhao> canhoes, Object collisionLock) {
        super(x, y, raio, velocidade, larguraTela, alturaTela, canhoes, collisionLock);
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
