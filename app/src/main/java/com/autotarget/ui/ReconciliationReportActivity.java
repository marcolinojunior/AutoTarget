package com.autotarget.ui;

import android.graphics.Color;
import android.os.Bundle;
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
        scrollView.setPadding(24, 24, 24, 24);

        TextView tvReport = new TextView(this);
        tvReport.setTextColor(Color.WHITE);
        tvReport.setTextSize(14f);
        tvReport.setText(report);
        tvReport.setLineSpacing(8f, 1.0f);

        scrollView.addView(tvReport);
        setContentView(scrollView);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
