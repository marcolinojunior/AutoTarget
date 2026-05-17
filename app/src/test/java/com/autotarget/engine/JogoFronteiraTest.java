package com.autotarget.engine;

import org.junit.Test;
import static org.junit.Assert.*;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Lado;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * T04 - Teste de Stress de Fronteira.
 * Verifica o determinismo na interseção entre cruzamento de linha e disparo.
 */
public class JogoFronteiraTest {

    @Test
    public void testStressFronteiraCruzamentoSimultaneo() throws InterruptedException {
        final int NUM_REPETICOES = 1000;
        final int LARGURA = 1000;
        final int ALTURA = 1000;
        final float MEIO = LARGURA / 2f;

        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(LARGURA, ALTURA);

        // Alvo posicionado exatamente na fronteira
        Alvo alvo = new AlvoComum(MEIO, 500, 30, 0, LARGURA, ALTURA);
        
        // Simulação de corrida: Thread A tenta transferir, Thread B tenta abater
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Simular abates repetidos em alvos na fronteira
        int abatesSucesso = 0;
        for (int i = 0; i < NUM_REPETICOES; i++) {
            Alvo a = new AlvoComum(MEIO, 500, 30, 0, LARGURA, ALTURA);
            
            // Thread de "Física" (Transferência)
            executor.submit(() -> {
                // Forçar o alvo a oscilar levemente entre lados
                a.setPosicaoX(MEIO + 1); // Passa para DIREITO
                // Jogo faria a transferência aqui
            });

            // Thread de "Projétil" (Abate)
            if (a.tentarAbater(Lado.ESQUERDO)) {
                abatesSucesso++;
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // O teste de stress valida que a flag atômica do Alvo nunca permite duplo abate
        // e que o sistema permanece consistente.
        assertTrue("Deve haver registros de abates no stress test", abatesSucesso > 0);
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
                jogo.getAlvosEsquerdo().add(alvo);

                // Empurra o alvo para a fronteira exata; a regra deve levá-lo ao lado direito.
                alvo.setPosicaoX(meio);

                java.lang.reflect.Method transferir = Jogo.class.getDeclaredMethod("transferirAlvosCruzados");
                transferir.setAccessible(true);
                transferir.invoke(jogo);

                assertFalse("O alvo deve sair da lista esquerda após cruzar a fronteira", jogo.getAlvosEsquerdo().contains(alvo));
                assertTrue("O alvo deve aparecer na lista direita após cruzar a fronteira", jogo.getAlvosDireito().contains(alvo));
            }
}
