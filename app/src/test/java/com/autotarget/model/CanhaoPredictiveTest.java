package com.autotarget.model;

import com.autotarget.engine.Jogo;
import com.autotarget.util.ReconciliationLog;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CanhaoPredictiveTest {

    private Canhao canhao;
    private Jogo mockJogo;
    private List<Alvo> alvos;
    private Object collisionLock;

    @Before
    public void setUp() {
        mockJogo = mock(Jogo.class);
        alvos = new ArrayList<>();
        collisionLock = new Object();
        canhao = new Canhao(0, 0, Lado.ESQUERDO, alvos, collisionLock, 1000, 1000, mockJogo);
        ReconciliationLog.getInstance().reset();
    }

    @Test
    public void testBouncingReflection() {
        // Alvo se movendo para fora da tela (x=990, dirX=1, vel=10)
        // Largura da tela = 1000
        Alvo alvo = new Alvo(990, 500, 1, 0, 1000, 1000);
        alvos.add(alvo);
        when(mockJogo.reservarAlvo(canhao)).thenReturn(alvo);

        // Forçar um tempo de impacto 't' grande o suficiente para rebater
        // t = dist / vProj. Se dist=900 e vProj=24/16=1.5, t=600ms.
        // vTargetX = 1 * 3 / 30 = 0.1 px/ms (Alvo vel base é 3f)
        // virtualX = 990 + 0.1 * 600 = 990 + 60 = 1050.
        // Com rebatida: 1000 - (1050 - 1000) = 950.

        canhao.disparar();

        // Verificar no log se o aimX foi rebatido (deve estar por volta de 950, não 1050)
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        // O log de SHOT contém "aim=(x,y)"
        // SHOT lado=ESQUERDO hit=N canhao=(0,0) alvo=(990,500) aim=(950,500)
        assertTrue("Log deve conter aim próximo a 950: " + log, log.contains("aim=(950"));
    }

    @Test
    public void testConfidenceFilterHoldFire() {
        // AlvoRapido muito longe (baixa confiança)
        AlvoRapido alvo = new AlvoRapido(900, 500, 1, 0, 1000, 1000);
        alvos.add(alvo);
        when(mockJogo.reservarAlvo(canhao)).thenReturn(alvo);

        // dist = 900. vProj = 1.5 px/ms. t = 600ms.
        // ticks = 600 / 30 = 20.
        // chance = 0.95^20 = 0.358 (< 0.65) -> Deve abortar.

        canhao.disparar();

        // Se abortou, não deve haver log de SHOT no ReconciliationLog
        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertFalse("Não deve haver disparos para alvo incerto: " + log, log.contains("SHOT"));
    }

    @Test
    public void testConfidenceFilterShoot() {
        // AlvoRapido perto (alta confiança)
        AlvoRapido alvo = new AlvoRapido(50, 0, 1, 0, 1000, 1000);
        alvos.add(alvo);
        when(mockJogo.reservarAlvo(canhao)).thenReturn(alvo);

        // dist = 50. vProj = 1.5 px/ms. t = 33.3ms.
        // ticks = 33.3 / 30 = 1.
        // chance = 0.95^1 = 0.95 (> 0.65) -> Deve atirar.

        canhao.disparar();

        String log = ReconciliationLog.getInstance().gerarRelatorio();
        assertTrue("Deve haver disparos para alvo próximo: " + log, log.contains("SHOT"));
    }
}