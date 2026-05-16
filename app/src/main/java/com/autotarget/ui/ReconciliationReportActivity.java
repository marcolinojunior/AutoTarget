package com.autotarget.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Exibe o relatório textual de reconciliação ao fim da partida.
 */
public class ReconciliationReportActivity extends AppCompatActivity {

    public static final String EXTRA_REPORT = "extra_report";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Relatório de Reconciliação");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String report = getIntent() != null ? getIntent().getStringExtra(EXTRA_REPORT) : null;
        if (report == null || report.trim().isEmpty()) {
            report = "Nenhum dado de reconciliação disponível.";
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scrollView.setPadding(32, 32, 32, 32);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Splitting report by sections for better UI visualization
        String[] sections = report.split("\n(?=METRICAS|RELATORIO|\\[EVIDENCIA)");
        for (String section : sections) {
            TextView tvSection = new TextView(this);
            tvSection.setTextSize(14f);
            tvSection.setLineSpacing(8f, 1.0f);
            tvSection.setPadding(0, 0, 0, 32);

            SpannableString spannable = new SpannableString(section);

            // Highlight Headers
            if (section.startsWith("METRICAS_") || section.startsWith("RELATORIO_")) {
                int headerEnd = section.indexOf('\n');
                if (headerEnd == -1) headerEnd = section.length();
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#E94560")), 0, headerEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, headerEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (section.startsWith("[EVIDENCIA C0-07]")) {
                int headerEnd = section.indexOf('\n');
                if (headerEnd == -1) headerEnd = section.length();
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#00B4D8")), 0, headerEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, headerEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvSection.setBackgroundColor(Color.parseColor("#223344"));
                tvSection.setPadding(16, 16, 16, 16);
            } else {
                tvSection.setTextColor(Color.parseColor("#E0E0E0"));
            }

            // Default Text Color for normal text inside highlighted headers
            if (spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class).length > 0) {
                int headerEnd = section.indexOf('\n');
                if(headerEnd != -1 && headerEnd < section.length()) {
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#E0E0E0")), headerEnd, section.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                tvSection.setTextColor(Color.parseColor("#E0E0E0"));
            }

            tvSection.setText(spannable);
            layout.addView(tvSection);
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
