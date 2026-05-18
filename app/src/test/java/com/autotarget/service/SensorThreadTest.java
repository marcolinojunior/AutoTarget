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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        jogo.adicionarAlvoManual(new AlvoComum(200, 300, 20, 0, 1000, 1000), Lado.ESQUERDO);
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

    @Test
    public void testSnapshotIncluiReferenciaRealNaoZero() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        AlvoComum alvo = new AlvoComum(220, 330, 20, 2, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
        jogo.adicionarCanhao(120, 200, Lado.ESQUERDO);

        SensorThread sensorThread = new SensorThread(jogo, new Object(), jogo.getCollisionLock());
        Method coletarDados = SensorThread.class.getDeclaredMethod("coletarDados");
        coletarDados.setAccessible(true);
        for (int i = 0; i < SensorThread.getHistoricoMinimoReconciliacao(); i++) {
            coletarDados.invoke(sensorThread);
        }

        java.util.List<SensorThread.TargetSnapshot> snapshots =
                sensorThread.getSnapshotsParaReconciliacao(Lado.ESQUERDO);
        assertNotNull(snapshots);
        assertTrue(!snapshots.isEmpty());
        SensorThread.TargetSnapshot snapshot = snapshots.get(0);
        assertTrue("x verdadeiro deve ser preenchido", Math.abs(snapshot.verdadeiroX) > 0.001f);
        assertTrue("y verdadeiro deve ser preenchido", Math.abs(snapshot.verdadeiroY) > 0.001f);
    }

    @Test
    public void testRuidoGaussianoProporcionalCincoPorCento() throws Exception {
        SensorThread sensorThread = new SensorThread(new Jogo(), new Object(), new Object());
        Method ruido = SensorThread.class.getDeclaredMethod("aplicarRuidoProporcional", float.class);
        ruido.setAccessible(true);

        final float valorBase = 200f;
        final int amostras = 4000;
        double soma = 0;
        double soma2 = 0;
        for (int i = 0; i < amostras; i++) {
            float leitura = (float) ruido.invoke(sensorThread, valorBase);
            soma += leitura;
            soma2 += leitura * leitura;
        }
        double media = soma / amostras;
        double variancia = (soma2 / amostras) - (media * media);
        double desvio = Math.sqrt(Math.max(0.0, variancia));

        assertTrue("Média do ruído deve preservar valor base", Math.abs(media - valorBase) < 4.0);
        assertTrue("Desvio padrão deve ficar perto de 5%", desvio > 6.0 && desvio < 14.0);
    }

    @Test
    public void testEstatisticaRuidoBaseadaEmResiduo() throws Exception {
        // Testa se o cálculo de registrarEstatisticasSensor usa resíduo real
        Jogo jogo = new Jogo();
        SensorThread sensorThread = new SensorThread(jogo, new Object(), new Object());
        
        SensorThread.SideSnapshot snap = new SensorThread.SideSnapshot();
        snap.alvosAtivos.add(new AlvoComum(100, 100, 0, 0, 1000, 1000));
        snap.leiturasPosX = new float[]{105f}; // Ruído de +5
        snap.verdadeiroPosX = new float[]{100f};
        snap.leiturasPosY = new float[]{100f};
        snap.verdadeiroPosY = new float[]{100f};
        snap.leiturasVelocidade = new float[]{0f};
        snap.leiturasVelocidadeX = new float[]{0f};
        snap.leiturasVelocidadeY = new float[]{0f};
        snap.verdadeiroVelocidadeX = new float[]{0f};
        snap.verdadeiroVelocidadeY = new float[]{0f};

        Method registrar = SensorThread.class.getDeclaredMethod("registrarEstatisticasSensor", Lado.class, SensorThread.SideSnapshot.class);
        registrar.setAccessible(true);
        
        // Precisamos de histórico para registrar
        LinkedList<float[][]> hist = obterHistorico(sensorThread, Lado.ESQUERDO);
        for (int i=0; i<10; i++) hist.add(new float[][]{{100f}});

        registrar.invoke(sensorThread, Lado.ESQUERDO, snap);
        
        // Verificar se o log recebeu o valor correto (residuo=5)
        com.autotarget.util.ReconciliationLog log = com.autotarget.util.ReconciliationLog.getInstance();
        java.util.List<com.autotarget.util.ReconciliationLog.SensorVarianceSample> samples = log.getSensorVarianceSamples();
        assertFalse(samples.isEmpty());
        com.autotarget.util.ReconciliationLog.SensorVarianceSample last = samples.get(samples.size() - 1);
        
        assertEquals("Média do resíduo X deve ser 5.0", 5.0, last.mediaX, 0.01);
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
