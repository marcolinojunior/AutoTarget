package com.autotarget.engine;

import com.autotarget.exception.JogoException;
import com.autotarget.model.Lado;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Testes unitários para regras de negócio e validações em Jogo,
 * validando que as exceções são lançadas corretamente.
 */
public class JogoTest {

    private Jogo jogo;

    @Before
    public void setUp() {
        jogo = new Jogo();
        // Definindo as dimensões fixas da tela para os testes
        jogo.setDimensoesTela(800, 600);
    }

    @Test(expected = JogoException.class)
    public void testAdicionarCanhao_Caso1_PosicoesNegativas() throws JogoException {
        // Caso 1: Tentar adicionar um canhão com posições X e Y negativas (fora da tela).
        // Isso deve lançar a exceção JogoException.
        jogo.adicionarCanhao(-50f, -10f, Lado.ESQUERDO);
    }

    @Test(expected = JogoException.class)
    public void testAdicionarCanhao_Caso2_PosicoesMaioresQueTela() throws JogoException {
        // Caso 2: Tentar adicionar um canhão com posições X e Y maiores que larguraTela e alturaTela.
        // Tela é de 800x600. Posições 850x650 estão fora.
        // Isso deve lançar a exceção JogoException.
        jogo.adicionarCanhao(850f, 650f, Lado.DIREITO);
    }

    @Test
    public void testAdicionarCanhao_CenarioValido() {
        // Cenário Opcional/Complementar: Validar que coordenadas estritamente dentro da tela funcionam bem.
        try {
            jogo.adicionarCanhao(100f, 100f, Lado.ESQUERDO);
            assertEquals("Deve haver exatamente 1 canhão na lista após adição bem-sucedida", 1, jogo.getCanhoes().size());
        } catch (JogoException e) {
            fail("Não deveria lançar JogoException para coordenadas válidas e dentro da tela");
        }
    }
}
