package com.autotarget.engine;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JogoStressConcorrenciaTest {

    @Test
    public void testStressTransferenciaColisaoSpawnEAjusteFrota() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1200, 800);

        try {
            jogo.adicionarCanhao(120, 200, Lado.ESQUERDO);
            jogo.adicionarCanhao(220, 300, Lado.ESQUERDO);
            jogo.adicionarCanhao(980, 200, Lado.DIREITO);
            jogo.adicionarCanhao(1080, 300, Lado.DIREITO);
        } catch (JogoException e) {
            throw new AssertionError("Falha ao preparar canhões para teste de stress", e);
        }

        Method spawnarAlvo = Jogo.class.getDeclaredMethod("spawnarAlvo");
        spawnarAlvo.setAccessible(true);
        Method transferir = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferir.setAccessible(true);

        AtomicReference<Throwable> erro = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < 120; i++) {
                    spawnarAlvo.invoke(jogo);
                }
            } catch (Throwable t) {
                erro.compareAndSet(null, t);
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < 200; i++) {
                    transferir.invoke(jogo);
                }
            } catch (Throwable t) {
                erro.compareAndSet(null, t);
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < 200; i++) {
                    if (!jogo.getAlvosEsquerdo().isEmpty()) {
                        jogo.getAlvosEsquerdo().get(0).setAtivo(false);
                    }
                    if (!jogo.getAlvosDireito().isEmpty()) {
                        jogo.getAlvosDireito().get(0).setAtivo(false);
                    }
                    jogo.verificarColisoes();
                }
            } catch (Throwable t) {
                erro.compareAndSet(null, t);
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < 120; i++) {
                    Lado lado = (i % 2 == 0) ? Lado.ESQUERDO : Lado.DIREITO;
                    float x = (lado == Lado.ESQUERDO) ? 60 + (i % 5) * 40 : 1140 - (i % 5) * 40;
                    float y = 120 + (i % 6) * 80;
                    try {
                        jogo.adicionarCanhao(x, y, lado);
                    } catch (JogoException ignored) {
                        // Ignora limite de canhões/energia neste stress test.
                    }
                    if (lado == Lado.ESQUERDO && !jogo.getCanhoesEsquerdo().isEmpty()) {
                        Canhao c = jogo.getCanhoesEsquerdo().get(0);
                        jogo.removerCanhao(c);
                    } else if (lado == Lado.DIREITO && !jogo.getCanhoesDireito().isEmpty()) {
                        Canhao c = jogo.getCanhoesDireito().get(0);
                        jogo.removerCanhao(c);
                    }
                }
            } catch (Throwable t) {
                erro.compareAndSet(null, t);
            }
        });

        latch.countDown();
        executor.shutdown();
        assertTrue("Threads do stress test devem finalizar", executor.awaitTermination(10, TimeUnit.SECONDS));
        assertNull("Não deve haver exceções em cenário concorrente", erro.get());

        for (Alvo alvo : jogo.getAlvosEsquerdo()) {
            assertFalse("Alvo não pode estar simultaneamente nos dois lados", jogo.getAlvosDireito().contains(alvo));
        }

        for (Alvo alvo : jogo.getAllAlvos()) {
            alvo.setAtivo(false);
            alvo.interrupt();
        }
        for (Canhao canhao : jogo.getAllCanhoes()) {
            canhao.pararCanhao();
            canhao.interrupt();
        }
    }
}
