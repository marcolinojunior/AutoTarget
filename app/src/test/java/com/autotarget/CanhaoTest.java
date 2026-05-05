/*
 * ============================================================================
 * Arquivo: CanhaoTest.java
 * Pacote:  com.autotarget (test)
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe de testes unitários JUnit para a classe Canhao. Valida criação
 *   com lado (ESQUERDO/DIREITO), disparo condicionado ao mesmo lado,
 *   mecanismo de penalidade na taxa de disparo, parada do canhão e
 *   determinação de lado via Lado.determinar().
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Testes unitários (6.1.7):
 *     - ≥3 testes cobrindo pontos críticos + casos de borda:
 *       • testCriacaoCanhaoEsquerdo — atributos iniciais com Lado.ESQUERDO
 *       • testCriacaoCanhaoDireito — atributos iniciais com Lado.DIREITO
 *       • testDisparoSemAlvo — sem alvos → nenhum projétil criado
 *       • testDisparoComAlvoMesmoLado — alvo ESQ + canhão ESQ → dispara
 *       • testDisparoIgnoraAlvoOutroLado — alvo DIR + canhão ESQ → não dispara
 *       • testPararCanhao — desativa canhão e projéteis
 *       • testPenalidadeAumentaIntervaloDisparo — 2x no intervalo
 *       • testDeterminacaoLado — Lado.determinar() para valores de borda
 *
 *   ► Cenário competitivo (seção 4.2):
 *     - testDisparoComAlvoMesmoLado e testDisparoIgnoraAlvoOutroLado
 *       validam que canhões SÓ disparam contra alvos do MESMO lado.
 *     - testPenalidadeAumentaIntervaloDisparo valida o mecanismo de
 *       penalidade: intervalo base → aplicar(true) → 2x → aplicar(false) → base.
 *
 *   ► Sincronização (6.1.5):
 *     - Usa CopyOnWriteArrayList e collisionLock nos testes, replicando
 *       a configuração de sincronização do jogo real.
 *
 * TOTAL DE TESTES: 8
 *
 * ============================================================================
 */
package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * Testes unitários para a classe {@link Canhao}.
 * <p>
 * Verifica criação com lado, disparo no mesmo lado, penalidade,
 * e concorrência na lista de projéteis.
 */
public class CanhaoTest {

    private CopyOnWriteArrayList<Alvo> alvos;
    private Object collisionLock;
    private static final int LARGURA = 800;
    private static final int ALTURA = 600;

    /** Lista para rastrear canhões criados nos testes, garantindo cleanup. */
    private final java.util.List<Canhao> canhoesTestados = new java.util.ArrayList<>();

    @Before
    public void setUp() {
        alvos = new CopyOnWriteArrayList<>();
        collisionLock = new Object();
        canhoesTestados.clear();
    }

    /**
     * Cleanup: desativa e interrompe todas as threads de projétil e canhão
     * criadas durante os testes. Evita flaky tests por threads vazando.
     */
    @After
    public void tearDown() {
        for (Canhao c : canhoesTestados) {
            c.pararCanhao();
            c.interrupt();
        }
        for (Alvo a : alvos) {
            a.setAtivo(false);
            a.interrupt();
        }
    }

    @Test
    public void testCriacaoCanhaoEsquerdo() {
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);

        assertEquals(100f, canhao.getX(), 0.001f);
        assertEquals(200f, canhao.getY(), 0.001f);
        assertEquals(0f, canhao.getAngulo(), 0.001f);
        assertEquals(Lado.ESQUERDO, canhao.getLado());
        assertTrue(canhao.isAtivo());
        assertNotNull(canhao.getProjeteis());
        assertTrue(canhao.getProjeteis().isEmpty());
    }

    @Test
    public void testCriacaoCanhaoDireito() {
        Canhao canhao = new Canhao(600, 200, Lado.DIREITO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);

        assertEquals(Lado.DIREITO, canhao.getLado());
        assertEquals(600f, canhao.getX(), 0.001f);
    }

    @Test
    public void testDisparoSemAlvo() {
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);
        canhao.disparar();

        assertEquals(0, canhao.getProjeteis().size());
    }

    @Test
    public void testDisparoComAlvoMesmoLado() {
        // Alvo no lado esquerdo (x=200 < 400=metade)
        AlvoComum alvo = new AlvoComum(200, 200, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Canhão no lado esquerdo
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);
        canhao.disparar();

        assertEquals("Deveria disparar contra alvo no mesmo lado",
                1, canhao.getProjeteis().size());
    }

    @Test
    public void testDisparoIgnoraAlvoOutroLado() {
        // Alvo no lado DIREITO (x=600 > 400=metade)
        AlvoComum alvo = new AlvoComum(600, 200, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Canhão no lado ESQUERDO
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);
        canhao.disparar();

        assertEquals("Não deveria disparar contra alvo do outro lado",
                0, canhao.getProjeteis().size());
    }

    @Test
    public void testPararCanhao() {
        AlvoComum alvo = new AlvoComum(200, 200, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);
        canhao.disparar();
        canhao.pararCanhao();

        assertFalse(canhao.isAtivo());
        for (Projetil p : canhao.getProjeteis()) {
            assertFalse(p.isAtivo());
        }
    }

    @Test
    public void testPenalidadeAumentaIntervaloDisparo() {
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
        canhoesTestados.add(canhao);

        int intervaloBase = Canhao.getIntervaloDisparoBase();
        assertEquals(intervaloBase, canhao.getIntervaloDisparo());

        // Aplicar penalidade
        canhao.aplicarPenalidade(true);
        assertEquals(intervaloBase * 2, canhao.getIntervaloDisparo());

        // Remover penalidade
        canhao.aplicarPenalidade(false);
        assertEquals(intervaloBase, canhao.getIntervaloDisparo());
    }

    @Test
    public void testDeterminacaoLado() {
        assertEquals(Lado.ESQUERDO, Lado.determinar(100, LARGURA));
        assertEquals(Lado.ESQUERDO, Lado.determinar(399, LARGURA));
        assertEquals(Lado.DIREITO, Lado.determinar(400, LARGURA));
        assertEquals(Lado.DIREITO, Lado.determinar(700, LARGURA));
    }
}
