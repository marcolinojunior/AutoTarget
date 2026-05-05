/*
 * ============================================================================
 * Arquivo: Lado.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Enum que modela os dois campos (lados) do jogo competitivo AutoTarget.
 *   A tela é dividida verticalmente ao meio: ESQUERDO (x < metade) e
 *   DIREITO (x >= metade). O método estático determinar(x, larguraTela)
 *   calcula automaticamente a qual lado uma posição X pertence.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Cenário competitivo (seção 4.2):
 *     - Dois sistemas independentes (esquerda e direita) disputam o controle.
 *     - O Lado é usado por: Canhao (pertence a um lado), Jogo (pontuação por
 *       lado, energia por lado, penalidade por lado), Projetil (colisão atribui
 *       ponto ao lado do alvo), GameSurfaceView (cores distintas por lado).
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - Enum com método utilitário estático determinar() — encapsula a lógica
 *       de determinação de lado, evitando duplicação no código.
 *
 *   ► Testes unitários (6.1.7):
 *     - Lado.determinar() é testado em CanhaoTest.testDeterminacaoLado()
 *       validando os limites esquerdo/direito.
 *
 * VALORES:
 *   - ESQUERDO: campo esquerdo (x < larguraTela / 2)
 *   - DIREITO: campo direito (x >= larguraTela / 2)
 *
 * ============================================================================
 */
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
