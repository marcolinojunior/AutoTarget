package com.autotarget.service;

import com.autotarget.engine.Jogo;
import com.autotarget.model.Lado;
import com.autotarget.util.DataReconciliation;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;

public class ReconciliacaoThreadTest {

    @Test
    public void testBloqueiaReconciliacaoComMenosDeDezLeituras() throws Exception {
        Jogo jogo = new Jogo();
        SensorThread sensor = new SensorThread(jogo, new Object(), new Object()) {
            @Override
            public int getHistoricoCount(Lado lado) {
                return SensorThread.getHistoricoMinimoReconciliacao() - 1;
            }
        };

        ReconciliacaoThread reconciliacao = new ReconciliacaoThread(
                new DataReconciliation(), sensor, jogo, new Object(), new Object());

        Method method = ReconciliacaoThread.class.getDeclaredMethod("executarReconciliacaoPorLado", Lado.class);
        method.setAccessible(true);
        boolean executou = (boolean) method.invoke(reconciliacao, Lado.ESQUERDO);

        assertFalse("Reconciliação deve ser bloqueada com histórico < 10", executou);
    }
}
