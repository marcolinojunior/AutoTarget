package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;

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

    @Before
    public void setUp() {
        alvos = new CopyOnWriteArrayList<>();
        collisionLock = new Object();
    }

    @Test
    public void testCriacaoCanhaoEsquerdo() {
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);

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

        assertEquals(Lado.DIREITO, canhao.getLado());
        assertEquals(600f, canhao.getX(), 0.001f);
    }

    @Test
    public void testDisparoSemAlvo() {
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
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
        canhao.disparar();

        assertEquals("Deveria disparar contra alvo no mesmo lado",
                1, canhao.getProjeteis().size());

        // Limpar
        for (Projetil p : canhao.getProjeteis()) {
            p.setAtivo(false); p.interrupt();
        }
    }

    @Test
    public void testDisparoIgnoraAlvoOutroLado() {
        // Alvo no lado DIREITO (x=600 > 400=metade)
        AlvoComum alvo = new AlvoComum(600, 200, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Canhão no lado ESQUERDO
        Canhao canhao = new Canhao(100, 200, Lado.ESQUERDO, alvos,
                collisionLock, LARGURA, ALTURA);
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
