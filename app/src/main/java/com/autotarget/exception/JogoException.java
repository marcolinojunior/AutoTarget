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
