package com.autotarget.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autotarget.model.Lado;
import com.autotarget.service.SensorThread;

import org.junit.Before;
import org.junit.Test;

public class DataStarvationControllerTest {

    private int historicoEsq;
    private int historicoDir;
    private DataStarvationController controller;

    @Before
    public void setUp() {
        SensorThread sensorStub = new SensorThread(null, new Object(), new Object()) {
            @Override
            public int getHistoricoCount(Lado lado) {
                return lado == Lado.ESQUERDO ? historicoEsq : historicoDir;
            }
        };
        controller = new DataStarvationController(sensorStub);
    }

    @Test
    public void historicoInsuficienteAtivaEvasao() {
        historicoEsq = 9;
        historicoDir = 5;

        controller.monitorarEAtuar();

        assertEquals(1.0, controller.getEvasaoEsquerda(), 0.0);
        assertEquals(1.0, controller.getEvasaoDireita(), 0.0);
        assertTrue(controller.alvoEsquivou(Lado.ESQUERDO));
    }

    @Test
    public void historicoSuficienteDesativaEvasao() {
        historicoEsq = 10;
        historicoDir = 15;

        controller.monitorarEAtuar();

        assertEquals(0.0, controller.getEvasaoEsquerda(), 0.0);
        assertEquals(0.0, controller.getEvasaoDireita(), 0.0);
        assertFalse(controller.alvoEsquivou(Lado.DIREITO));
    }

    @Test
    public void historicoZeroNaoTrava() {
        historicoEsq = 0;
        historicoDir = 0;

        controller.monitorarEAtuar();

        assertEquals(0.0, controller.getEvasaoEsquerda(), 0.0);
        assertEquals(0.0, controller.getEvasaoDireita(), 0.0);
        assertFalse(controller.alvoEsquivou(Lado.ESQUERDO));
    }
}