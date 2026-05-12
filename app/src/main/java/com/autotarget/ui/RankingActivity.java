/*
 * ============================================================================
 * Arquivo: RankingActivity.java
 * Pacote:  com.autotarget.ui
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Tela de ranking com melhores pontuações lidas do Firestore.
 *   Dados sensíveis são descriptografados via Cryptography (AES/GCM).
 *
 * ============================================================================
 */
package com.autotarget.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.autotarget.R;
import com.autotarget.network.FirestoreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tela de ranking com pontuações do Firestore.
 */
public class RankingActivity extends AppCompatActivity {

    private static final String TAG = "RankingActivity";
    private RecyclerView recyclerView;
    private RankingAdapter adapter;
    private FirestoreRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🏆 Ranking");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rvRanking);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RankingAdapter();
        recyclerView.setAdapter(adapter);

        repository = new FirestoreRepository();
        carregarRanking();
    }

    private void carregarRanking() {
        repository.listarRanking(ranking -> runOnUiThread(() -> {
            adapter.setData(ranking);
            Log.i(TAG, "Ranking carregado: " + ranking.size() + " entradas");
        }));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ── Adapter ─────────────────────────────────────────────────

    static class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ViewHolder> {
        private List<Map<String, Object>> data = new ArrayList<>();

        void setData(List<Map<String, Object>> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ranking, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Map<String, Object> item = data.get(position);

            holder.tvPosicao.setText("#" + (position + 1));

            Object pontTotal = item.get("pontuacaoTotal");
            holder.tvPontuacao.setText(pontTotal != null ? pontTotal.toString() : "0");

            Object pontEsq = item.get("pontuacaoEsq");
            Object pontDir = item.get("pontuacaoDir");
            holder.tvDetalhe.setText(String.format("ESQ:%s | DIR:%s",
                    pontEsq != null ? pontEsq.toString() : "0",
                    pontDir != null ? pontDir.toString() : "0"));

            Object vencedor = item.get("vencedor");
            holder.tvVencedor.setText(vencedor != null ? vencedor.toString() : "");

            // Indicador de dados criptografados
            Object crypto = item.get("dadosCriptografados");
            holder.tvCrypto.setVisibility(
                    crypto != null && !crypto.toString().isEmpty() ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPosicao, tvPontuacao, tvDetalhe, tvVencedor, tvCrypto;

            ViewHolder(View v) {
                super(v);
                tvPosicao = v.findViewById(R.id.tvPosicao);
                tvPontuacao = v.findViewById(R.id.tvPontuacao);
                tvDetalhe = v.findViewById(R.id.tvDetalhe);
                tvVencedor = v.findViewById(R.id.tvVencedor);
                tvCrypto = v.findViewById(R.id.tvCrypto);
            }
        }
    }
}
