package com.autotarget.engine;

import com.autotarget.model.Lado;

/**
 * Helper encapsulating screen geometry calculations.
 * Extracted from Lado utility usages to centralize screen-dimension logic.
 */
public final class GameGeometry {

    private int largura;
    private int altura;

    public GameGeometry(int largura, int altura) {
        this.largura = largura;
        this.altura = altura;
    }

    public static GameGeometry forScreen(int largura, int altura) {
        return new GameGeometry(largura, altura);
    }

    public void setDimensions(int largura, int altura) {
        this.largura = largura;
        this.altura = altura;
    }

    public int getLargura() { return largura; }
    public int getAltura() { return altura; }

    public float getMidpointX() { return largura / 2f; }

    /**
     * Determines the side for an X coordinate using the current screen width.
     * If width is not positive, returns ESQUERDO as a safe default.
     */
    public Lado determineLado(float x) {
        if (largura <= 0) return Lado.ESQUERDO;
        return (x < largura / 2f) ? Lado.ESQUERDO : Lado.DIREITO;
    }
}
