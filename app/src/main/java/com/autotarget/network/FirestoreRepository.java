/*
 * ============================================================================
 * Arquivo: FirestoreRepository.java
 * Pacote:  com.autotarget.network
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Repositório para persistência de dados no Firebase Firestore.
 *   Implementa padrão Multi-Tenant com isolamento por userId.
 *   Coleções: 'partidas' (histórico) e 'telemetria' (sensor térmico).
 *
 * SEGURANÇA (AV3 §6.3.2-e):
 *   - Firebase Auth para passaporte lógico por jogador
 *   - Firestore Rules: request.auth.uid == userId
 *   - IDs UUID aleatórios (sem sequenciais monótonos)
 *
 * ============================================================================
 */
package com.autotarget.network;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositório Firestore com Multi-Tenant.
 * Todas as operações são off-UI-thread (invocadas pela FirebaseIOThread).
 */
public class FirestoreRepository {

    private static final String TAG = "FirestoreRepository";
    private static final String COLECAO_PARTIDAS = "partidas";
    private static final String COLECAO_TELEMETRIA = "telemetria";

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public FirestoreRepository() {
        try {
            this.db = FirebaseFirestore.getInstance();
            this.auth = FirebaseAuth.getInstance();
            Log.i(TAG, "FirestoreRepository inicializado");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar Firebase", e);
        }
    }

    /**
     * Retorna o UID do usuário logado, ou null.
     */
    private String getUserId() {
        if (auth == null) return null;
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Salva resultado de uma partida no Firestore.
     *
     * @param pontuacaoEsq     pontuação do lado esquerdo
     * @param pontuacaoDir     pontuação do lado direito
     * @param tempoTotal       tempo total em segundos
     * @param reconciliacoes   número de reconciliações
     * @param vencedor         "ESQUERDO", "DIREITO" ou "EMPATE"
     * @param dadosCriptografados dados extras criptografados (AES/GCM)
     */
    public void salvarPartida(int pontuacaoEsq, int pontuacaoDir,
                               long tempoTotal, int reconciliacoes,
                               String vencedor, String dadosCriptografados) {
        String userId = getUserId();
        if (db == null) {
            Log.w(TAG, "Firestore não disponível — partida não salva");
            return;
        }

        Map<String, Object> partida = new HashMap<>();
        partida.put("pontuacaoEsq", pontuacaoEsq);
        partida.put("pontuacaoDir", pontuacaoDir);
        partida.put("pontuacaoTotal", pontuacaoEsq + pontuacaoDir);
        partida.put("tempoTotal", tempoTotal);
        partida.put("reconciliacoes", reconciliacoes);
        partida.put("vencedor", vencedor);
        partida.put("dadosCriptografados", dadosCriptografados);
        partida.put("timestamp", com.google.firebase.Timestamp.now());
        partida.put("userId", userId != null ? userId : "anonymous");

        db.collection(COLECAO_PARTIDAS)
                .add(partida)
                .addOnSuccessListener(docRef ->
                        Log.i(TAG, "Partida salva: " + docRef.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Erro ao salvar partida", e));
    }

    /**
     * Sobrecarga de compatibilidade.
     */
    public void salvarPartida(int pontuacao, long tempo) {
        salvarPartida(pontuacao, 0, tempo, 0, "N/A", "");
    }

    /**
     * Salva dados de telemetria (sensor térmico).
     */
    public void salvarTelemetria(float temperatura, int alvosAtivos,
                                  float energiaEsq, float energiaDir) {
        String userId = getUserId();
        if (db == null) return;

        Map<String, Object> telemetria = new HashMap<>();
        telemetria.put("temperatura", temperatura);
        telemetria.put("alvosAtivos", alvosAtivos);
        telemetria.put("energiaEsq", energiaEsq);
        telemetria.put("energiaDir", energiaDir);
        telemetria.put("timestamp", com.google.firebase.Timestamp.now());
        telemetria.put("userId", userId != null ? userId : "anonymous");

        db.collection(COLECAO_TELEMETRIA)
                .add(telemetria)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Erro ao salvar telemetria", e));
    }

    /**
     * Lista ranking de partidas ordenado por pontuação total (desc).
     *
     * @param callback callback com lista de mapas
     */
    public void listarRanking(OnRankingLoadedListener callback) {
        if (db == null) {
            callback.onRankingLoaded(new ArrayList<>());
            return;
        }

        db.collection(COLECAO_PARTIDAS)
                .orderBy("pontuacaoTotal", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> ranking = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        ranking.add(doc.getData());
                    }
                    callback.onRankingLoaded(ranking);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao carregar ranking", e);
                    callback.onRankingLoaded(new ArrayList<>());
                });
    }

    /**
     * Callback para ranking carregado.
     */
    public interface OnRankingLoadedListener {
        void onRankingLoaded(List<Map<String, Object>> ranking);
    }
}
