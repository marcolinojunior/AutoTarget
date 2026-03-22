package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testes unitários para a hierarquia de classes {@link Alvo}.
 * <p>
 * Verifica polimorfismo (AlvoComum vs AlvoRapido), cálculo de distância,
 * validação de limites e movimento.
 */
public class AlvoTest {

    private static final int LARGURA = 800;
    private static final int ALTURA = 600;

    // ── Testes de criação e polimorfismo ──────────────────────────

    @Test
    public void testCriacaoAlvoComum() {
        AlvoComum alvo = new AlvoComum(100, 200, 30, 5, LARGURA, ALTURA);

        assertEquals(100f, alvo.getX(), 0.001f);
        assertEquals(200f, alvo.getY(), 0.001f);
        assertEquals(30f, alvo.getRaio(), 0.001f);
        assertEquals(5f, alvo.getVelocidade(), 0.001f);
        assertTrue(alvo.isAtivo());
    }

    @Test
    public void testAlvoRapidoTemVelocidadeSuperior() {
        float velocidadeBase = 5f;
        AlvoComum comum = new AlvoComum(100, 100, 20, velocidadeBase, LARGURA, ALTURA);
        AlvoRapido rapido = new AlvoRapido(100, 100, 20, velocidadeBase, LARGURA, ALTURA);

        // AlvoRapido deve ter velocidade efetiva 2x maior
        assertTrue("AlvoRapido deve ser mais rápido que AlvoComum",
                rapido.getVelocidade() > comum.getVelocidade());
        assertEquals(velocidadeBase * 2, rapido.getVelocidade(), 0.001f);
    }

    @Test
    public void testPolimorfismoMover() {
        // Criar ambos os tipos como referência Alvo (polimorfismo)
        Alvo comum = new AlvoComum(400, 300, 20, 5, LARGURA, ALTURA);
        Alvo rapido = new AlvoRapido(400, 300, 20, 5, LARGURA, ALTURA);

        float xInicialComum = comum.getX();
        float xInicialRapido = rapido.getX();

        // Executar mover() várias vezes
        for (int i = 0; i < 10; i++) {
            comum.mover();
            rapido.mover();
        }

        // Ambos devem ter se movido (posição diferente da inicial)
        assertTrue("AlvoComum deveria ter se movido",
                Math.abs(comum.getX() - xInicialComum) > 0
                        || Math.abs(comum.getY() - 300) > 0);
        assertTrue("AlvoRapido deveria ter se movido",
                Math.abs(rapido.getX() - xInicialRapido) > 0
                        || Math.abs(rapido.getY() - 300) > 0);
    }

    // ── Testes de cálculo de distância ───────────────────────────

    @Test
    public void testCalcularDistanciaHorizontal() {
        float dist = Alvo.calcularDistancia(0, 0, 3, 0);
        assertEquals(3f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaVertical() {
        float dist = Alvo.calcularDistancia(0, 0, 0, 4);
        assertEquals(4f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaDiagonal() {
        // Triângulo 3-4-5
        float dist = Alvo.calcularDistancia(0, 0, 3, 4);
        assertEquals(5f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaMesmoPonto() {
        float dist = Alvo.calcularDistancia(10, 20, 10, 20);
        assertEquals(0f, dist, 0.001f);
    }

    // ── Testes de estado ─────────────────────────────────────────

    @Test
    public void testDesativarAlvo() {
        AlvoComum alvo = new AlvoComum(100, 100, 20, 5, LARGURA, ALTURA);
        assertTrue(alvo.isAtivo());

        alvo.setAtivo(false);
        assertFalse(alvo.isAtivo());
    }

    @Test
    public void testAlvoEhThread() {
        AlvoComum alvo = new AlvoComum(100, 100, 20, 5, LARGURA, ALTURA);
        assertTrue("Alvo deve ser uma Thread", alvo instanceof Thread);
    }
}
