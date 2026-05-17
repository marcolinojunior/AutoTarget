package com.autotarget.engine;

import org.junit.Test;
import static org.junit.Assert.*;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JogoFronteiraTest {

    @Test
    public void testTransferenciaAtomatica() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        
        Alvo alvo1 = new AlvoComum(490, 500, 30, 0, 1000, 1000); // Lado esquerdo
        jogo.getAlvosEsquerdo().add(alvo1);
        
        Method transferirMethod = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferirMethod.setAccessible(true);
        
        // Simular movimento para o lado direito via reflexão
        java.lang.reflect.Field xField = Alvo.class.getDeclaredField("x");
        xField.setAccessible(true);
        xField.set(alvo1, 510f); 
        
        transferirMethod.invoke(jogo);
        
        assertEquals("Alvo não deveria estar na lista esquerda", 0, jogo.getAlvosEsquerdo().size());
        assertEquals("Alvo deveria estar na lista direita", 1, jogo.getAlvosDireito().size());
    }

    @Test
    public void testBordaXIgualMeioVaiParaDireitaSemDuplicar() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);

        Alvo alvo = new AlvoComum(499, 500, 30, 0, 1000, 1000);
        jogo.getAlvosEsquerdo().add(alvo);

        Method transferirMethod = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferirMethod.setAccessible(true);

        java.lang.reflect.Field xField = Alvo.class.getDeclaredField("x");
        xField.setAccessible(true);
        xField.set(alvo, 500f); // exatamente na divisória

        transferirMethod.invoke(jogo);

        assertFalse("Alvo não pode permanecer no lado esquerdo", jogo.getAlvosEsquerdo().contains(alvo));
        assertTrue("Alvo deve ser transferido para o lado direito no caso x == meio", jogo.getAlvosDireito().contains(alvo));
        assertEquals("Não pode haver alvo duplicado nas listas", 1,
                (jogo.getAlvosEsquerdo().contains(alvo) ? 1 : 0) + (jogo.getAlvosDireito().contains(alvo) ? 1 : 0));
    }

    @Test
    public void testSimultaneidadeColisaoEFronteira() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        
        Alvo alvo1 = new AlvoComum(490, 500, 30, 0, 1000, 1000);
        jogo.getAlvosEsquerdo().add(alvo1);
        
        Method transferirMethod = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferirMethod.setAccessible(true);
        
        java.lang.reflect.Field xField = Alvo.class.getDeclaredField("x");
        xField.setAccessible(true);
        xField.set(alvo1, 510f); // Cruza a linha
        alvo1.setAtivo(false); // Colide/Abatido no exato instante
        
        // Transferência ocorre
        transferirMethod.invoke(jogo);
        
        // Física processa a colisão
        int destruidos = jogo.verificarColisoes();
        
        assertEquals("Um alvo deve ter sido reportado como destruído", 1, destruidos);
        assertEquals("Lista esquerda deve estar vazia após colisão", 0, jogo.getAlvosEsquerdo().size());
        assertEquals("Lista direita deve estar vazia após colisão", 0, jogo.getAlvosDireito().size());
        
        // Como cruzou para a direita, a pontuação vai para o lado direito
        assertEquals("A pontuação direita deve computar o alvo abatido na sua fronteira", 5, jogo.getPontuacaoDireito());
        assertEquals("A pontuação esquerda deve permanecer 0", 0, jogo.getPontuacaoEsquerdo());
    }
    
    @Test
    public void testRaceConditionTransferenciaColisao() throws Exception {
        final Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        
        final Alvo alvo1 = new AlvoComum(500, 500, 30, 0, 1000, 1000);
        jogo.getAlvosEsquerdo().add(alvo1);
        
        final Method transferirMethod = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferirMethod.setAccessible(true);
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Thread 1: Movimento + Transferência
        executor.submit(() -> {
            try {
                latch.await();
                java.lang.reflect.Field xField = Alvo.class.getDeclaredField("x");
                xField.setAccessible(true);
                xField.set(alvo1, 501f); // Cruza a linha
                synchronized(jogo.getCollisionLock()) {
                    transferirMethod.invoke(jogo);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        // Thread 2: Disparo + Colisão
        executor.submit(() -> {
            try {
                latch.await();
                alvo1.setAtivo(false); // Canhão abateu
                jogo.verificarColisoes();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        // Start them roughly simultaneously
        latch.countDown();
        
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        
        // Verification: The target should be removed from both sides without throwing ConcurrentModificationException
        // And exactly one side should have received 5 points.
        assertEquals("Alvo não pode sobrar na esquerda", 0, jogo.getAlvosEsquerdo().size());
        assertEquals("Alvo não pode sobrar na direita", 0, jogo.getAlvosDireito().size());
        
        int pontuacaoTotal = jogo.getPontuacaoEsquerdo() + jogo.getPontuacaoDireito();
        assertEquals("A pontuação total distribuída deve ser exatamente 5 pontos", 5, pontuacaoTotal);
    }

    @Test
    public void testCruzamentoSimultaneoComDisparoNaoOrfaNemDuplicaAlvo() throws Exception {
        final Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);

        final Alvo alvo = new AlvoComum(499, 500, 30, 0, 1000, 1000);
        jogo.getAlvosEsquerdo().add(alvo);

        final Canhao canhao = new Canhao(120, 500, Lado.ESQUERDO, jogo.getAlvosEsquerdo(),
                jogo.getCollisionLock(), 1000, 1000, jogo);

        final Method transferirMethod = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferirMethod.setAccessible(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                latch.await();
                java.lang.reflect.Field xField = Alvo.class.getDeclaredField("x");
                xField.setAccessible(true);
                xField.set(alvo, 501f);
                transferirMethod.invoke(jogo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                canhao.disparar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        int ocorrencias = (jogo.getAlvosEsquerdo().contains(alvo) ? 1 : 0)
                + (jogo.getAlvosDireito().contains(alvo) ? 1 : 0);
        assertEquals("Alvo deve existir em exatamente uma lista após corrida disparo/fronteira", 1, ocorrencias);

        canhao.pararCanhao();
        canhao.interrupt();
    }
}
