package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Canhao;
import com.autotarget.model.Projetil;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Testes unitários para a classe {@link Projetil}.
 * <p>
 * Verifica cálculo de distância, detecção de colisão (cenários normais e
 * casos de borda), movimento linear e diagonal, e validação de estado.
 */
public class ProjetilTest {

    private List<Alvo> alvos;
    private List<Canhao> canhoes;
    private Object collisionLock;
    private static final int LARGURA = 800;
    private static final int ALTURA = 600;

    @Before
    public void setUp() {
        alvos = new ArrayList<>();
        canhoes = new ArrayList<>();
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
        // Como o teste é feito: Projétil e Alvo na mesma posição (sobreposição total).
        // Condição de Pass: collide() retorna true (distância 0 <= soma dos raios).
        // Condição de Not Pass: collide() retorna false incorretamente.
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA, canhoes, collisionLock);
        alvos.add(alvo);

        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                LARGURA, ALTURA);

        assertTrue("Deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaoDetectadaDistante() {
        // Como o teste é feito: Projétil muito longe do alvo.
        // Condição de Pass: collide() retorna false (distância >> soma dos raios).
        // Condição de Not Pass: collide() retorna true incorretamente.
        AlvoComum alvo = new AlvoComum(500, 500, 20, 3, LARGURA, ALTURA, canhoes, collisionLock);
        alvos.add(alvo);

        Projetil projetil = new Projetil(10, 10, 1, 0, 10,
                LARGURA, ALTURA);

        assertFalse("Não deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaFronteiraDaDistancia() {
        // Como o teste é feito: Projétil (raio=5) posicionado exatamente na distância = raioAlvo + raioProjetil = 25.
        // Este é o CASO DE BORDA onde a distância é estritamente igual à soma dos raios.
        // Condição de Pass: collide() retorna true (distância <= soma dos raios, com igualdade).
        // Condição de Not Pass: collide() retorna false, indicando que a condição usa < em vez de <=.
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA, canhoes, collisionLock);
        alvos.add(alvo);

        float distanciaLimite = 20f + Projetil.getRaio(); // 25
        Projetil projetilLimite = new Projetil(100 + distanciaLimite, 100, 1, 0, 10,
                LARGURA, ALTURA);

        assertTrue("Deveria detectar colisão no limite exato (borda tocando borda)",
                projetilLimite.collide(alvo));
    }

    @Test
    public void testColisaoNaFronteiraDaDistanciaEixoY() {
        // Como o teste é feito: Mesmo caso de borda, mas no eixo Y em vez de X.
        // Condição de Pass: collide() retorna true para toque exato na borda vertical.
        // Condição de Not Pass: Falha se a detecção depender do eixo.
        AlvoComum alvo = new AlvoComum(100, 100, 30, 3, LARGURA, ALTURA, canhoes, collisionLock);

        float somaRaios = 30f + Projetil.getRaio(); // 35
        Projetil projetil = new Projetil(100, 100 + somaRaios, 1, 0, 10, LARGURA, ALTURA);
        assertTrue("Colisão na borda inferior deve ser detectada", projetil.collide(alvo));
    }

    // ── Testes de movimento ──────────────────────────────────────

    @Test
    public void testMovimento() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                LARGURA, ALTURA);

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
                LARGURA, ALTURA);

        projetil.mover();

        float esperado = dir * 10;
        assertEquals(esperado, projetil.getX(), 0.001f);
        assertEquals(esperado, projetil.getY(), 0.001f);
    }

    // ── Testes de estado ─────────────────────────────────────────

    @Test
    public void testProjetilCriadoAtivo() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                LARGURA, ALTURA);
        assertTrue(projetil.isAtivo());
    }

    @Test
    public void testProjetilEhThread() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                LARGURA, ALTURA);
        assertTrue("Projetil deve ser uma Thread", projetil instanceof Thread);
    }
}
