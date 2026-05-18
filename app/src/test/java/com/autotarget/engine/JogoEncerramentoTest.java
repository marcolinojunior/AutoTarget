package com.autotarget.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.autotarget.model.Lado;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class JogoEncerramentoTest {

    @Test
    public void testVencedorEsquerdoNoEncerramento() throws Exception {
        Jogo jogo = new Jogo();
        CapturaListener listener = new CapturaListener();
        jogo.setListener(listener);

        setEstadoRodando(jogo);
        setPontuacao(jogo, 12, 8);
        invocarEncerrarPartida(jogo);

        assertEquals(Lado.ESQUERDO, listener.vencedorCapturado);
    }

    @Test
    public void testEmpateNoEncerramento() throws Exception {
        Jogo jogo = new Jogo();
        CapturaListener listener = new CapturaListener();
        jogo.setListener(listener);

        setEstadoRodando(jogo);
        setPontuacao(jogo, 10, 10);
        invocarEncerrarPartida(jogo);

        assertNull(listener.vencedorCapturado);
    }

    private void setEstadoRodando(Jogo jogo) throws Exception {
        Field estadoField = Jogo.class.getDeclaredField("estado");
        estadoField.setAccessible(true);
        estadoField.set(jogo, Jogo.Estado.RODANDO);

        Field inicioField = Jogo.class.getDeclaredField("timestampInicio");
        inicioField.setAccessible(true);
        inicioField.setLong(jogo, System.currentTimeMillis());
    }

    private void setPontuacao(Jogo jogo, int esq, int dir) throws Exception {
        Field esqField = Jogo.class.getDeclaredField("pontuacaoEsquerdo");
        Field dirField = Jogo.class.getDeclaredField("pontuacaoDireito");
        esqField.setAccessible(true);
        dirField.setAccessible(true);
        ((AtomicInteger) esqField.get(jogo)).set(esq);
        ((AtomicInteger) dirField.get(jogo)).set(dir);
    }

    private void invocarEncerrarPartida(Jogo jogo) throws Exception {
        Method encerrar = Jogo.class.getDeclaredMethod("encerrarPartida");
        encerrar.setAccessible(true);
        encerrar.invoke(jogo);
    }

    private static final class CapturaListener implements Jogo.OnJogoListener {
        Lado vencedorCapturado;

        @Override public void onPontuacaoAtualizada(int esquerda, int direita) {}
        @Override public void onEstadoAlterado(Jogo.Estado estado) {}
        @Override public void onAlvosAtivosAtualizado(int count) {}
        @Override public void onTempoAtualizado(int segundosRestantes) {}
        @Override public void onEnergiaAtualizada(float energiaEsq, float energiaDir) {}
        @Override
        public void onPartidaEncerrada(int pontEsq, int pontDir, int tempoTotal, int reconciliacoes, Lado vencedor) {
            this.vencedorCapturado = vencedor;
        }
        @Override public void onRelatorioReconciliacao(String relatorio) {}
    }
}
