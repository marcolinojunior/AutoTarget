package com.autotarget.engine;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;
import com.autotarget.util.EnergyManager;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JogoRegrasNegocioTest {

    @Test
    public void testUmAbateGeraUmCreditoEnergeticoExato() {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        EnergyManager em = jogo.getEnergyManager(Lado.ESQUERDO);
        em.set(50.0f);
        
        Alvo alvo = new AlvoComum(200, 200, 0, 0, 1000, 1000);
        // Simular nascimento agora para garantir idade < 2s => 1.0f energia
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
        
        alvo.tentarAbater(Lado.ESQUERDO);
        
        // Simular o processamento que o Jogo faria
        java.lang.reflect.Method processar = null;
        try {
            processar = Jogo.class.getDeclaredMethod("processarAlvosInativos", java.util.List.class, Lado.class);
            processar.setAccessible(true);
            processar.invoke(jogo, jogo.getAlvosEsquerdo(), Lado.ESQUERDO);
        } catch (Exception e) {
            fail("Erro ao invocar processarAlvosInativos via reflexão: " + e.getMessage());
        }
        
        assertEquals("Energia deve ter aumentado exatamente 1.0f (abate rápido)", 51.0f, em.get(), 0.01f);
    }

    @Test
    public void testFimDePartidaEVencedor() throws Exception {
        Jogo jogo = new Jogo();
        jogo.iniciar();
        
        // Simular fim do tempo
        java.lang.reflect.Field tempoField = Jogo.class.getDeclaredField("tempoRestante");
        tempoField.setAccessible(true);
        tempoField.set(jogo, 0);
        
        // Forçar um placar
        java.lang.reflect.Field pe = Jogo.class.getDeclaredField("pontuacaoEsquerdo");
        pe.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger)pe.get(jogo)).set(100);
        
        java.lang.reflect.Field pd = Jogo.class.getDeclaredField("pontuacaoDireito");
        pd.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger)pd.get(jogo)).set(50);
        
        final Lado[] vencedorCapturado = new Lado[1];
        final CountDownLatch latch = new CountDownLatch(1);
        
        jogo.setListener(new Jogo.OnJogoListener() {
            @Override public void onPontuacaoAtualizada(int e, int d) {}
            @Override public void onEstadoAlterado(Jogo.Estado novo) {}
            @Override public void onAlvosAtivosAtualizado(int total) {}
            @Override public void onTempoAtualizado(int tempo) {}
            @Override public void onEnergiaAtualizada(float e, float d) {}
            @Override public void onRelatorioReconciliacao(String r) {}
            @Override public void onPartidaEncerrada(int e, int d, int rec, int miss, Lado vencedor) {
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
            @Override public void onPartidaEncerrada(int e, int d, int rec, int miss, Lado vencedor) {
                if (vencedor == null) empateCapturado[0] = true;
                latch.countDown();
            }
        });
        
        jogo.encerrarPartida(); // Pontuações 0 e 0
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue("Partida sem pontos deve resultar em empate (vencedor null)", empateCapturado[0]);
    }
}
