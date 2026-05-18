package com.autotarget.util;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Testes unitários para {@link EnergyManager}.
 * Valida operações atômicas, limites, e segurança em concorrência.
 */
public class EnergyManagerTest {

    private static final float INITIAL = 100f;
    private static final float MAX = 100f;
    private static final float DELTA = 0.001f;

    private EnergyManager em;

    @Before
    public void setUp() {
        em = new EnergyManager(INITIAL, MAX);
    }

    // ── Testes básicos de inicialização ─────────────────────────

    @Test
    public void testInitialValue() {
        assertEquals("Valor inicial deve ser 100", INITIAL, em.get(), DELTA);
    }

    @Test
    public void testMaxValueNotExceeded() {
        em.add(50f);
        assertEquals("Não deve exceder o máximo", MAX, em.get(), DELTA);
    }

    // ── Testes de add() ─────────────────────────────────────────

    @Test
    public void testAddPositive() {
        em.add(10f);
        assertEquals("100 + 10 = 110 → capped at 100", MAX, em.get(), DELTA);

        EnergyManager em2 = new EnergyManager(50f, 100f);
        em2.add(20f);
        assertEquals("50 + 20 = 70", 70f, em2.get(), DELTA);
    }

    @Test
    public void testAddZero() {
        em.add(0f);
        assertEquals("Add 0 não deve alterar", INITIAL, em.get(), DELTA);
    }

    @Test
    public void testAddNegative() {
        em.add(-10f);
        assertEquals("Add negativo deve ser ignorado", INITIAL, em.get(), DELTA);
    }

    // ── Testes de remove() ──────────────────────────────────────

    @Test
    public void testRemoveSuccess() {
        boolean result = em.remove(30f);
        assertTrue("Remoção deve suceder com energia suficiente", result);
        assertEquals("100 - 30 = 70", 70f, em.get(), DELTA);
    }

    @Test
    public void testRemoveExactAmount() {
        boolean result = em.remove(100f);
        assertTrue("Remoção exata deve suceder", result);
        assertEquals("100 - 100 = 0", 0f, em.get(), DELTA);
    }

    @Test
    public void testRemoveInsufficient() {
        boolean result = em.remove(150f);
        assertFalse("Remoção deve falhar com energia insuficiente", result);
        assertEquals("Valor não deve mudar em falha", INITIAL, em.get(), DELTA);
    }

    @Test
    public void testRemoveZero() {
        boolean result = em.remove(0f);
        assertTrue("Remoção de 0 deve retornar true", result);
        assertEquals("Valor não deve mudar", INITIAL, em.get(), DELTA);
    }

    @Test
    public void testRemoveNegative() {
        boolean result = em.remove(-10f);
        assertTrue("Remoção negativa deve retornar true (ignorada)", result);
        assertEquals("Valor não deve mudar", INITIAL, em.get(), DELTA);
    }

    // ── Testes de tryRemove() ───────────────────────────────────

    @Test
    public void testTryRemoveSuccess() {
        assertTrue("tryRemove com energia suficiente", em.tryRemove(50f));
        assertEquals(50f, em.get(), DELTA);
    }

    @Test
    public void testTryRemoveFailure() {
        assertFalse("tryRemove com energia insuficiente", em.tryRemove(200f));
        assertEquals(INITIAL, em.get(), DELTA);
    }

    // ── Testes de set() ─────────────────────────────────────────

    @Test
    public void testSetWithinRange() {
        em.set(42f);
        assertEquals(42f, em.get(), DELTA);
    }

    @Test
    public void testSetAboveMax() {
        em.set(999f);
        assertEquals("set acima do max deve ser clamped", MAX, em.get(), DELTA);
    }

    @Test
    public void testSetNegative() {
        em.set(-10f);
        assertEquals("set negativo deve ser clamped a 0", 0f, em.get(), DELTA);
    }

    // ── Testes de hasSufficient() ───────────────────────────────

    @Test
    public void testHasSufficientTrue() {
        assertTrue("100 >= 50", em.hasSufficient(50f));
    }

    @Test
    public void testHasSufficientExact() {
        assertTrue("100 >= 100", em.hasSufficient(100f));
    }

    @Test
    public void testHasSufficientFalse() {
        assertFalse("100 >= 200", em.hasSufficient(200f));
    }

    // ── Testes de getPercentage() ───────────────────────────────

    @Test
    public void testGetPercentageFull() {
        assertEquals(100f, em.getPercentage(), DELTA);
    }

    @Test
    public void testGetPercentageHalf() {
        em.remove(50f);
        assertEquals(50f, em.getPercentage(), DELTA);
    }

    @Test
    public void testGetPercentageZero() {
        em.remove(100f);
        assertEquals(0f, em.getPercentage(), DELTA);
    }

    // ── Testes de concorrência ──────────────────────────────────

    @Test
    public void testConcurrentRemoves_NoOverConsumption() throws InterruptedException {
        EnergyManager concurrentEm = new EnergyManager(1000f, 1000f);
        int threads = 10;
        int removesPerThread = 50;
        float amountPerRemove = 2f; // 10 × 50 × 2 = 1000

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < removesPerThread; i++) {
                    if (concurrentEm.tryRemove(amountPerRemove)) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        assertTrue("Threads devem completar em 5s", latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        float finalEnergy = concurrentEm.get();
        assertEquals("Energia final deve ser >= 0", 0f, Math.max(0, finalEnergy), DELTA);
        assertEquals("Total de remoções bem-sucedidas × amount = energia consumida",
                successes.get() * amountPerRemove + finalEnergy, 1000f, DELTA);
        assertTrue("Deve ter exatamente 1000/2 = 500 sucessos", successes.get() == 500);
    }

    @Test
    public void testConcurrentAddAndRemove_Consistency() throws InterruptedException {
        EnergyManager concurrentEm = new EnergyManager(100f, 200f);
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    if (threadId % 2 == 0) {
                        concurrentEm.add(1f);
                    } else {
                        concurrentEm.remove(1f);
                    }
                }
                latch.countDown();
            });
        }

        assertTrue("Threads devem completar em 5s", latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        float finalEnergy = concurrentEm.get();
        assertTrue("Energia deve estar entre 0 e 200", finalEnergy >= 0f && finalEnergy <= 200f);
    }

    // ── Testes de sequência de operações ────────────────────────

    @Test
    public void testSequenceOfOperations() {
        assertEquals(100f, em.get(), DELTA);

        em.remove(30f);
        assertEquals(70f, em.get(), DELTA);

        em.remove(50f);
        assertEquals(20f, em.get(), DELTA);

        em.remove(30f); // insuficiente
        assertEquals(20f, em.get(), DELTA); // não muda

        em.add(10f);
        assertEquals(30f, em.get(), DELTA);

        em.add(999f); // excede max
        assertEquals(MAX, em.get(), DELTA);

        em.set(0f);
        assertEquals(0f, em.get(), DELTA);

        em.remove(1f); // insuficiente
        assertEquals(0f, em.get(), DELTA);
    }
}
