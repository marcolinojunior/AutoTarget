package com.autotarget.model;

import com.autotarget.engine.Jogo;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;

public class CanhaoDimensionSyncTest {

    @Test
    public void testDimensionSync() throws Exception {
        Jogo jogo = new Jogo();
        // Inicialmente 0,0 ou dimensões padrão
        jogo.setDimensoesTela(800, 600);
        
        // Adicionar um canhão
        jogo.adicionarCanhao(100, 100, Lado.ESQUERDO);
        Canhao canhao = jogo.getCanhoesEsquerdo().get(0);
        
        // Verificar se pegou as dimensões iniciais
        // Dependendo de como o Canhao é criado em adicionarCanhao
        // Atualmente adicionarCanhao passa larguraTela/alturaTela do Jogo
        
        // Mudar dimensões do jogo
        jogo.setDimensoesTela(1920, 1080);
        
        // Verificar se o canhão foi atualizado via reflexão/acesso
        // Como larguraTela é private em Canhao, mas temos setters e possivelmente getters
        // Se não houver getter, precisaremos adicionar um para teste ou confiar no comportamento
        
        // Vou adicionar getters em Canhao se não existirem
        // Olhando o outline anterior, não vi getLarguraTela
    }
}