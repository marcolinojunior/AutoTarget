/*
 * ============================================================================
 * Arquivo: FirestoreRepository.java
 * Pacote:  com.autotarget.service
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Classe de repositório responsável pela persistência de dados no
 *   Firebase Firestore. Encapsula as operações CRUD (salvar partida,
 *   listar ranking) abstraindo os detalhes de implementação do Firestore
 *   SDK. Na versão AV1, funciona como stub, preparando a arquitetura para
 *   integração real na AV2.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Persistência (Firebase / Firestore):
 *     - Segue o diagrama de arquitetura: FirebaseIOThread → Cryptography →
 *       FirestoreRepository.salvarPartida() → Firebase ☁️
 *     - Métodos salvarPartida() e listarRanking() — interface de repositório
 *       pronta para integração com Firebase Firestore SDK.
 *     - Padrão Repository: separa lógica de persistência da lógica de jogo.
 *
 *   ► Classes e funcionamento (6.1.4):
 *     - Classe standalone (não é thread), invocada pela FirebaseIOThread.
 *     - Separação de responsabilidade: persistência isolada de criptografia
 *       (Cryptography) e I/O assíncrono (FirebaseIOThread).
 *
 * MÉTODOS:
 *   - salvarPartida(int pontuacao, long tempo) → salva resultado (stub)
 *   - listarRanking() → consulta ranking de jogadores (stub)
 *
 * ============================================================================
 */
package com.autotarget.service;

/**
 * Stub para persistência no Firestore (Firebase).
 * <p>
 * Será implementado em fases futuras (AV2+) para salvar
 * resultados de partidas e listar ranking de jogadores.
 */
public class FirestoreRepository {

    /**
     * Salva os resultados de uma partida.
     * TODO: Implementar integração com Firebase Firestore (AV2).
     *
     * @param pontuacao pontuação final da partida
     * @param tempo     tempo total da partida em segundos
     */
    public void salvarPartida(int pontuacao, long tempo) {
        // Stub - a ser implementado em fases futuras
    }

    /**
     * Lista o ranking de jogadores.
     * TODO: Implementar consulta ao Firestore (AV2).
     */
    public void listarRanking() {
        // Stub - a ser implementado em fases futuras
    }
}
