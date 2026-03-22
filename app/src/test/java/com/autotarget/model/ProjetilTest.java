package com.autotarget.model;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testes unitários para a lógica de colisão do Projétil.
 */
public class ProjetilTest {

    private Object dummyLock;
    private ArrayList<Canhao> dummyCanhoes;

    @Before
    public void setUp() {
        // Configurações e mocks manuais de dependências exigidas pelas classes, mas não relevantes para a colisão em si.
        dummyLock = new Object();
        dummyCanhoes = new ArrayList<>();
    }

    @Test
    public void testCollide_CenarioNormal_Sobrepostos() {
        // Cenário Normal: Projétil e Alvo sobrepostos (deve retornar true).
        // Alvo (100, 100), R = 30. Projétil (100, 100)
        Alvo alvo = new AlvoComum(100f, 100f, 30f, 3f, 800, 600, dummyCanhoes, dummyLock);
        Projetil projetil = new Projetil(100f, 100f, 1f, 0f, 10f, 800, 600);

        assertTrue("Projétil e Alvo na mesma posição e sobrepostos devem colidir", projetil.collide(alvo));
    }

    @Test
    public void testCollide_CenarioNormal_MuitoLonge() {
        // Cenário Normal: Projétil muito longe do Alvo (deve retornar false).
        // Alvo (100, 100), R = 30. Projétil (500, 500)
        Alvo alvo = new AlvoComum(100f, 100f, 30f, 3f, 800, 600, dummyCanhoes, dummyLock);
        Projetil projetil = new Projetil(500f, 500f, 1f, 0f, 10f, 800, 600);

        assertFalse("Projétil muito longe do alvo não deve retornar colisão", projetil.collide(alvo));
    }

    @Test
    public void testCollide_CasoDeBorda_TocandoExatamenteBorda() {
        // Caso de Borda: Projétil tocando exatamente a borda do Alvo.
        // A distância é exata e igual à soma dos raios. Deve retornar true.
        // Raio do Alvo = 30f, Raio estático do Projétil = 5f. Soma = 35f.
        Alvo alvo = new AlvoComum(100f, 100f, 30f, 3f, 800, 600, dummyCanhoes, dummyLock);
        
        // Projetil no eixo X exatamente a 35 pixels de distância -> Posição (135, 100)
        Projetil projetilX = new Projetil(135f, 100f, 1f, 0f, 10f, 800, 600);
        assertTrue("Projétil tocando exatamente a borda à direita do alvo deve colidir", projetilX.collide(alvo));

        // Projetil no eixo Y exatamente a 35 pixels de distância -> Posição (100, 135)
        Projetil projetilY = new Projetil(100f, 135f, 1f, 0f, 10f, 800, 600);
        assertTrue("Projétil tocando exatamente a borda inferior do alvo deve colidir", projetilY.collide(alvo));
    }
}
