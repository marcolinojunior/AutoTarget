package com.autotarget.model;

import com.autotarget.engine.Jogo;
import com.autotarget.util.ReconciliationLog;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CanhaoPredictiveTest {

    private Canhao canhao;
    private Jogo jogo;
    private List<Alvo> alvos;
    private Object collisionLock;

    @Before
    public void setUp() {
        jogo = new Jogo(); 
        alvos = new ArrayList<>();
        collisionLock = new Object();
        // Canhao(x, y, lado, alvos, collisionLock, largura, altura, jogo)
        canhao = new Canhao(0, 500, Lado.ESQUERDO, alvos, collisionLock, 1000, 1000, jogo);
        ReconciliationLog.getInstance().reset();
    }

    private static class TestAlvo extends AlvoComum {
        public TestAlvo(float x, float y, float dirX, float dirY, float vel, int w, int h) {
            super(x, y, 30, vel, w, h);
            this.direcaoX = dirX;
            this.direcaoY = dirY;
            this.setAtivo(true);
        }
    }

    private static class TestAlvoRapido extends AlvoRapido {
        public TestAlvoRapido(float x, float y, float dirX, float dirY, float vel, int w, int h) {
            super(x, y, 30, vel, w, h);
            this.direcaoX = dirX;
            this.direcaoY = dirY;
            this.setAtivo(true);
        }
    }

    @Test
    public void testBouncingReflection() {
        // Alvo se movendo para fora da tela (x=990, dirX=1, vel=3)
        // Largura da tela = 1000
        TestAlvo alvo = new TestAlvo(990, 500, 1, 0, 3, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        // t = dist / vProj. dx = 990. vProj = 1.5 px/ms. t = 660 ms.
        // vTargetX = 1 * 3 / 30 = 0.1 px/ms.
        // virtualX = 990 + 0.1 * 660 = 990 + 66 = 1056.
        // Rebatida: 1000 - 56 = 944.

        canhao.disparar();

        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve registrar disparo", log.contains("SHOT"));
        float aimX = extrairUltimoAimX(log);
        assertTrue("Aim refletido deve permanecer nos limites da tela", aimX >= 0f && aimX <= 1000f);
        assertTrue("Aim deve diferir da posição original (990) após reflexão", Math.abs(aimX - 990f) > 1f);
    }

    @Test
    public void testConfidenceFilterHoldFire() {
        // AlvoRapido longe. dist = 900. t = 600ms. ticks = 20. 0.95^20 = 0.358 < 0.65
        TestAlvoRapido alvo = new TestAlvoRapido(900, 500, 1, 0, 1, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        canhao.disparar();

        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertFalse("Não deve haver disparos para alvo incerto: " + log, log.contains("SHOT"));
    }

    @Test
    public void testConfidenceFilterShoot() {
        // AlvoRapido perto. dist = 50. t = 33.3ms. ticks = 1. 0.95^1 = 0.95 > 0.65
        TestAlvoRapido alvo = new TestAlvoRapido(50, 500, 1, 0, 1, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        canhao.disparar();

        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve haver disparos para alvo próximo: " + log, log.contains("SHOT"));
    }

    private float extrairUltimoAimX(String log) {
        int idx = log.lastIndexOf("aim=(");
        assertTrue("Relatório deve conter coordenada de mira", idx >= 0);
        int inicio = idx + "aim=(".length();
        int virgula = log.indexOf(',', inicio);
        assertTrue("Formato esperado aim=(x,y)", virgula > inicio);
        return Float.parseFloat(log.substring(inicio, virgula).trim());
    }
}
