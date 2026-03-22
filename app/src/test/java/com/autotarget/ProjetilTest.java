package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Projetil;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * Testes unitários para a classe {@link Projetil}.
 * <p>
 * Verifica cálculo de distância, detecção de colisão, movimento
 * e validação de posição.
 */
public class ProjetilTest {

    private CopyOnWriteArrayList<Alvo> alvos;
    private Object collisionLock;
    private static final int LARGURA = 800;
    private static final int ALTURA = 600;

    @Before
    public void setUp() {
        alvos = new CopyOnWriteArrayList<>();
        collisionLock = new Object();
    }

    // ── Testes de cálculo de distância ───────────────────────────

    @Test
    public void testCalcularDistancia() {
        // Triângulo pitagórico 3-4-5
        float dist = Projetil.calcularDistancia(0, 0, 3, 4);
        assertEquals(5.0f, dist, 0.001f);
    }

    @Test
    public void testDistanciaZero() {
        float dist = Projetil.calcularDistancia(5, 5, 5, 5);
        assertEquals(0f, dist, 0.001f);
    }

    // ── Testes de colisão ────────────────────────────────────────

    @Test
    public void testColisaoDetectada() {
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil na mesma posição do alvo → colisão
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);

        assertTrue("Deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaoDetectadaDistante() {
        AlvoComum alvo = new AlvoComum(500, 500, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil muito longe → sem colisão
        Projetil projetil = new Projetil(10, 10, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);

        assertFalse("Não deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaFronteiraDaDistancia() {
        // Alvo com raio 20 na posição (100, 100)
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil (raio 5) exatamente no limite: distância = raioAlvo + raioProjetil = 25
        float distanciaLimite = 20f + Projetil.getRaio(); // 25
        Projetil projetilLimite = new Projetil(100 + distanciaLimite, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);

        // Colisão exata na fronteira
        assertTrue("Deveria detectar colisão no limite",
                projetilLimite.collide(alvo));
    }

    // ── Testes de movimento ──────────────────────────────────────

    @Test
    public void testMovimento() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);

        float xInicial = projetil.getX();
        projetil.mover();

        // Deveria ter avançado 10 pixels na direção X
        assertEquals(xInicial + 10f, projetil.getX(), 0.001f);
    }

    @Test
    public void testMovimentoDiagonal() {
        // Direção normalizada 45°
        float dir = (float) (1 / Math.sqrt(2));
        Projetil projetil = new Projetil(0, 0, dir, dir, 10,
                alvos, collisionLock, LARGURA, ALTURA);

        projetil.mover();

        float esperado = dir * 10;
        assertEquals(esperado, projetil.getX(), 0.001f);
        assertEquals(esperado, projetil.getY(), 0.001f);
    }

    // ── Testes de estado ─────────────────────────────────────────

    @Test
    public void testProjetilCriadoAtivo() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);
        assertTrue(projetil.isAtivo());
    }

    @Test
    public void testProjetilEhThread() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA);
        assertTrue("Projetil deve ser uma Thread", projetil instanceof Thread);
    }
}
