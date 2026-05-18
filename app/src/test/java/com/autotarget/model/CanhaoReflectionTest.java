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
        // Alvo em 900, indo para direita (dirX=1) com vel=3.
        // t = 660ms (aproximado). dist = 900 -> vProj=1.5 -> t=600ms.
        // virtualX = 900 + 0.1 * 600 = 960. (Sem bounce)
        // Se virtualX = 1050 -> bounceX=1, offset=50 -> aimX = 1000 - 50 = 950.
        
        TestAlvo alvo = new TestAlvo(900, 500, 1, 0, 10); // Vel alta para forçar bounce
        jogo.getAlvosEsquerdo().add(alvo);
        
        // dist=900, vProj=1.5 -> t=600ms.
        // vTargetX = (1 * 10) / 30 = 0.333 px/ms.
        // virtualX = 900 + 0.333 * 600 = 1100.
        // bouncesX = 1, offset = 100. aimX = 1000 - 100 = 900.
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aim próximo a 900: " + log, log.contains("aim=(900"));
    }

    @Test
    public void testBackwardBounce() {
        // Alvo em 100, indo para esquerda (dirX=-1)
        TestAlvo alvo = new TestAlvo(100, 500, -1, 0, 10);
        jogo.getAlvosEsquerdo().add(alvo);
        
        // dist=100, vProj=1.5 -> t=66.6ms.
        // vTargetX = (-1 * 10) / 30 = -0.333 px/ms.
        // virtualX = 100 - 0.333 * 66.6 = 100 - 22.2 = 77.8. (Sem bounce ainda)
        
        // Forçar bounce aumentando velocidade
        alvos.clear();
        alvo = new TestAlvo(100, 500, -1, 0, 60); 
        jogo.getAlvosEsquerdo().add(alvo);
        // dist=100, t=66.6. vTargetX = -2. virtualX = 100 - 133.3 = -33.3.
        // bouncesX = 0 (abs(-33.3)=33.3 / 1000 = 0), offset = 33.3.
        // aimX = 33.3.
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aim próximo a 33: " + log, log.contains("aim=(33"));
    }

    @Test
    public void testMultipleBouncesY() {
        // Canhão em (0, 0), Alvo em (0, 100) indo para baixo (dirY=1)
        canhao.setPosicao(0, 0);
        TestAlvo alvo = new TestAlvo(0, 100, 0, 1, 100); 
        jogo.getAlvosEsquerdo().add(alvo);
        
        // dist=100, vProj=1.5 -> t=66.6.
        // vTargetY = (1 * 100) / 30 = 3.333 px/ms.
        // virtualY = 100 + 3.333 * 66.6 = 100 + 222 = 322. (Sem bounce, tela é 1000)
        
        // Mais rápido!
        alvos.clear();
        alvo = new TestAlvo(0, 100, 0, 1, 600);
        jogo.getAlvosEsquerdo().add(alvo);
        // dist=100, t=66.6. vTargetY = 20. virtualY = 100 + 1333.3 = 1433.3.
        // bouncesY = 1, offset = 433.3. aimY = 1000 - 433.3 = 566.7.
        
        canhao.disparar();
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve conter aimY próximo a 566: " + log, log.contains(",566"));
    }
}