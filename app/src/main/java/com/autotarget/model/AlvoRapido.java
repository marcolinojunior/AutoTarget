/*
 * ============================================================================
 * Arquivo: AlvoRapido.java
 * Pacote:  com.autotarget.model
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Subclasse concreta de Alvo que implementa o padrão de movimento rápido
 *   e imprevisível. Aplica um multiplicador de 2x na velocidade base e,
 *   a cada frame, possui 5% de chance de mudar aleatoriamente de direção,
 *   tornando-o significativamente mais difícil de interceptar que AlvoComum.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - AlvoRapido extends Alvo — segunda subclasse concreta na hierarquia.
 *     - Sobrescreve mover() com lógica distinta de AlvoComum:
 *       chance de mudança aleatória de direção + velocidade multiplicada.
 *     - Sobrescreve getVelocidade() para retornar a velocidade efetiva (2x).
 *     - Demonstra polimorfismo: o Jogo e GameSurfaceView tratam AlvoRapido
 *       como Alvo, mas o comportamento executado é específico desta subclasse.
 *     - GameSurfaceView renderiza AlvoRapido com cor LARANJA (#FF9800),
 *       diferenciando-o visualmente do AlvoComum (verde).
 *
 *   ► Classes/threads (6.1.4):
 *     - Herda o comportamento de thread de Alvo (extends Thread).
 *     - O loop run() da classe pai chama mover() a cada 30ms.
 *
 * COMPORTAMENTO DE MOVIMENTO:
 *   - MULTIPLICADOR_VELOCIDADE = 2.0f → velocidade efetiva = velocidadeBase * 2
 *   - CHANCE_MUDAR_DIRECAO = 0.05f (5%) → a cada frame pode gerar novo ângulo
 *   - Resulta em trajetória errática e acelerada (mais desafiador)
 *
 * ============================================================================
 */
package com.autotarget.model;

/**
 * Alvo rápido com movimento acelerado e mudanças de direção mais frequentes.
 * <p>
 * Aplica um multiplicador de 2x na velocidade e, a cada atualização,
 * possui uma chance de 5% de trocar aleatoriamente de direção,
 * tornando-o mais difícil de interceptar.
 */
public class AlvoRapido extends Alvo {

    /** Multiplicador de velocidade em relação à velocidade base. */
    private static final float MULTIPLICADOR_VELOCIDADE = 2.0f;

    /** Probabilidade de mudança aleatória de direção (0–1). */
    private static final float CHANCE_MUDAR_DIRECAO = 0.05f;

    /**
     * Cria um AlvoRapido.
     *
     * @param x           posição X inicial
     * @param y           posição Y inicial
     * @param raio        raio do círculo
     * @param velocidade  velocidade base (será multiplicada internamente)
     * @param larguraTela largura do canvas
     * @param alturaTela  altura do canvas
     */
    public AlvoRapido(float x, float y, float raio, float velocidade,
                      int larguraTela, int alturaTela) {
        super(x, y, raio, velocidade, larguraTela, alturaTela);
    }

    /**
     * Movimento rápido: aplica 2x na velocidade e muda de direção
     * aleatoriamente com 5% de chance a cada frame.
     */
    @Override
    public void mover() {
        // Mudança aleatória de direção
        if (random.nextFloat() < CHANCE_MUDAR_DIRECAO) {
            double angulo = random.nextDouble() * 2 * Math.PI;
            direcaoX = (float) Math.cos(angulo);
            direcaoY = (float) Math.sin(angulo);
        }

        this.x += direcaoX * velocidade * MULTIPLICADOR_VELOCIDADE;
        this.y += direcaoY * velocidade * MULTIPLICADOR_VELOCIDADE;
    }

    /**
     * Retorna a velocidade efetiva (com o multiplicador aplicado).
     */
    @Override
    public float getVelocidade() {
        return super.getVelocidade() * MULTIPLICADOR_VELOCIDADE;
    }

    /**
     * Cor do AlvoRapido: laranja (#FF9800).
     */
    @Override
    public int getCorId() {
        return 0xFFFF9800;
    }
}
