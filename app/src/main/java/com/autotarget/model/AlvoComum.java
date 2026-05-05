/*
 * ============================================================================
 * Arquivo: AlvoComum.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Subclasse concreta de Alvo que implementa o padrão de movimento linear
 *   (padrão/"comum"). Desloca-se em linha reta com a velocidade base,
 *   mudando de direção somente ao colidir com as bordas da tela (bouncing
 *   tratado em verificarLimites() da classe pai).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - AlvoComum extends Alvo — subclasse concreta na hierarquia de herança.
 *     - Sobrescreve o método abstrato mover() com movimento linear simples:
 *       x += direcaoX * velocidade; y += direcaoY * velocidade.
 *     - Quando o Jogo itera List<Alvo> e chama mover(), este método é
 *       invocado polimorficamente para instâncias de AlvoComum.
 *     - GameSurfaceView renderiza AlvoComum com cor VERDE (#4CAF50).
 *
 *   ► Classes/threads (6.1.4):
 *     - Herda o comportamento de thread de Alvo (extends Thread).
 *     - O loop run() da classe pai chama mover() a cada 30ms.
 *
 * COMPORTAMENTO DE MOVIMENTO:
 *   - Direção constante (definida aleatoriamente no construtor de Alvo).
 *   - Velocidade sem multiplicador (velocidade base pura).
 *   - Muda de direção apenas por reflexão nas bordas (bouncing).
 *
 * ============================================================================
 */
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

    /**
     * Cor do AlvoComum: verde (#4CAF50).
     */
    @Override
    public int getCorId() {
        return 0xFF4CAF50;
    }
}
