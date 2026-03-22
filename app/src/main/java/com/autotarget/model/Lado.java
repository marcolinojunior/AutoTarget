package com.autotarget.model;

/**
 * Representa o lado (campo) do jogo.
 * <p>
 * A tela é dividida verticalmente em duas áreas: esquerda e direita.
 * Cada lado possui seu próprio conjunto de canhões e orçamento de energia.
 * Alvos pertencem ao lado em que estão posicionados e mudam de lado
 * ao cruzar a linha divisória central.
 */
public enum Lado {
    /** Campo esquerdo. */
    ESQUERDO,

    /** Campo direito. */
    DIREITO;

    /**
     * Determina o lado baseado na posição X e na largura da tela.
     *
     * @param x           posição X da entidade
     * @param larguraTela largura total do canvas
     * @return ESQUERDO se x < metade, DIREITO caso contrário
     */
    public static Lado determinar(float x, int larguraTela) {
        return (x < larguraTela / 2f) ? ESQUERDO : DIREITO;
    }
}
