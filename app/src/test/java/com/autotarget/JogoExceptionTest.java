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
