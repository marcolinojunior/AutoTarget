package com.autotarget.engine;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;
import com.autotarget.util.EnergyManager;
import com.autotarget.service.ReconciliacaoThread;
import com.autotarget.service.SensorThread;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

public class JogoRegrasNegocioTest {

    @Test
    public void testUmAbateGeraUmCreditoEnergeticoExato() {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        EnergyManager em = jogo.getEnergyManager(Lado.ESQUERDO);
        em.set(50.0f);
        
        Alvo alvo = new AlvoComum(200, 200, 0, 0, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
        
        alvo.tentarAbater(Lado.ESQUERDO);
        
        try {
            java.lang.reflect.Method processar = Jogo.class.getDeclaredMethod("processarAlvosInativos", List.class, Lado.class);
            processar.setAccessible(true);
            processar.invoke(jogo, jogo.getAlvosEsquerdo(), Lado.ESQUERDO);
        } catch (Exception e) {
            fail("Erro ao invocar processarAlvosInativos: " + e.getMessage());
        }
        
        assertEquals("Energia deve ter aumentado exatamente 1.0f (abate rápido)", 51.0f, em.get(), 0.01f);
    }

    @Test
    public void testFimDePartidaEVencedor() throws Exception {
        Jogo jogo = new Jogo();
        jogo.iniciar();
        
        java.lang.reflect.Field tempoField = Jogo.class.getDeclaredField("tempoRestante");
        tempoField.setAccessible(true);
        tempoField.set(jogo, 0);
        
        java.lang.reflect.Field pe = Jogo.class.getDeclaredField("pontuacaoEsquerdo");
        pe.setAccessible(true);
        ((AtomicInteger)pe.get(jogo)).set(100);
        
        java.lang.reflect.Field pd = Jogo.class.getDeclaredField("pontuacaoDireito");
        pd.setAccessible(true);
        ((AtomicInteger)pd.get(jogo)).set(50);
        
        final Lado[] vencedorCapturado = new Lado[1];
        final CountDownLatch latch = new CountDownLatch(1);
        
        jogo.setListener(new Jogo.OnJogoListener() {
            @Override public void onPontuacaoAtualizada(int e, int d) {}
            @Override public void onEstadoAlterado(Jogo.Estado novo) {}
            @Override public void onAlvosAtivosAtualizado(int total) {}
            @Override public void onTempoAtualizado(int tempo) {}
            @Override public void onEnergiaAtualizada(float e, float d) {}
            @Override public void onRelatorioReconciliacao(String r) {}
            @Override public void onPartidaEncerrada(int e, int d, int tempo, int rec, Lado vencedor) {
                vencedorCapturado[0] = vencedor;
                latch.countDown();
            }
        });
        
        jogo.encerrarPartida();
        
        assertTrue("onPartidaEncerrada deve ser chamado", latch.await(1, TimeUnit.SECONDS));
        assertEquals("Vencedor deve ser o ESQUERDO", Lado.ESQUERDO, vencedorCapturado[0]);
        assertEquals("Estado deve ser ENCERRADO", Jogo.Estado.ENCERRADO, jogo.getEstado());
    }

    @Test
    public void testEmpateLogico() throws Exception {
        Jogo jogo = new Jogo();
        jogo.iniciar();
        
        final boolean[] empateCapturado = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        
        jogo.setListener(new Jogo.OnJogoListener() {
            @Override public void onPontuacaoAtualizada(int e, int d) {}
            @Override public void onEstadoAlterado(Jogo.Estado novo) {}
            @Override public void onAlvosAtivosAtualizado(int total) {}
            @Override public void onTempoAtualizado(int tempo) {}
            @Override public void onEnergiaAtualizada(float e, float d) {}
            @Override public void onRelatorioReconciliacao(String r) {}
            @Override public void onPartidaEncerrada(int e, int d, int tempo, int rec, Lado vencedor) {
                if (vencedor == null) empateCapturado[0] = true;
                latch.countDown();
            }
        });
        
        jogo.encerrarPartida();
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue("Partida sem pontos deve resultar em empate (vencedor null)", empateCapturado[0]);
    }

    @Test
    public void testSelecaoConsistenteCanhaoRemocao() throws Exception {
        float[][] distancias = {
            {10f, 500f},
            {15f, 600f}
        };
        
        ReconciliacaoThread rt = new ReconciliacaoThread(null, null, null, null, null);
        java.lang.reflect.Method method = rt.getClass().getDeclaredMethod("selecionarCanhaoMenorContribuicao", float[][].class);
        method.setAccessible(true);
        
        Integer indice = (Integer) method.invoke(rt, (Object) distancias);
        
        assertEquals("Canhão 1 deve ser selecionado para remoção", 1, indice.intValue());
    }

    @Test
    public void testMudancaDinamicaCanhoesNaoCorrompeHistorico() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        SensorThread st = new SensorThread(jogo, new Object(), new Object());
        
        Alvo alvo = new AlvoComum(100, 100, 0, 0, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
        
        jogo.adicionarCanhao(10, 10, Lado.ESQUERDO);
        java.lang.reflect.Method coletar = SensorThread.class.getDeclaredMethod("coletarDados");
        coletar.setAccessible(true);
        coletar.invoke(st);
        
        jogo.adicionarCanhao(20, 20, Lado.ESQUERDO);
        coletar.invoke(st);
        
        List<SensorThread.TargetSnapshot> snaps = st.getSnapshotsParaReconciliacao(Lado.ESQUERDO);
        assertNotNull(snaps);
    }
}
