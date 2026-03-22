package com.autotarget.engine;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Testes unitários para regras de negócio e validações em Jogo,
 * validando que as exceções são lançadas corretamente e que a
 * lógica de colisões funciona como esperado.
 */
public class JogoTest {

    private Jogo jogo;

    @Before
    public void setUp() {
        jogo = new Jogo();
        // Definindo as dimensões fixas da tela para os testes
        jogo.setDimensoesTela(800, 600);
    }

    // ── Testes de validação de coordenadas ───────────────────────

    @Test(expected = JogoException.class)
    public void testAdicionarCanhao_Caso1_PosicoesNegativas() throws JogoException {
        // Como o teste é feito: Tentamos chamar o método 'adicionarCanhao' passando coordenadas X e Y negativas, o que representa uma posição fora dos limites virtuais da tela.
        // Condição de Pass: O teste passa se o método lançar uma exceção do tipo JogoException (conforme definido em expected = JogoException.class), confirmando que a validação barrou a posição inválida.
        // Condição de Not Pass: O teste falha se nenhuma exceção for lançada ou se for lançada uma exceção de tipo diferente.
        
        // Caso 1: Tentar adicionar um canhão com posições X e Y negativas (fora da tela).
        // Isso deve lançar a exceção JogoException.
        jogo.adicionarCanhao(-50f, -10f, Lado.ESQUERDO);
    }

    @Test(expected = JogoException.class)
    public void testAdicionarCanhao_Caso2_PosicoesMaioresQueTela() throws JogoException {
        // Como o teste é feito: Tentamos adicionar um canhão com posições maiores que as dimensões configuradas da tela (800x600).
        // Condição de Pass: O método deve lançar a exceção JogoException definida na anotação @Test, indicando sucesso na validação de limites superiores.
        // Condição de Not Pass: Falha caso a exceção não seja lançada, significando que o canhão foi adicionado erroneamente fora da tela.
        
        // Caso 2: Tentar adicionar um canhão com posições X e Y maiores que larguraTela e alturaTela.
        // Tela é de 800x600. Posições 850x650 estão fora.
        // Isso deve lançar a exceção JogoException.
        jogo.adicionarCanhao(850f, 650f, Lado.DIREITO);
    }

    @Test
    public void testAdicionarCanhao_CenarioValido() {
        // Como o teste é feito: Adicionamos um canhão com coordenadas válidas dentro das dimensões da tela (100, 100). Em seguida, verificamos se a lista de canhões no jogo agora contém 1 elemento.
        // Condição de Pass: O teste passa se nenhuma exceção for lançada durante a adição E o tamanho da lista de canhões for exatamente 1.
        // Condição de Not Pass: O teste falha se a regra de validação disparar incorretamente uma JogoException, ou se o tamanho da lista de canhões for diferente de 1.
        
        // Cenário Opcional/Complementar: Validar que coordenadas estritamente dentro da tela funcionam bem.
        try {
            jogo.adicionarCanhao(100f, 100f, Lado.ESQUERDO);
            assertEquals("Deve haver exatamente 1 canhão na lista após adição bem-sucedida", 1, jogo.getCanhoes().size());
        } catch (JogoException e) {
            fail("Não deveria lançar JogoException para coordenadas válidas e dentro da tela");
        }
    }

    // ── Teste do limite máximo de canhões por lado ───────────────

    @Test
    public void testAdicionarCanhao_LimiteMaximoPorLado() {
        // Como o teste é feito: Adicionamos exatamente 10 canhões (MAX_CANHOES_POR_LADO) no lado ESQUERDO.
        // A 11ª tentativa deve lançar JogoException com mensagem informando que o limite foi atingido.
        // Condição de Pass: Os 10 primeiros adicionam sem erro. O 11º lança JogoException.
        // Condição de Not Pass: Falha se algum dos 10 primeiros lançar exceção, ou se o 11º NÃO lançar exceção.

        // Adicionar 10 canhões com sucesso (limite máximo)
        try {
            for (int i = 0; i < 10; i++) {
                float x = 50f + (i * 30f); // Posições dentro da metade esquerda
                jogo.adicionarCanhao(x, 100f, Lado.ESQUERDO);
            }
        } catch (JogoException e) {
            fail("Não deveria lançar exceção para os primeiros 10 canhões: " + e.getMessage());
        }

        assertEquals("Devem existir exatamente 10 canhões", 10, jogo.getCanhoes().size());

        // O 11º canhão deve lançar JogoException
        try {
            jogo.adicionarCanhao(50f, 200f, Lado.ESQUERDO);
            fail("Deveria ter lançado JogoException ao exceder o limite de 10 canhões");
        } catch (JogoException e) {
            assertTrue("Mensagem deve mencionar o limite",
                    e.getMessage().contains("10") || e.getMessage().toLowerCase().contains("máximo"));
        }
    }

    // ── Teste de verificarColisoes() com contagem ────────────────

    @Test
    public void testVerificarColisoes_RetornaContagemDeDestruidos() {
        // Como o teste é feito: Inserimos manualmente alvos inativos (já destruídos) na lista de alvos do Jogo.
        // Ao chamar verificarColisoes(), o método deve detectar os inativos, removê-los da lista e retornar a contagem.
        // Condição de Pass: verificarColisoes() retorna o número correto de alvos destruídos (inativos) e os remove da lista.
        // Condição de Not Pass: Retorno diferente da quantidade de alvos inativos inseridos, ou lista não é limpa.

        Object lock = jogo.getCollisionLock();
        ArrayList<Canhao> dummyCanhoes = new ArrayList<>();

        // Criar alvos e marcar 2 deles como inativos (simulando que foram destruídos por colisão)
        Alvo alvo1 = new AlvoComum(100, 100, 20, 3, 800, 600, dummyCanhoes, lock);
        Alvo alvo2 = new AlvoComum(200, 200, 20, 3, 800, 600, dummyCanhoes, lock);
        Alvo alvo3 = new AlvoComum(300, 300, 20, 3, 800, 600, dummyCanhoes, lock);

        alvo1.setAtivo(false); // Destruído
        // alvo2 permanece ativo
        alvo3.setAtivo(false); // Destruído

        synchronized (lock) {
            jogo.getAlvos().add(alvo1);
            jogo.getAlvos().add(alvo2);
            jogo.getAlvos().add(alvo3);
        }

        // verificarColisoes() deve detectar 2 inativos e retornar 2
        int destruidos = jogo.verificarColisoes();
        assertEquals("Deve retornar 2 alvos destruídos", 2, destruidos);

        // A lista deve conter apenas o alvo2 (ativo)
        synchronized (lock) {
            assertEquals("Apenas 1 alvo ativo deve restar na lista", 1, jogo.getAlvos().size());
            assertTrue("O alvo restante deve estar ativo", jogo.getAlvos().get(0).isAtivo());
        }
    }

    @Test
    public void testVerificarColisoes_SemDestruidos() {
        // Como o teste é feito: Todos os alvos na lista estão ativos (nenhum foi destruído).
        // Condição de Pass: verificarColisoes() retorna 0 e a lista permanece inalterada.
        // Condição de Not Pass: Retorno diferente de 0 ou remoção indevida de alvos ativos.

        Object lock = jogo.getCollisionLock();
        ArrayList<Canhao> dummyCanhoes = new ArrayList<>();

        Alvo alvo1 = new AlvoComum(100, 100, 20, 3, 800, 600, dummyCanhoes, lock);
        Alvo alvo2 = new AlvoComum(200, 200, 20, 3, 800, 600, dummyCanhoes, lock);

        synchronized (lock) {
            jogo.getAlvos().add(alvo1);
            jogo.getAlvos().add(alvo2);
        }

        int destruidos = jogo.verificarColisoes();
        assertEquals("Nenhum alvo foi destruído, retorno deve ser 0", 0, destruidos);

        synchronized (lock) {
            assertEquals("Ambos os alvos devem permanecer na lista", 2, jogo.getAlvos().size());
        }
    }
}
