package com.autotarget.service;

/**
 * Stub para criptografia de dados sensíveis.
 * <p>
 * Será implementado em fases futuras (AV2+) para criptografar
 * estatísticas e dados de partida antes do armazenamento.
 */
public class Cryptography {

    /**
     * Criptografa uma string.
     * TODO: Implementar AES-256 (AV2).
     *
     * @param dados dados a serem criptografados
     * @return dados criptografados (stub retorna o original)
     */
    public String encrypt(String dados) {
        // Stub - a ser implementado em fases futuras
        return dados;
    }

    /**
     * Descriptografa uma string.
     * TODO: Implementar AES-256 (AV2).
     *
     * @param dados dados criptografados
     * @return dados originais (stub retorna o original)
     */
    public String decrypt(String dados) {
        // Stub - a ser implementado em fases futuras
        return dados;
    }
}
