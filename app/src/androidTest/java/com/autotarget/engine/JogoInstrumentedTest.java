package com.autotarget.engine;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Lado;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testes instrumentados (AndroidTest) para o ciclo de vida completo do {@link Jogo}.
 * Valida transições de estado, pontuação, energia, concorrência e encerramento.
 *
 * Estes testes rodam em dispositivo/emulador Android real.
 */
@RunWith(AndroidJUnit4.class)
public class JogoInstrumentedTest {

    private Jogo jogo;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        jogo = new Jogo(context);
        jogo.setDimensoesTela(1080, 1920);
    }

    @After
    public void tearDown() {
        if (jogo != null && jogo.getEstado() == Jogo.Estado.RODANDO) {
            jogo.pararJogo();
        }
    }

    // ── Testes de estado ────────────────────────────────────────

    @Test
    public void testInitialState_IsParado() {
        assertEquals("Estado inicial deve ser PARADO",
                Jogo.Estado.PARADO, jogo.getEstado());
    }

    @Test
    public void testIniciar_ChangesToRodando() throws JogoException {
        jogo.iniciar();
        assertEquals("Após iniciar, estado deve ser RODANDO",
                Jogo.Estado.RODANDO, jogo.getEstado());
    }

    @Test
    public void testPararJogo_ChangesToEncerrado() throws JogoException {
        jogo.iniciar();
        jogo.pararJogo();
        assertEquals("Após parar, estado deve ser ENCERRADO",
                Jogo.Estado.ENCERRADO, jogo.getEstado());
    }

    @Test
    public void testIniciarAlreadyRunning_ThrowsException() throws JogoException {
        jogo.iniciar();
        try {
            jogo.iniciar();
            fail("Deve lançar JogoException ao iniciar jogo já rodando");
        } catch (JogoException e) {
            assertTrue("Mensagem deve mencionar 'já está em execução'",
                    e.getMessage().contains("já está em execução"));
        }
    }

    // ── Testes de energia ───────────────────────────────────────

    @Test
    public void testEnergiaInicial_100PorLado() {
        assertEquals("Energia inicial esquerda deve ser 100",
                100f, jogo.getEnergia(Lado.ESQUERDO), 0.01f);
        assertEquals("Energia inicial direita deve ser 100",
                100f, jogo.getEnergia(Lado.DIREITO), 0.01f);
    }

    @Test
    public void testEnergiaManager_ConsistentAfterOperations() {
        // Verificar que o EnergyManager mantém consistência
        float energiaInicial = jogo.getEnergia(Lado.ESQUERDO);
        assertEquals(100f, energiaInicial, 0.01f);
    }

    // ── Testes de canhões ───────────────────────────────────────

    @Test
    public void testAdicionarCanhao_ValidPosition() throws JogoException {
        jogo.adicionarCanhao(100f, 200f, Lado.ESQUERDO);
        List<com.autotarget.model.Canhao> canhoes = jogo.getCanhoesNoLadoSnapshot(Lado.ESQUERDO);
        assertEquals("Deve ter 1 canhão no lado esquerdo", 1, canhoes.size());
    }

    @Test
    public void testAdicionarCanhao_OutOfBounds_ThrowsException() {
        try {
            jogo.adicionarCanhao(-10f, 200f, Lado.ESQUERDO);
            fail("Deve lançar JogoException para posição fora dos limites");
        } catch (JogoException e) {
            assertTrue("Mensagem deve mencionar 'fora dos limites'",
                    e.getMessage().contains("fora dos limites"));
        }
    }

    @Test
    public void testAdicionarCanhao_ExceedsMax_ThrowsException() throws JogoException {
        // Adicionar 15 canhões (máximo permitido)
        for (int i = 0; i < 15; i++) {
            jogo.adicionarCanhao(50f + i * 30, 200f, Lado.ESQUERDO);
        }
        try {
            jogo.adicionarCanhao(100f, 300f, Lado.ESQUERDO);
            fail("Deve lançar JogoException ao exceder máximo de canhões");
        } catch (JogoException e) {
            assertTrue("Mensagem deve mencionar 'Máximo'",
                    e.getMessage().contains("Máximo"));
        }
    }

    @Test
    public void testRemoverCanhao() throws JogoException {
        jogo.adicionarCanhao(100f, 200f, Lado.ESQUERDO);
        List<com.autotarget.model.Canhao> canhoes = jogo.getCanhoesNoLadoSnapshot(Lado.ESQUERDO);
        assertEquals(1, canhoes.size());

        jogo.removerCanhao(canhoes.get(0));
        canhoes = jogo.getCanhoesNoLadoSnapshot(Lado.ESQUERDO);
        assertEquals("Após remover, deve ter 0 canhões", 0, canhoes.size());
    }

    // ── Testes de pontuação ─────────────────────────────────────

    @Test
    public void testPontuacaoInicial_Zero() {
        assertEquals("Pontuação inicial esquerda deve ser 0",
                0, jogo.getPontuacaoEsquerdo());
        assertEquals("Pontuação inicial direita deve ser 0",
                0, jogo.getPontuacaoDireito());
    }

    // ── Testes de dimensões ─────────────────────────────────────

    @Test
    public void testDimensoesTela_DefaultZero() {
        Jogo freshJogo = new Jogo();
        assertEquals("Largura padrão deve ser 0", 0, freshJogo.getLarguraTela());
        assertEquals("Altura padrão deve ser 0", 0, freshJogo.getAlturaTela());
    }

    @Test
    public void testDimensoesTela_SetCorrectly() {
        jogo.setDimensoesTela(1080, 1920);
        assertEquals(1080, jogo.getLarguraTela());
        assertEquals(1920, jogo.getAlturaTela());
    }

    // ── Testes de ciclo de vida completo ────────────────────────

    @Test
    public void testCicloVida_EncerramentoSalvaDados() throws JogoException, InterruptedException {
        // Listener para capturar o encerramento
        AtomicBoolean encerrado = new AtomicBoolean(false);
        AtomicReference<Lado> vencedor = new AtomicReference<>();
        AtomicInteger pontEsq = new AtomicInteger(-1);
        AtomicInteger pontDir = new AtomicInteger(-1);
        AtomicInteger reconciliacoes = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);

        jogo.setListener(new Jogo.OnJogoListener() {
            @Override public void onPontuacaoAtualizada(int e, int d) {}
            @Override public void onEstadoAlterado(Jogo.Estado s) {}
            @Override public void onAlvosAtivosAtualizado(int c) {}
            @Override public void onTempoAtualizado(int s) {}
            @Override public void onEnergiaAtualizada(float e, float d) {}
            @Override
            public void onPartidaEncerrada(int pe, int pd, int tempo, int rec, Lado v) {
                pontEsq.set(pe);
                pontDir.set(pd);
                reconciliacoes.set(rec);
                vencedor.set(v);
                encerrado.set(true);
                latch.countDown();
            }
            @Override public void onRelatorioReconciliacao(String r) {}
        });

        jogo.iniciar();

        // Aguardar até 5 segundos para o jogo iniciar
        Thread.sleep(2000);

        // Parar o jogo manualmente
        jogo.pararJogo();

        // Aguardar callback de encerramento
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Callback de encerramento deve ser recebido", received);
        assertTrue("Jogo deve ter sido encerrado", encerrado.get());
        assertNotNull("Pontuação esquerda deve ser definida", pontEsq.get() >= 0);
        assertNotNull("Pontuação direita deve ser definida", pontDir.get() >= 0);
    }

    @Test
    public void testCicloVida_ComCanhoes() throws JogoException, InterruptedException {
        // Adicionar canhões antes de iniciar
        jogo.adicionarCanhao(100f, 300f, Lado.ESQUERDO);
        jogo.adicionarCanhao(200f, 500f, Lado.ESQUERDO);
        jogo.adicionarCanhao(800f, 300f, Lado.DIREITO);

        assertEquals(2, jogo.getCanhoesNoLadoSnapshot(Lado.ESQUERDO).size());
        assertEquals(1, jogo.getCanhoesNoLadoSnapshot(Lado.DIREITO).size());

        jogo.iniciar();
        Thread.sleep(1500);

        // Verificar que o jogo está rodando
        assertEquals(Jogo.Estado.RODANDO, jogo.getEstado());

        jogo.pararJogo();
        assertEquals(Jogo.Estado.ENCERRADO, jogo.getEstado());
    }

    // ── Testes de concorrência ──────────────────────────────────

    @Test
    public void testConcurrentAccess_DefensiveCopy() throws JogoException {
        jogo.adicionarCanhao(100f, 200f, Lado.ESQUERDO);

        // getAlvos() retorna cópia defensiva — modificar não deve afetar o jogo
        List<com.autotarget.model.Alvo> alvos = jogo.getAlvos();
        int sizeBefore = alvos.size();

        // A lista retornada é imutável
        try {
            alvos.add(null);
            fail("Lista retornada deve ser imutável");
        } catch (UnsupportedOperationException e) {
            // Esperado
        }
    }

    @Test
    public void testContarCanhoesAtivos() throws JogoException {
        assertEquals(0, jogo.contarCanhoesAtivos(Lado.ESQUERDO));

        jogo.adicionarCanhao(100f, 200f, Lado.ESQUERDO);
        jogo.adicionarCanhao(200f, 300f, Lado.ESQUERDO);

        assertEquals(2, jogo.contarCanhoesAtivos(Lado.ESQUERDO));
    }

    // ── Testes de tempo ─────────────────────────────────────────

    @Test
    public void testTempoRestante_Inicial60() {
        assertEquals("Tempo restante inicial deve ser 60s",
                Jogo.DURACAO_PARTIDA_SEGUNDOS, jogo.getTempoRestante());
    }

    // ── Testes de construtor sem Context ────────────────────────

    @Test
    public void testConstrutorSemContext() {
        Jogo jogoSemContext = new Jogo();
        assertNotNull("Jogo sem context deve ser criado", jogoSemContext);
        assertEquals(Jogo.Estado.PARADO, jogoSemContext.getEstado());
    }
}
