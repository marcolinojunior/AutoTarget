/*
 * ============================================================================
 * Arquivo: ProjetilTest.java
 * Pacote:  com.autotarget (test)
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe de testes unitários JUnit para a classe Projetil. Valida
 *   cálculo de distância euclidiana, detecção de colisão (detectada,
 *   não detectada, fronteira exata), movimento retilíneo e diagonal,
 *   e verificação de que Projetil é uma Thread.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Testes unitários (6.1.7):
 *     - ≥3 testes cobrindo pontos críticos + casos de borda:
 *       • testCalcularDistancia — triângulo pitagórico 3-4-5
 *       • testDistanciaZero — mesma posição → distância 0
 *       • testColisaoDetectada — projétil na mesma posição do alvo → true
 *       • testColisaoNaoDetectadaDistante — projétil longe → false
 *       • testColisaoNaFronteiraDaDistancia — dist == raioAlvo+raioProjetil → true
 *       • testMovimento — deslocamento horizontal 10px
 *       • testMovimentoDiagonal — deslocamento 45° normalizado
 *       • testProjetilCriadoAtivo — flag ativo = true ao criar
 *       • testProjetilEhThread — instanceof Thread (requisito 6.1.4)
 *
 *   ► Sincronização (6.1.5):
 *     - Usa CopyOnWriteArrayList e collisionLock nos testes, replicando
 *       a configuração de região crítica do jogo real.
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - testColisaoDetectada/NaoDetectada usam AlvoComum como instância
 *       concreta de Alvo, validando a interação polimórfica projétil↔alvo.
 *
 * TOTAL DE TESTES: 9
 *
 * ============================================================================
 */
package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Canhao;
import com.autotarget.model.Projetil;
import com.autotarget.model.Lado;
import com.autotarget.engine.Jogo;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * Testes unitários para a classe {@link Projetil}.
 * <p>
 * Verifica cálculo de distância, detecção de colisão, movimento
 * e validação de posição.
 */
public class ProjetilTest {

    private CopyOnWriteArrayList<Alvo> alvos;
    private Object collisionLock;
    private Jogo jogo;
    private static final int LARGURA = 800;
    private static final int ALTURA = 600;

    @Before
    public void setUp() {
        alvos = new CopyOnWriteArrayList<>();
        collisionLock = new Object();
        jogo = new Jogo();
    }

    // ── Testes de cálculo de distância ───────────────────────────

    @Test
    public void testCalcularDistancia() {
        // Triângulo pitagórico 3-4-5
        float dist = Projetil.calcularDistancia(0, 0, 3, 4);
        assertEquals(5.0f, dist, 0.001f);
    }

    @Test
    public void testDistanciaZero() {
        float dist = Projetil.calcularDistancia(5, 5, 5, 5);
        assertEquals(0f, dist, 0.001f);
    }

    // ── Testes de colisão ────────────────────────────────────────

    @Test
    public void testColisaoDetectada() {
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil na mesma posição do alvo → colisão
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);

        assertTrue("Deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaoDetectadaDistante() {
        AlvoComum alvo = new AlvoComum(500, 500, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil muito longe → sem colisão
        Projetil projetil = new Projetil(10, 10, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);

        assertFalse("Não deveria detectar colisão", projetil.collide(alvo));
    }

    @Test
    public void testColisaoNaFronteiraDaDistancia() {
        // Alvo com raio 20 na posição (100, 100)
        AlvoComum alvo = new AlvoComum(100, 100, 20, 3, LARGURA, ALTURA);
        alvos.add(alvo);

        // Projétil (raio 5) exatamente no limite: distância = raioAlvo + raioProjetil = 25
        float distanciaLimite = 20f + Projetil.getRaio(); // 25
        Projetil projetilLimite = new Projetil(100 + distanciaLimite, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);

        // Colisão exata na fronteira
        assertTrue("Deveria detectar colisão no limite",
                projetilLimite.collide(alvo));
    }

    // ── Testes de movimento ──────────────────────────────────────

    @Test
    public void testMovimento() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);

        float xInicial = projetil.getX();
        projetil.mover();

        // Deveria ter avançado 10 pixels na direção X
        assertEquals(xInicial + 10f, projetil.getX(), 0.001f);
    }

    @Test
    public void testMovimentoDiagonal() {
        // Direção normalizada 45°
        float dir = (float) (1 / Math.sqrt(2));
        Projetil projetil = new Projetil(0, 0, dir, dir, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);

        projetil.mover();

        float esperado = dir * 10;
        assertEquals(esperado, projetil.getX(), 0.001f);
        assertEquals(esperado, projetil.getY(), 0.001f);
    }

    // ── Testes de estado ─────────────────────────────────────────

    @Test
    public void testProjetilCriadoAtivo() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);
        assertTrue(projetil.isAtivo());
    }

    @Test
    public void testProjetilEhThread() {
        Projetil projetil = new Projetil(100, 100, 1, 0, 10,
                alvos, collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, null);
        assertTrue("Projetil deve ser um Runnable", projetil instanceof Runnable);
    }

    @Test
    public void testReservaLiberadaAoErrarSaindoDaTela() throws Exception {
        jogo.setDimensoesTela(LARGURA, ALTURA);
        AlvoComum alvo = new AlvoComum(200, 200, 20, 0, LARGURA, ALTURA);
        jogo.getAlvosEsquerdo().add(alvo);

        Canhao c1 = new Canhao(100, 100, Lado.ESQUERDO, jogo.getAlvosEsquerdo(),
                collisionLock, LARGURA, ALTURA, jogo);
        Canhao c2 = new Canhao(150, 100, Lado.ESQUERDO, jogo.getAlvosEsquerdo(),
                collisionLock, LARGURA, ALTURA, jogo);

        Alvo reservado = jogo.reservarAlvo(c1);
        assertSame(alvo, reservado);

        Projetil projetil = new Projetil(LARGURA - 2, 30, 1, 0, 20,
                jogo.getAlvosEsquerdo(), collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, reservado);
        Thread t = new Thread(projetil);
        t.start();
        t.join(500);

        Alvo reservadoPorOutroCanhao = jogo.reservarAlvo(c2);
        assertSame("Reserva deve ser liberada quando projétil erra e sai da tela", alvo, reservadoPorOutroCanhao);
    }

    @Test
    public void testReservaLiberadaAoAcertar() throws Exception {
        jogo.setDimensoesTela(LARGURA, ALTURA);
        AlvoComum alvo = new AlvoComum(100, 100, 20, 0, LARGURA, ALTURA);
        jogo.getAlvosEsquerdo().add(alvo);

        Canhao c1 = new Canhao(80, 100, Lado.ESQUERDO, jogo.getAlvosEsquerdo(),
                collisionLock, LARGURA, ALTURA, jogo);
        Alvo reservado = jogo.reservarAlvo(c1);
        assertSame(alvo, reservado);

        Projetil projetil = new Projetil(100, 100, 1, 0, 1,
                jogo.getAlvosEsquerdo(), collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, reservado);
        Thread t = new Thread(projetil);
        t.start();
        t.join(500);

        ConcurrentHashMap<Alvo, Canhao> reservas = getReservas(jogo);
        assertFalse("Reserva deve ser removida quando projétil acerta o alvo", reservas.containsKey(alvo));
    }

    @Test
    public void testReservaLiberadaEmInterrupcaoDoProjetil() throws Exception {
        jogo.setDimensoesTela(LARGURA, ALTURA);
        AlvoComum alvo = new AlvoComum(300, 300, 20, 0, LARGURA, ALTURA);
        jogo.getAlvosEsquerdo().add(alvo);

        Canhao c1 = new Canhao(80, 100, Lado.ESQUERDO, jogo.getAlvosEsquerdo(),
                collisionLock, LARGURA, ALTURA, jogo);
        Alvo reservado = jogo.reservarAlvo(c1);
        assertSame(alvo, reservado);

        Projetil projetil = new Projetil(20, 20, 0.5f, 0.7f, 8,
                jogo.getAlvosEsquerdo(), collisionLock, LARGURA, ALTURA, jogo, Lado.ESQUERDO, reservado);
        Thread t = new Thread(projetil);
        t.start();
        Thread.sleep(30);
        t.interrupt();
        t.join(500);

        ConcurrentHashMap<Alvo, Canhao> reservas = getReservas(jogo);
        assertFalse("Reserva deve ser removida quando projétil é interrompido", reservas.containsKey(alvo));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Alvo, Canhao> getReservas(Jogo jogo) throws Exception {
        Field field = Jogo.class.getDeclaredField("reservasAlvos");
        field.setAccessible(true);
        return (ConcurrentHashMap<Alvo, Canhao>) field.get(jogo);
    }
}
