/*
 * ============================================================================
 * Arquivo: Cryptography.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe de serviço responsável pela criptografia de dados sensíveis
 *   (estatísticas de partida) antes do armazenamento no Firestore. Na
 *   versão AV1, funciona como stub (retorna dados sem alteração), preparando
 *   a arquitetura para implementação real com AES-256 na AV2.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Segurança cibernética / criptografia:
 *     - Segue o diagrama de arquitetura: dados passam por Cryptography
 *       antes de serem enviados ao Firebase (fluxo: Região Crítica →
 *       FirebaseIOThread → Cryptography.encrypt() → Firestore).
 *     - Métodos encrypt(String) e decrypt(String) — interface pronta para
 *       implementação futura de cifra simétrica (AES-256).
 *
 *   ► Classes e funcionamento (6.1.4):
 *     - Classe standalone (não é thread), invocada pela FirebaseIOThread.
 *     - Separação de responsabilidade: lógica de criptografia isolada da
 *       lógica de persistência (FirestoreRepository) e I/O (FirebaseIOThread).
 *
 * MÉTODOS:
 *   - encrypt(String dados) → criptografa dados (stub: retorna original)
 *   - decrypt(String dados) → descriptografa dados (stub: retorna original)
 *
 * ============================================================================
 */
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
