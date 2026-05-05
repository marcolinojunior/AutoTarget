/*
 * ============================================================================
 * Arquivo: JogoException.java
 * Pacote:  com.autotarget.exception
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Exceção personalizada (checked exception) para situações de erro
 *   específicas do jogo AutoTarget. Estende Exception e oferece dois
 *   construtores: um com mensagem descritiva e outro com mensagem + causa
 *   original (encadeamento de exceções).
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - Exceção personalizada JogoException — OBRIGATÓRIA pela rubrica.
 *     - Lançada em situações de erro com mensagens úteis e preventivas:
 *       • Canhão fora dos limites da tela
 *       • Número máximo de canhões por lado excedido (10)
 *       • Energia insuficiente para adicionar canhão
 *       • Jogo já em execução (transição de estado inválida)
 *       • Divisão por zero ao calcular ângulo (encadeamento com causa)
 *     - Capturada com try-catch em:
 *       • MainActivity.configurarBotoes() → Toast ao usuário
 *       • MainActivity.adicionarCanhaoNoLado() → Toast + Log.w
 *     - Construtores:
 *       • JogoException(String mensagem) — erro com descrição
 *       • JogoException(String mensagem, Throwable causa) — com causa original
 *
 *   ► Testes unitários (6.1.7):
 *     - Testada em JogoExceptionTest: mensagem simples, mensagem com causa,
 *       lançamento (expected), captura try-catch, herança de Exception.
 *
 * ============================================================================
 */
package com.autotarget.exception;

/**
 * Exceção customizada para situações de erro no jogo AutoTarget.
 * <p>
 * Exemplos de uso:
 * <ul>
 *   <li>Tentativa de adicionar canhão fora dos limites da tela</li>
 *   <li>Número máximo de canhões excedido</li>
 *   <li>Transição de estado inválida do jogo</li>
 *   <li>Divisão por zero ao calcular ângulo</li>
 * </ul>
 */
public class JogoException extends Exception {

    /**
     * Cria uma JogoException com mensagem descritiva.
     *
     * @param mensagem descrição do erro ocorrido
     */
    public JogoException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria uma JogoException com mensagem e causa original.
     *
     * @param mensagem descrição do erro ocorrido
     * @param causa    exceção original que provocou o erro
     */
    public JogoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
