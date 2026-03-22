package com.autotarget.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Testes unitários para a classe Alvo, focando no cálculo de distância.
 */
public class AlvoTest {

    @Test
    public void testCalcularDistancia_CenarioNormal() {
        // Cenário Normal: Calcular a distância entre dois pontos distintos conhecidos (Triângulo 3, 4, 5)
        // Ponto 1: (0, 0), Ponto 2: (3, 4) -> Distância deve ser 5.0
        float distancia = Alvo.calcularDistancia(0f, 0f, 3f, 4f);
        assertEquals("A distância entre (0,0) e (3,4) deve ser 5.0", 5.0f, distancia, 0.001f);
    }

    @Test
    public void testCalcularDistancia_CasoDeBorda_MesmoPonto() {
        // Caso de Borda: Distância entre o mesmo ponto (deve ser 0)
        // Ponto 1: (10, 15), Ponto 2: (10, 15) -> Distância deve ser 0.0
        float distancia = Alvo.calcularDistancia(10f, 15f, 10f, 15f);
        assertEquals("A distância entre um ponto e ele mesmo deve ser 0.0", 0.0f, distancia, 0.001f);
    }

    @Test
    public void testCalcularDistancia_CasoDeBorda_CoordenadasNegativas() {
        // Caso de Borda: Distância usando coordenadas negativas
        // Para garantir que o cálculo (módulo/raiz) lide adequadamente com números negativos.
        // Ponto 1: (-3, -4), Ponto 2: (0, 0) -> Distância deve ser 5.0
        float distancia = Alvo.calcularDistancia(-3f, -4f, 0f, 0f);
        assertEquals("A distância com coordenadas negativas deve ser calculada corretamente (módulo/raiz)", 5.0f, distancia, 0.001f);

        // Outro teste envolvendo dois pontos negativos
        // Ponto 1: (-5, -5), Ponto 2: (-5, -10) -> Distância (no eixo Y) deve ser 5.0
        float distancia2 = Alvo.calcularDistancia(-5f, -5f, -5f, -10f);
        assertEquals("A distância entre duas coordenadas negativas deve ser positiva e exata", 5.0f, distancia2, 0.001f);
    }
}
