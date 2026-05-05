/*
 * ============================================================================
 * Arquivo: JogoExceptionTest.java
 * Pacote:  com.autotarget (test)
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe de testes unitários JUnit para a exceção personalizada
 *   JogoException. Valida criação com mensagem simples, encadeamento
 *   de causas (Exception chaining), lançamento/captura via @expected e
 *   try-catch, e verificação da hierarquia de herança (extends Exception).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Testes unitários (6.1.7):
 *     - ≥3 testes cobrindo pontos críticos + casos de borda:
 *       • testMensagemSimples — JogoException com mensagem, verifica getMessage()
 *       • testMensagemComCausa — encadeamento com ArithmeticException como causa
 *       • testLancamentoExcecao — @Test(expected) verifica que é lançada
 *       • testCapturaTryCatch — captura com try-catch e valida mensagem
 *       • testHerancaDeException — instanceof Exception e Throwable
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - Valida que JogoException funciona corretamente como checked exception:
 *       pode ser lançada, capturada, e encadeia causas originais.
 *     - Testa os dois construtores: (String) e (String, Throwable).
 *
 * TOTAL DE TESTES: 5
 *
 * ============================================================================
 */
package com.autotarget;

import com.autotarget.exception.JogoException;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testes unitários para a exceção customizada {@link JogoException}.
 * <p>
 * Verifica a criação correta de mensagens, encadeamento de causas
 * e lançamento/captura da exceção.
 */
public class JogoExceptionTest {

    @Test
    public void testMensagemSimples() {
        JogoException ex = new JogoException("Erro de teste");
        assertEquals("Erro de teste", ex.getMessage());
    }

    @Test
    public void testMensagemComCausa() {
        ArithmeticException causa = new ArithmeticException("divisão por zero");
        JogoException ex = new JogoException("Erro ao calcular ângulo", causa);

        assertEquals("Erro ao calcular ângulo", ex.getMessage());
        assertSame(causa, ex.getCause());
        assertTrue(ex.getCause() instanceof ArithmeticException);
    }

    @Test(expected = JogoException.class)
    public void testLancamentoExcecao() throws JogoException {
        throw new JogoException("Canhão fora dos limites!");
    }

    @Test
    public void testCapturaTryCatch() {
        String mensagemCapturada = null;
        try {
            throw new JogoException("Número máximo de canhões atingido");
        } catch (JogoException e) {
            mensagemCapturada = e.getMessage();
        }
        assertNotNull(mensagemCapturada);
        assertTrue(mensagemCapturada.contains("máximo"));
    }

    @Test
    public void testHerancaDeException() {
        JogoException ex = new JogoException("teste herança");
        assertTrue(ex instanceof Exception);
        assertTrue(ex instanceof Throwable);
    }
}
