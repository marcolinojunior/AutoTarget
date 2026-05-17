package com.autotarget.service;

import com.autotarget.engine.Jogo;
import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SensorThreadTest {

    @Test
    public void testMediaVarianciaComDadosConhecidos() throws Exception {
        SensorThread sensorThread = new SensorThread(new Jogo(), new Object(), new Object());

        LinkedList<float[][]> historico = obterHistorico(sensorThread, Lado.ESQUERDO);
        historico.clear();
        historico.add(new float[][]{{10f, 20f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{18f, 28f}});
        // Preenchendo até TAMANHO_HISTORICO = 10 para o teste passar
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});
        historico.add(new float[][]{{14f, 24f}});

        float[][] media = sensorThread.getMediaDistancias(Lado.ESQUERDO);
        float[][] variancia = sensorThread.getVarianciaDistancias(Lado.ESQUERDO);

        assertNotNull(media);
        assertNotNull(variancia);
        assertEquals(14f, media[0][0], 0.0001f);
        assertEquals(24f, media[0][1], 0.0001f);
        assertEquals(3.5555f, variancia[0][0], 0.0001f);
        assertEquals(3.5555f, variancia[0][1], 0.0001f);
    }

    @Test
    public void testJanelaDeslizanteMantemMaximoDezLeituras() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        jogo.getAlvosEsquerdo().add(new AlvoComum(200, 300, 20, 0, 1000, 1000));
        try {
            jogo.adicionarCanhao(120, 200, Lado.ESQUERDO);
        } catch (JogoException e) {
            throw new AssertionError("Falha ao preparar canhão de teste", e);
        }

        SensorThread sensorThread = new SensorThread(jogo, new Object(), jogo.getCollisionLock());
        Method coletarDados = SensorThread.class.getDeclaredMethod("coletarDados");
        coletarDados.setAccessible(true);

        for (int i = 0; i < 12; i++) {
            coletarDados.invoke(sensorThread);
        }

        assertEquals(SensorThread.getHistoricoMinimoReconciliacao(),
                sensorThread.getHistoricoCount(Lado.ESQUERDO));

        for (Alvo alvo : jogo.getAlvosEsquerdo()) {
            alvo.setAtivo(false);
            alvo.interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<float[][]> obterHistorico(SensorThread sensorThread, Lado lado) throws Exception {
        Field dadosPorLadoField = SensorThread.class.getDeclaredField("dadosPorLado");
        dadosPorLadoField.setAccessible(true);
        EnumMap<Lado, Object> dadosPorLado = (EnumMap<Lado, Object>) dadosPorLadoField.get(sensorThread);

        Object sideData = dadosPorLado.get(lado);
        Field historicoField = sideData.getClass().getDeclaredField("historicoDistancias");
        historicoField.setAccessible(true);
        return (LinkedList<float[][]>) historicoField.get(sideData);
    }
}
