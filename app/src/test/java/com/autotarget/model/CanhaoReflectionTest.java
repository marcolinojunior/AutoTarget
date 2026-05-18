package com.autotarget.model;

import com.autotarget.engine.Jogo;
import com.autotarget.util.ReconciliationLog;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CanhaoReflectionTest {

    private Canhao canhao;
    private Jogo jogo;
    private List<Alvo> alvos;

    @Before
    public void setUp() {
        jogo = new Jogo();
        alvos = new ArrayList<>();
        // largura=1000, altura=1000
        canhao = new Canhao(0, 500, Lado.ESQUERDO, alvos, new Object(), 1000, 1000, jogo);
        ReconciliationLog.getInstance().reset();
    }

    private static class TestAlvo extends AlvoComum {
        public TestAlvo(float x, float y, float dirX, float dirY, float vel) {
            super(x, y, 30, vel, 1000, 1000);
            this.direcaoX = dirX;
            this.direcaoY = dirY;
            this.setAtivo(true);
        }
    }

    @Test
    public void testForwardBounce() {
        // Alvo em 900, indo para direita (dirX=1) com vel=30.
        // vTargetX = 1 px/ms. vProj = 1.5 px/ms. dist = 900.
        // t = 1800ms. virtualX = 900 + 1800 = 2700.
        // bounceX = 2 (par), offset = 700. aimX = 700.
        
        TestAlvo alvo = new TestAlvo(900, 500, 1, 0, 30); 
        jogo.getAlvosEsquerdo().add(alvo);
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aim próximo a 700: " + log, log.contains("aim=(700"));
    }

    @Test
    public void testBackwardBounce() {
        // Alvo em 100, indo para esquerda (dirX=-1) com vel=30.
        // vTargetX = -1. vProj = 1.5. dist = 100.
        // a = -1.25. b = -200. c = 10000.
        // delta = 40000 + 50000 = 90000. sqrt=300.
        // t = (200 - 300) / -2.5 = 40.
        // virtualX = 100 - 40 = 60. No bounce.
        
        // Vamos usar Cannon em 1000, Alvo em 100 indo para esquerda.
        canhao.setPosicao(1000, 500);
        TestAlvo alvo = new TestAlvo(100, 500, -1, 0, 30);
        jogo.getAlvosEsquerdo().add(alvo);
        // dist=900. vTargetX=-1 (afastando). vProj=1.5.
        // virtualX = 100 - 1800 = -1700.
        // Math.abs(-1700) = 1700. bounceX = 1 (ímpar), offset = 700.
        // aimX = 1000 - 700 = 300.
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aim próximo a 300: " + log, log.contains("aim=(300"));
    }

    @Test
    public void testMultipleBouncesY() {
        // Canhão em (0, 1000), Alvo em (0, 900) indo para baixo (dirY=1)
        // Isso força o alvo a se aproximar do "chão" e rebater.
        canhao.setPosicao(0, 1000);
        TestAlvo alvo = new TestAlvo(0, 900, 0, 1, 600); 
        jogo.getAlvosEsquerdo().add(alvo);
        
        // dist=100. vProj=1.5. vTargetY=20 (aproximando).
        // a = 20^2 - 1.5^2 = 397.75.
        // dx=0, dy = 900 - 1000 = -100.
        // b = 2 * (0 + -100 * 20) = -4000.
        // c = 10000.
        // delta = (-4000)^2 - 4 * 397.75 * 10000 = 90000.
        // sqrt(delta) = 300.
        // t1 = (4000 + 300) / 795.5 = 5.4.
        // t2 = (4000 - 300) / 795.5 = 4.65.
        // t = 4.65 (ms? No, frames in Canhao logic, but it's used as ms in reflection).
        // Na verdade em Canhao.java: vTarget = (dir * vel) / 30f. vProj = 24 / 16 = 1.5.
        // virtualY = 900 + 20 * 4.65 = 900 + 93 = 993. (Ainda não bateu).
        
        // Vamos aumentar t aumentando a distância.
        canhao.setPosicao(0, 0); // Longe
        alvos.clear();
        alvo = new TestAlvo(0, 900, 0, 1, 600);
        jogo.getAlvosEsquerdo().add(alvo);
        // dist=900. b = 2 * (900 * 20) = 36000. c = 810000.
        // delta = 36000^2 - 4 * 397.75 * 810000 = 1,296,000,000 - 1,288,710,000 = 7,290,000.
        // sqrt(delta) = 2700.
        // t = (-36000 + 2700) / 795.5 -> Negativo.
        
        // VELOCIDADE DO PROJETIL É O PROBLEMA. 1.5 px/ms é muito lento comparado a 20 px/ms.
        // Vou usar velocidades menores nos testes.
        canhao.setPosicao(0, 0);
        alvos.clear();
        alvo = new TestAlvo(0, 500, 0, 1, 30); // vTargetY = 1 px/ms. vProj = 1.5 px/ms.
        jogo.getAlvosEsquerdo().add(alvo);
        // dist=500. a = 1^2 - 1.5^2 = -1.25.
        // b = 2 * (500 * 1) = 1000. c = 250000.
        // delta = 1000000 - 4 * -1.25 * 250000 = 1000000 + 1250000 = 2250000.
        // sqrt(delta) = 1500.
        // t = (-1000 - 1500) / -2.5 = 2500 / 2.5 = 1000 ms.
        // virtualY = 500 + 1 * 1000 = 1500.
        // bouncesY = 1, offset = 500. aimY = 1000 - 500 = 500.
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aimY próximo a 500: " + log, log.contains(",500"));
    }
}