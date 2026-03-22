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
