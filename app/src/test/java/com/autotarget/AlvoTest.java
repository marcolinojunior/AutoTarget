package com.autotarget;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.AlvoRapido;
import com.autotarget.model.Canhao;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Testes unitários para a hierarquia de classes {@link Alvo}.
 * <p>
 * Verifica polimorfismo (AlvoComum vs AlvoRapido), cálculo de distância,
 * validação de limites, movimento e casos de borda.
 */
public class AlvoTest {

    private static final int LARGURA = 800;
    private static final int ALTURA = 600;
    private List<Canhao> canhoes;
    private Object collisionLock;

    @Before
    public void setUp() {
        canhoes = new ArrayList<>();
        collisionLock = new Object();
    }

    // ── Testes de criação e polimorfismo ──────────────────────────

    @Test
    public void testCriacaoAlvoComum() {
        // Como o teste é feito: Instanciamos a classe AlvoComum e, em seguida, consultamos os valores configurados pelos getters.
        // Condição de Pass: Passa se as posições X/Y, raio, velocidade e estado (ativo) retornarem os mesmos valores que passamos no construtor.
        // Condição de Not Pass: Falha se algum getter retornar um valor diferente ou se a inicialização disparar algum erro imprevisto.
        AlvoComum alvo = new AlvoComum(100, 200, 30, 5, LARGURA, ALTURA, canhoes, collisionLock);

        assertEquals(100f, alvo.getX(), 0.001f);
        assertEquals(200f, alvo.getY(), 0.001f);
        assertEquals(30f, alvo.getRaio(), 0.001f);
        assertEquals(5f, alvo.getVelocidade(), 0.001f);
        assertTrue(alvo.isAtivo());
    }

    @Test
    public void testAlvoRapidoTemVelocidadeSuperior() {
        // Como o teste é feito: Cria-se um AlvoComum e um AlvoRapido usando a mesma velocidade base. Depois comparamos suas velocidades reais.
        // Condição de Pass: Passa se a velocidade do AlvoRapido for estritamente maior que a do AlvoComum E corresponder a exatamente 2x a velocidade base.
        // Condição de Not Pass: Falha caso o AlvoRapido possua velocidade igual/menor à do AlvoComum ou caso o multiplicador de velocidade não seja 2x.
        float velocidadeBase = 5f;
        AlvoComum comum = new AlvoComum(100, 100, 20, velocidadeBase, LARGURA, ALTURA, canhoes, collisionLock);
        AlvoRapido rapido = new AlvoRapido(100, 100, 20, velocidadeBase, LARGURA, ALTURA, canhoes, collisionLock);

        // AlvoRapido deve ter velocidade efetiva 2x maior
        assertTrue("AlvoRapido deve ser mais rápido que AlvoComum",
                rapido.getVelocidade() > comum.getVelocidade());
        assertEquals(velocidadeBase * 2, rapido.getVelocidade(), 0.001f);
    }

    @Test
    public void testPolimorfismoMover() {
        // Como o teste é feito: Instanciamos um AlvoComum e um AlvoRapido, mas os tratamos como a classe base abstrata 'Alvo'. Chamamos o método genérico mover() neles dentro de um loop.
        // Condição de Pass: Passa se as posições finais (X ou Y) de ambos os alvos mudarem em relação às suas posições iniciais, exibindo o comportamento correto de movimento.
        // Condição de Not Pass: Falha se a posição de algum deles não se alterar de jeito nenhum após o loop, indicando que o método mover() não reagiu.
        // Criar ambos os tipos como referência Alvo (polimorfismo)
        Alvo comum = new AlvoComum(400, 300, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);
        Alvo rapido = new AlvoRapido(400, 300, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);

        float xInicialComum = comum.getX();
        float xInicialRapido = rapido.getX();

        // Executar mover() várias vezes
        for (int i = 0; i < 10; i++) {
            comum.mover();
            rapido.mover();
        }

        // Ambos devem ter se movido (posição diferente da inicial)
        assertTrue("AlvoComum deveria ter se movido",
                Math.abs(comum.getX() - xInicialComum) > 0
                        || Math.abs(comum.getY() - 300) > 0);
        assertTrue("AlvoRapido deveria ter se movido",
                Math.abs(rapido.getX() - xInicialRapido) > 0
                        || Math.abs(rapido.getY() - 300) > 0);
    }

    @Test
    public void testAlvoRapidoMoverDeslocaMaisQueComum() {
        // Como o teste é feito: Cria AlvoComum e AlvoRapido na mesma posição e com mesma direção fixa (horizontal).
        // Ambos chamam mover() uma vez. Como AlvoRapido aplica multiplicador de 2x, seu deslocamento deve ser maior.
        // Condição de Pass: Deslocamento do AlvoRapido é maior que o do AlvoComum após um mover().
        // Condição de Not Pass: Falha se AlvoRapido não se deslocar mais que o AlvoComum, indicando que o multiplicador não está funcionando.
        AlvoComum comum = new AlvoComum(400, 300, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);
        AlvoRapido rapido = new AlvoRapido(400, 300, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);

        float xInicialComum = comum.getX();
        float yInicialComum = comum.getY();
        float xInicialRapido = rapido.getX();
        float yInicialRapido = rapido.getY();

        // Executar 50 movimentações para acumular deslocamento significativo
        for (int i = 0; i < 50; i++) {
            comum.mover();
            rapido.mover();
        }

        // Calcular deslocamento total de cada um (distância Euclidiana da origem)
        float deslocamentoComum = Alvo.calcularDistancia(xInicialComum, yInicialComum, comum.getX(), comum.getY());
        float deslocamentoRapido = Alvo.calcularDistancia(xInicialRapido, yInicialRapido, rapido.getX(), rapido.getY());

        // O AlvoRapido deve ter se deslocado mais que o AlvoComum (multiplicador 2x)
        assertTrue("AlvoRapido (2x velocidade) deveria deslocar mais que AlvoComum",
                deslocamentoRapido > deslocamentoComum);
    }

    // ── Testes de cálculo de distância ───────────────────────────

    @Test
    public void testCalcularDistanciaHorizontal() {
        // Como o teste é feito: Verifica a fórmula de distância Euclidiana matemática usando dois pontos fixos ao longo do eixo X (mesmo Y).
        // Condição de Pass: Passa se a distância calculada entre (0,0) e (3,0) for exatamente 3.
        // Condição de Not Pass: Falha se o cálculo retornar um número incorreto (diferente de 3).
        float dist = Alvo.calcularDistancia(0, 0, 3, 0);
        assertEquals(3f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaVertical() {
        // Como o teste é feito: Verifica a fórmula de distância Euclidiana da classe usando dois pontos fixos ao longo do eixo Y (mesmo X).
        // Condição de Pass: Passa se a distância calculada entre (0,0) e (0,4) for exatamente 4.
        // Condição de Not Pass: Falha em caso de imprecisão na fórmula de distância, retornando valor diferente de 4.
        float dist = Alvo.calcularDistancia(0, 0, 0, 4);
        assertEquals(4f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaDiagonal() {
        // Como o teste é feito: Utiliza do teorema de Pitágoras em um triângulo retângulo onde os lados em desnível (catetos) medem 3 e 4 unidades.
        // Condição de Pass: Passa se a hipotenusa retornada (distância) pela equação matemática for exatamente 5.
        // Condição de Not Pass: Falha se o algoritmo matemático interno contiver erros e retornar número diferente de 5.
        // Triângulo 3-4-5
        float dist = Alvo.calcularDistancia(0, 0, 3, 4);
        assertEquals(5f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaMesmoPonto() {
        // Como o teste é feito: Verifica a lógica no cenário onde ambos os pontos espaciais forem idênticos (mesma coordenada X e Y).
        // Condição de Pass: Passa se a distância for calculada como exatamente 0.
        // Condição de Not Pass: Falha se houver problema no calculo de zeros e acabar gerando distância diferente de 0.
        float dist = Alvo.calcularDistancia(10, 20, 10, 20);
        assertEquals(0f, dist, 0.001f);
    }

    @Test
    public void testCalcularDistanciaCoordenadasNegativas() {
        // Como o teste é feito: Verifica que o cálculo de distância funciona corretamente com coordenadas negativas.
        // Condição de Pass: A distância entre (-3,-4) e (0,0) deve ser 5 (módulo/raiz quadrada sempre positiva).
        // Condição de Not Pass: Falha se coordenadas negativas causarem resultado incorreto.
        float dist = Alvo.calcularDistancia(-3f, -4f, 0f, 0f);
        assertEquals("Distância com coordenadas negativas deve ser correta", 5.0f, dist, 0.001f);

        // Dois pontos negativos no eixo Y
        float dist2 = Alvo.calcularDistancia(-5f, -5f, -5f, -10f);
        assertEquals("Distância entre dois pontos negativos deve ser positiva", 5.0f, dist2, 0.001f);
    }

    // ── Testes de estado ─────────────────────────────────────────

    @Test
    public void testDesativarAlvo() {
        // Como o teste é feito: Confere a integridade da flag booleana interno (isAtivo) para atestar a continuidade temporal com chamadas setters diretas.
        // Condição de Pass: Passa se o alvo for ativo por padrão, e após usar `setAtivo(false)`, o método `isAtivo()` retornar false em definitivo.
        // Condição de Not Pass: Falha se a propriedade não iniciar como alvo verdadeiro (true) ou for incapaz de persistir à mudança da instrução desativante.
        AlvoComum alvo = new AlvoComum(100, 100, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);
        assertTrue(alvo.isAtivo());

        alvo.setAtivo(false);
        assertFalse(alvo.isAtivo());
    }

    @Test
    public void testAlvoEhThread() {
        // Como o teste é feito: Utiliza o operador de herança "instanceof" em tempo de execução para atestar a hierarquia de threads implementada na API Thread Java.
        // Condição de Pass: Ocorre um sucesso se a instância materializada pertencer familiarmente à categoria subentendida "Thread".
        // Condição de Not Pass: Acontece rejeição se na arquitetura atual ninguém interagir nem importar diretamente com Thread.
        AlvoComum alvo = new AlvoComum(100, 100, 20, 5, LARGURA, ALTURA, canhoes, collisionLock);
        assertTrue("Alvo deve ser uma Thread", alvo instanceof Thread);
    }
}
