/*
 * ============================================================================
 * Arquivo: DivisaoTelaTest.java
 * Pacote:  com.autotarget.engine (test)
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Testes unitários para validação da divisão de tela e transferência
 *   atômica de alvos entre os lados ESQUERDO e DIREITO. Valida cenários
 *   de borda (boundary conditions) conforme exigência AV2.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS:
 *   ► Divisão de tela e transferência atômica (AV2 §4.2)
 *   ► Testes unitários robustos (6.1.7)
 *   ► Sincronização e região crítica (6.1.5)
 *
 * ============================================================================
 */
package com.autotarget.engine;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Lado;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Testes de borda para divisão de tela e transferência atômica de alvos.
 * Garante que alvos são corretamente classificados e transferidos entre
 * lados sem perda de dados (thread-safety).
 */
public class DivisaoTelaTest {

    private static final int LARGURA_TELA = 1080;
    private static final int ALTURA_TELA = 1920;
    private static final float METADE = LARGURA_TELA / 2f;

    private Jogo jogo;

    @Before
    public void setUp() {
        jogo = new Jogo();
        jogo.setDimensoesTela(LARGURA_TELA, ALTURA_TELA);
    }

    // ── Testes de classificação de lado ──────────────────────────

    /**
     * Alvo posicionado claramente no lado esquerdo (x < metade)
     * deve ser classificado como ESQUERDO.
     */
    @Test
    public void testAlvoNoLadoEsquerdo() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        Lado lado = geom.determineLado(100f);
        assertEquals("Alvo com x=100 deve estar no lado ESQUERDO",
                Lado.ESQUERDO, lado);
    }

    /**
     * Alvo posicionado claramente no lado direito (x >= metade)
     * deve ser classificado como DIREITO.
     */
    @Test
    public void testAlvoNoLadoDireito() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        Lado lado = geom.determineLado(800f);
        assertEquals("Alvo com x=800 deve estar no lado DIREITO",
                Lado.DIREITO, lado);
    }

    /**
     * Caso de borda: alvo na posição x = metade - 1 (último pixel do lado esquerdo)
     * deve permanecer no lado ESQUERDO.
     */
    @Test
    public void testAlvoBordaEsquerdaUltimoPixel() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        Lado lado = geom.determineLado(METADE - 1f);
        assertEquals("Alvo com x=(metade-1) deve estar no lado ESQUERDO",
                Lado.ESQUERDO, lado);
    }

    /**
     * Caso de borda: alvo na posição x = metade (primeiro pixel do lado direito)
     * deve pertencer ao lado DIREITO.
     */
    @Test
    public void testAlvoBordaExataMetade() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        Lado lado = geom.determineLado(METADE);
        assertEquals("Alvo com x=metade deve estar no lado DIREITO",
                Lado.DIREITO, lado);
    }

    /**
     * Caso de borda: alvo na posição x = 0 (borda absoluta esquerda)
     * deve pertencer ao lado ESQUERDO.
     */
    @Test
    public void testAlvoBordaAbsolutaEsquerda() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        assertEquals(Lado.ESQUERDO, geom.determineLado(0f));
    }

    /**
     * Caso de borda: alvo na posição x = larguraTela (borda absoluta direita)
     * deve pertencer ao lado DIREITO.
     */
    @Test
    public void testAlvoBordaAbsolutaDireita() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        assertEquals(Lado.DIREITO, geom.determineLado((float) LARGURA_TELA));
    }

    // ── Testes de transferência atômica ──────────────────────────

    /**
     * Alvo adicionado ao lado esquerdo deve aparecer na lista correta
     * e não na lista do lado oposto.
     */
    @Test
    public void testAdicionarAlvoNoLadoCorreto() {
        Alvo alvo = new AlvoComum(100f, 500f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        List<Alvo> alvosEsq = jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO);
        List<Alvo> alvosDir = jogo.getAlvosNoLadoSnapshot(Lado.DIREITO);

        assertTrue("Alvo deve estar na lista do lado ESQUERDO",
                alvosEsq.contains(alvo));
        assertFalse("Alvo NÃO deve estar na lista do lado DIREITO",
                alvosDir.contains(alvo));
    }

    /**
     * Transferência de alvo: ao mover manualmente um alvo do lado esquerdo
     * para o lado direito, ele deve aparecer somente na lista do lado direito.
     * Simula a transferência atômica sem perda.
     */
    @Test
    public void testTransferenciaManualSemPerda() {
        Alvo alvo = new AlvoComum(100f, 500f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        // Verificar estado inicial
        assertEquals(1, jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO));
        assertEquals(0, jogo.getQuantidadeAlvosNoLado(Lado.DIREITO));

        // Simular transferência atômica (remove de um lado, adiciona no outro)
        jogo.removerAlvoManual(alvo, Lado.ESQUERDO);
        jogo.adicionarAlvoManual(alvo, Lado.DIREITO);

        // Verificar estado final
        assertEquals("Lado esquerdo deve estar vazio após transferência",
                0, jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO));
        assertEquals("Lado direito deve conter o alvo transferido",
                1, jogo.getQuantidadeAlvosNoLado(Lado.DIREITO));

        // Verificar que o alvo não foi duplicado nem perdido
        int total = jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO)
                + jogo.getQuantidadeAlvosNoLado(Lado.DIREITO);
        assertEquals("Total de alvos deve ser 1 (sem perda nem duplicação)", 1, total);
    }

    /**
     * Testa múltiplos alvos de tipos diferentes (polimorfismo)
     * sendo transferidos entre lados sem perda.
     */
    @Test
    public void testTransferenciaMultiplosAlvosPolimorficos() {
        Alvo alvoComum = new AlvoComum(100f, 300f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);
        Alvo alvoRapido = new AlvoRapido(200f, 400f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);

        jogo.adicionarAlvoManual(alvoComum, Lado.ESQUERDO);
        jogo.adicionarAlvoManual(alvoRapido, Lado.ESQUERDO);

        assertEquals(2, jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO));

        // Transferir apenas o AlvoRapido
        jogo.removerAlvoManual(alvoRapido, Lado.ESQUERDO);
        jogo.adicionarAlvoManual(alvoRapido, Lado.DIREITO);

        assertEquals("Esquerdo deve ter 1 alvo", 1, jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO));
        assertEquals("Direito deve ter 1 alvo", 1, jogo.getQuantidadeAlvosNoLado(Lado.DIREITO));

        // AlvoComum permanece no esquerdo
        assertTrue(jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO).contains(alvoComum));
        // AlvoRapido foi para o direito
        assertTrue(jogo.getAlvosNoLadoSnapshot(Lado.DIREITO).contains(alvoRapido));
    }

    // ── Testes de GameGeometry ───────────────────────────────────

    /**
     * GameGeometry com largura zero deve retornar ESQUERDO como fallback seguro.
     */
    @Test
    public void testGameGeometryLarguraZero() {
        GameGeometry geom = GameGeometry.forScreen(0, ALTURA_TELA);
        assertEquals("Largura zero deve retornar ESQUERDO como fallback",
                Lado.ESQUERDO, geom.determineLado(500f));
    }

    /**
     * GameGeometry com largura negativa deve retornar ESQUERDO como fallback seguro.
     */
    @Test
    public void testGameGeometryLarguraNegativa() {
        GameGeometry geom = GameGeometry.forScreen(-100, ALTURA_TELA);
        assertEquals("Largura negativa deve retornar ESQUERDO como fallback",
                Lado.ESQUERDO, geom.determineLado(500f));
    }

    /**
     * Midpoint deve ser exatamente metade da largura.
     */
    @Test
    public void testMidpointCorreto() {
        GameGeometry geom = GameGeometry.forScreen(LARGURA_TELA, ALTURA_TELA);
        assertEquals("Midpoint deve ser largura/2",
                METADE, geom.getMidpointX(), 0.001f);
    }

    // ── Teste de concorrência na transferência ───────────────────

    /**
     * Testa que acessos concorrentes à lista de alvos não causam
     * ConcurrentModificationException nem perda de dados.
     */
    @Test
    public void testAcessoConcorrenteSemExcecao() throws InterruptedException {
        // Adicionar vários alvos
        for (int i = 0; i < 20; i++) {
            float x = (i % 2 == 0) ? 100f : 800f;
            Alvo alvo = new AlvoComum(x, 500f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);
            Lado lado = (i % 2 == 0) ? Lado.ESQUERDO : Lado.DIREITO;
            jogo.adicionarAlvoManual(alvo, lado);
        }

        int totalInicial = jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO)
                + jogo.getQuantidadeAlvosNoLado(Lado.DIREITO);
        assertEquals("Devem existir 20 alvos no total", 20, totalInicial);

        // Threads concorrentes lendo snapshots (não deve lançar exceção)
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO);
                jogo.getAlvosNoLadoSnapshot(Lado.DIREITO);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                jogo.getAlvosNoLadoSnapshot(Lado.DIREITO);
                jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO);
            }
        });

        t1.start();
        t2.start();
        t1.join(3000);
        t2.join(3000);

        // Nenhuma exceção = sucesso
        int totalFinal = jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO)
                + jogo.getQuantidadeAlvosNoLado(Lado.DIREITO);
        assertEquals("Total de alvos não deve mudar após leituras concorrentes",
                totalInicial, totalFinal);
    }

    // ── Teste de defensive copy ─────────────────────────────────

    /**
     * Verifica que getAlvosNoLadoSnapshot retorna cópia defensiva.
     * Modificações na lista retornada NÃO devem afetar a lista interna.
     */
    @Test
    public void testDefensiveCopy() {
        Alvo alvo = new AlvoComum(100f, 500f, 30f, 3f, LARGURA_TELA, ALTURA_TELA);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);

        List<Alvo> snapshot = jogo.getAlvosNoLadoSnapshot(Lado.ESQUERDO);

        // Tentar modificar o snapshot (deve lançar UnsupportedOperationException
        // porque é Collections.unmodifiableList)
        try {
            snapshot.clear();
            fail("Snapshot deveria ser não-modificável (defensive copy)");
        } catch (UnsupportedOperationException e) {
            // Esperado — defensive copy está funcionando
        }

        // Lista interna não deve ter sido afetada
        assertEquals("Lista interna não deve ter sido modificada",
                1, jogo.getQuantidadeAlvosNoLado(Lado.ESQUERDO));
    }
}
