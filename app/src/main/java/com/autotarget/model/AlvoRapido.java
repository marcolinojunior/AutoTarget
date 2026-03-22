package com.autotarget.model;

import java.util.List;

/**
 * Alvo rápido com movimento acelerado e mudanças de direção mais frequentes.
 * <p>
 * Aplica um multiplicador de 2x na velocidade e, a cada atualização,
 * possui uma chance de 5% de trocar aleatoriamente de direção,
 * tornando-o mais difícil de interceptar.
 */
// OOP: Herança demonstrando que AlvoRapido também é um tipo de Alvo,
// mas com comportamentos especializados de movimentação.
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
     * @param canhoes lista com referências dos canhões em jogo
     * @param collisionLock mecanismo de trava de concorrência global de colisões
     */
    public AlvoRapido(float x, float y, float raio, float velocidade,
                      int larguraTela, int alturaTela, List<Canhao> canhoes, Object collisionLock) {
        super(x, y, raio, velocidade, larguraTela, alturaTela, canhoes, collisionLock);
    }

    /**
     * Movimento rápido: aplica 2x na velocidade e muda de direção
     * aleatoriamente com 5% de chance a cada frame.
     */
    // OOP: Mais um exemplo de Polimorfismo. O método mover() aqui foi sobrescrito
    // para adicionar uma lógica totalmente diferente: um movimento caótico (mudança aleatória) e acelerado.
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
    // OOP: Sobrescrita de método concreto da classe mãe (Alvo). Polimorfismo também se aplica
    // ao recuperar propriedades quando a subclasse tem regras específicas de cálculo.
    @Override
    public float getVelocidade() {
        return super.getVelocidade() * MULTIPLICADOR_VELOCIDADE;
    }
}
