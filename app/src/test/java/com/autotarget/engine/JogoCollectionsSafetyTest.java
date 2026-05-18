package com.autotarget.engine;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;

import org.junit.Test;

public class JogoCollectionsSafetyTest {

    @Test
    public void testSnapshotsSaoImutaveis() {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(800, 600);

        try {
            jogo.getAlvosEsquerdo().add(new AlvoComum(100, 100, 20, 0, 800, 600));
            fail("Snapshot de alvos deve ser imutável");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }

        try {
            jogo.getCanhoesDireito().clear();
            fail("Snapshot de canhões deve ser imutável");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }

        jogo.adicionarAlvoManual(new AlvoComum(200, 200, 20, 0, 800, 600), Lado.ESQUERDO);
        assertTrue(jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO).size() == 1);
    }
}
