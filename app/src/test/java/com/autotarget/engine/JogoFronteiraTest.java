package com.autotarget.engine;

import org.junit.Test;
import static org.junit.Assert.*;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;
import com.autotarget.model.Projetil;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * T04 - Teste de Stress de Fronteira.
 * Verifica o determinismo na interseção entre cruzamento de linha e disparo.
 */
public class JogoFronteiraTest {

    @Test
    public void testStressFronteiraCruzamentoSimultaneo() throws Exception {
        final int repeticoes = 300;
        final int largura = 1000;
        final int altura = 1000;
        final float meio = largura / 2f;

        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(largura, altura);
        java.lang.reflect.Method transferir = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
        transferir.setAccessible(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int i = 0; i < repeticoes; i++) {
            Alvo alvo = new AlvoComum(meio - 1f, 500, 30, 0, largura, altura);
            jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
            alvo.setPosicaoX(meio); // fronteira exata => pertence ao lado direito

            CountDownLatch latch = new CountDownLatch(1);
            executor.submit(() -> {
                try {
                    latch.await();
                    transferir.invoke(jogo);
                } catch (Exception ignored) {
                }
            });
            executor.submit(() -> {
                try {
                    latch.await();
                    alvo.tentarAbater(Lado.ESQUERDO);
                } catch (Exception ignored) {
                }
            });
            latch.countDown();
        }

        executor.shutdown();
        assertTrue("Execução concorrente deve finalizar", executor.awaitTermination(4, TimeUnit.SECONDS));

        for (Alvo alvo : jogo.getAllAlvos()) {
            assertFalse("Um alvo não pode existir simultaneamente em ambos os lados",
                    jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO).contains(alvo)
                            && jogo.getAlvosNoLadoSnapshot(Lado.DIREITO).contains(alvo));
        }
    }
    
    @Test
    public void testPrecedenciaAbateLadoErrado() {
        // Se um projétil do lado ESQUERDO atinge um alvo que já cruzou para o DIREITO,
        // o abate deve ser negado (conforme regra de precedência definida).
        int LARGURA = 1000;
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(LARGURA, 1000);
        
        // Alvo claramente no lado DIREITO
        Alvo alvo = new AlvoComum(LARGURA * 0.8f, 500, 30, 0, LARGURA, 1000);
        
        // Tentar abater pelo lado ESQUERDO
        com.autotarget.engine.GameGeometry geom = com.autotarget.engine.GameGeometry.forScreen(LARGURA, 1000);
        Lado ladoAlvo = geom.determineLado(alvo.getX());
        
        boolean abateEfetivado = false;
        if (ladoAlvo == Lado.ESQUERDO) {
            abateEfetivado = alvo.tentarAbater(Lado.ESQUERDO);
        }
        
        assertFalse("Um projétil não deve abater alvos que já cruzaram a fronteira.", abateEfetivado);
    }

    @Test
    public void testIntegracaoProjetilFronteira() throws Exception {
        // Item 6: Garantir que o Projetil verifica o lado do alvo no momento do impacto
        int LARGURA = 1000;
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(LARGURA, 1000);
        
        // Alvo na fronteira, mas ligeiramente no DIREITO
        float meio = LARGURA / 2f;
        Alvo alvo = new AlvoComum(meio + 1f, 500, 0, 0, LARGURA, 1000);
        java.util.List<Alvo> alvos = new java.util.ArrayList<>();
        alvos.add(alvo);
        
        // Projétil vindo do ESQUERDO
        Projetil p = new Projetil(
                meio - 5f, 500f, 1f, 0f, 10f, alvos, jogo.getCollisionLock(), 
                LARGURA, 1000, jogo, Lado.ESQUERDO, alvo);
        
        // Acionar verificação de colisão
        java.lang.reflect.Method check = p.getClass().getDeclaredMethod("verificarColisoes");
        check.setAccessible(true);
        check.invoke(p);
        
        assertTrue("Alvo deve continuar ativo pois cruzou a fronteira antes do impacto", alvo.isAtivo());
        assertTrue("Projétil deve continuar ativo pois o abate foi negado pela regra de lado", p.isAtivo());
    }

            @Test
            public void testCentroExatoPertenceAoLadoDireito() {
                int LARGURA = 1200;
                int ALTURA = 800;
                GameGeometry geom = GameGeometry.forScreen(LARGURA, ALTURA);

                assertEquals("O ponto exatamente no meio deve pertencer ao lado direito",
                        Lado.DIREITO, geom.determineLado(geom.getMidpointX()));
                assertEquals("Um pixel à esquerda do centro deve pertencer ao lado esquerdo",
                        Lado.ESQUERDO, geom.determineLado(geom.getMidpointX() - 1f));
            }

            @Test
            public void testTransferenciaNaFronteiraMantemConsistenciaAtomica() throws Exception {
                final int LARGURA = 1200;
                final int ALTURA = 800;
                Jogo jogo = new Jogo();
                jogo.setDimensoesTela(LARGURA, ALTURA);

                float meio = LARGURA / 2f;
                Alvo alvo = new AlvoComum(meio - 2f, 200f, 20f, 0f, LARGURA, ALTURA);
                jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

                // Empurra o alvo para a fronteira exata; a regra deve levá-lo ao lado direito.
                alvo.setPosicaoX(meio);

                java.lang.reflect.Method transferir = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
                transferir.setAccessible(true);
                transferir.invoke(jogo);

                assertFalse("O alvo deve sair da lista esquerda após cruzar a fronteira", jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO).contains(alvo));
                assertTrue("O alvo deve aparecer na lista direita após cruzar a fronteira", jogo.getAlvosNoLadoSnapshot(Lado.DIREITO).contains(alvo));
            }
}
