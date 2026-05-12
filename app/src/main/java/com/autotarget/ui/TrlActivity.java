/*
 * ============================================================================
 * Arquivo: TrlActivity.java
 * Pacote:  com.autotarget.ui
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Tela informativa com classificação TRL 1-9 do projeto AutoTarget.
 *   Justifica cada nível com base nas funcionalidades implementadas.
 *
 * ============================================================================
 */
package com.autotarget.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela informativa TRL (Technology Readiness Level) 1-9.
 */
public class TrlActivity extends AppCompatActivity {

    private static final String[][] TRL_DATA = {
        {"TRL 1", "Princípios básicos observados",
         "✅ Definição matemática de colisão vetorial circular e translação estocástica.",
         "#4CAF50"},
        {"TRL 2", "Conceito da tecnologia formulado",
         "✅ Especificação do modelo cibernético de predição e amortecimento balístico.",
         "#4CAF50"},
        {"TRL 3", "Prova de conceito analítica",
         "✅ Verificação do modelo de Lock Ordering na matriz experimental do PhysicsTimer.",
         "#4CAF50"},
        {"TRL 4", "Validação em laboratório",
         "✅ Integração dos módulos de reconciliação algébrica EJML com dados simulados.",
         "#4CAF50"},
        {"TRL 5", "Validação em ambiente relevante simulado",
         "⭐ NÍVEL VIGENTE — O código suporta degradação termal injetada, restrições gaussianas contínuas e restrição multicore por JNI.",
         "#FF9800"},
        {"TRL 6", "Demonstração em ambiente relevante",
         "❌ Necessitaria migração para microcontroladores físicos autônomos sem abstração Android.",
         "#9E9E9E"},
        {"TRL 7", "Protótipo em ambiente operacional",
         "❌ Pressupõe integração com hardware de defesa real e testes de campo.",
         "#9E9E9E"},
        {"TRL 8", "Sistema completo qualificado",
         "❌ Exigiria certificação de segurança e testes de confiabilidade industrial.",
         "#9E9E9E"},
        {"TRL 9", "Sistema operacional comprovado",
         "❌ Implantação e aceitação industrial completa em missões ininterruptas.",
         "#9E9E9E"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📊 Análise TRL");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scrollView.setPadding(24, 24, 24, 24);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Título
        TextView title = new TextView(this);
        title.setText("Technology Readiness Level\nProjeto AutoTarget");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);

        // Descrição
        TextView desc = new TextView(this);
        desc.setText("Metodologia criada pela NASA (1970s) para avaliar maturidade " +
                "tecnológica de sistemas em desenvolvimento. Escala de 1 (princípios " +
                "básicos) a 9 (sistema operacional comprovado).");
        desc.setTextColor(Color.parseColor("#B0BEC5"));
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, 32);
        layout.addView(desc);

        // Cards TRL
        for (String[] trl : TRL_DATA) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.parseColor("#16213E"));
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);

            // Nível + Indicador de cor
            TextView tvNivel = new TextView(this);
            tvNivel.setText(trl[0] + " — " + trl[1]);
            tvNivel.setTextColor(Color.parseColor(trl[3]));
            tvNivel.setTextSize(16);
            tvNivel.setPadding(0, 0, 0, 8);
            card.addView(tvNivel);

            // Justificativa
            TextView tvJust = new TextView(this);
            tvJust.setText(trl[2]);
            tvJust.setTextColor(Color.parseColor("#E0E0E0"));
            tvJust.setTextSize(13);
            card.addView(tvJust);

            layout.addView(card);
        }

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
