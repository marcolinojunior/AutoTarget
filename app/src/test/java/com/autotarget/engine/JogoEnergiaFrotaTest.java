package com.autotarget.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autotarget.model.AlvoComum;
import com.autotarget.model.Canhao;
import com.autotarget.model.Lado;

import org.junit.Test;

import java.lang.reflect.Method;

public class JogoEnergiaFrotaTest {

    @Test
    public void testParadaTotalDaFrotaQuandoEnergiaZera() throws Exception {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);

        // Adiciona 3 canhões do lado esquerdo
        jogo.adicionarCanhao(100, 100, Lado.ESQUERDO);
        jogo.adicionarCanhao(200, 200, Lado.ESQUERDO);
        jogo.adicionarCanhao(300, 300, Lado.ESQUERDO);

        assertEquals("Devem haver 3 canhões ativos do lado esquerdo inicialmente", 3, jogo.contarCanhoesAtivos(Lado.ESQUERDO));

        System.out.println("Canhões ativos antes da parada: " + jogo.contarCanhoesAtivos(Lado.ESQUERDO));

        // Força a energia do lado esquerdo a ser <= 0 usando reflexão para chamar atualizarEnergia
        // Na verdade, podemos apenas zerar a energia manualmente através do getEnergyManager e esperar a atualização
        jogo.getEnergyManager(Lado.ESQUERDO).set(0f);

        // Chamamos o método atualizarEnergia via reflexão
        Method atualizarEnergiaMethod = Jogo.class.getDeclaredMethod("atualizarEnergia");
        atualizarEnergiaMethod.setAccessible(true);
        atualizarEnergiaMethod.invoke(jogo);

        System.out.println("Canhões ativos depois da parada: " + jogo.contarCanhoesAtivos(Lado.ESQUERDO));

        assertEquals("Nenhum canhão do lado esquerdo deve estar ativo após a energia zerar", 0, jogo.contarCanhoesAtivos(Lado.ESQUERDO));
        assertEquals("A energia do lado esquerdo deve estar clampada em 0", 0f, jogo.getEnergyManager(Lado.ESQUERDO).get(), 0.001f);

        // Verifica todos os canhões individualmente
        for (Canhao c : jogo.getCanhoesEsquerdo()) {
            assertFalse("O canhão deve estar inativo", c.isAtivo());
        }

        // Para limpar recursos
        for (Canhao c : jogo.getAllCanhoes()) {
            c.pararCanhao();
            c.interrupt();
        }
    }

    @Test
    public void testCreditoEnergiaPorAbateAconteceUmaUnicaVez() {
        Jogo jogo = new Jogo();
        jogo.setDimensoesTela(1000, 1000);
        jogo.getEnergyManager(Lado.ESQUERDO).set(10f);

        AlvoComum alvo = new AlvoComum(120, 120, 20, 0, 1000, 1000);
        jogo.adicionarAlvoManual(alvo, Lado.ESQUERDO);
        assertTrue(alvo.tentarAbater(Lado.ESQUERDO));

        jogo.verificarColisoes();
        float aposPrimeiro = jogo.getEnergia(Lado.ESQUERDO);
        jogo.verificarColisoes();
        float aposSegundo = jogo.getEnergia(Lado.ESQUERDO);

        assertEquals("Abate deve creditar energia uma única vez", aposPrimeiro, aposSegundo, 0.0001f);
    }
}
