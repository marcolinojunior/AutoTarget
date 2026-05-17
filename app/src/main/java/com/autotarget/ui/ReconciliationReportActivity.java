package com.autotarget.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.R;
import com.autotarget.util.ReconciliationLog;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

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

        setContentView(R.layout.activity_reconciliation_report);

        LinearLayout layoutReportText = findViewById(R.id.layoutReportText);
        Button btnCopyLogs = findViewById(R.id.btnCopyLogs);
        LineChart lineChartEnergy = findViewById(R.id.lineChartEnergy);
        BarChart barChartRecon = findViewById(R.id.barChartRecon);

        String finalReport = report;
        btnCopyLogs.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Relatorio AutoTarget", finalReport);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show();
        });

        // Splitting report by sections for better UI visualization
        String[] sections = report.split("\n(?=METRICAS|RELATORIO|\\[EVIDENCIA)");
        for (String section : sections) {
            TextView tvSection = new TextView(this);
            tvSection.setTextSize(14f);
            tvSection.setLineSpacing(8f, 1.0f);
            tvSection.setPadding(0, 0, 0, 32);
            tvSection.setTextIsSelectable(true); // Permite selecionar e copiar texto

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
            layoutReportText.addView(tvSection);
        }

        setupEnergyChart(lineChartEnergy);
        setupReconChart(barChartRecon);
    }

    private void setupEnergyChart(LineChart chart) {
        List<ReconciliationLog.EnergyPenaltySample> samples = ReconciliationLog.getInstance().getEnergySamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de energia registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        List<Entry> entriesEsq = new ArrayList<>();
        List<Entry> entriesDir = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            ReconciliationLog.EnergyPenaltySample s = samples.get(i);
            entriesEsq.add(new Entry(i, s.energiaEsq));
            entriesDir.add(new Entry(i, s.energiaDir));
        }

        LineDataSet setEsq = new LineDataSet(entriesEsq, "Energia Esq");
        setEsq.setColor(Color.parseColor("#00B4D8"));
        setEsq.setCircleColor(Color.parseColor("#00B4D8"));
        setEsq.setLineWidth(2f);
        setEsq.setValueTextColor(Color.WHITE);

        LineDataSet setDir = new LineDataSet(entriesDir, "Energia Dir");
        setDir.setColor(Color.parseColor("#E94560"));
        setDir.setCircleColor(Color.parseColor("#E94560"));
        setDir.setLineWidth(2f);
        setDir.setValueTextColor(Color.WHITE);

        LineData lineData = new LineData(setEsq, setDir);
        chart.setData(lineData);

        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private void setupReconChart(BarChart chart) {
        List<ReconciliationLog.ReconSample> samples = ReconciliationLog.getInstance().getReconSamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de reconciliação registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        List<BarEntry> entriesBruto = new ArrayList<>();
        List<BarEntry> entriesRecon = new ArrayList<>();

        // Para evitar poluição visual, pegar até 20 amostras bem distribuídas
        int step = Math.max(1, samples.size() / 20);
        int index = 0;

        for (int i = 0; i < samples.size(); i += step) {
            ReconciliationLog.ReconSample s = samples.get(i);
            entriesBruto.add(new BarEntry(index, (float) s.mseBruto));
            entriesRecon.add(new BarEntry(index, (float) s.mseRecon));
            index++;
        }

        BarDataSet setBruto = new BarDataSet(entriesBruto, "MSE Bruto");
        setBruto.setColor(Color.parseColor("#FF9800"));
        setBruto.setValueTextColor(Color.WHITE);

        BarDataSet setRecon = new BarDataSet(entriesRecon, "MSE Reconciliado");
        setRecon.setColor(Color.parseColor("#4CAF50"));
        setRecon.setValueTextColor(Color.WHITE);

        float groupSpace = 0.08f;
        float barSpace = 0.06f; // x2 dataset
        float barWidth = 0.40f; // x2 dataset
        // (0.40 + 0.06) * 2 + 0.08 = 1.00

        BarData data = new BarData(setBruto, setRecon);
        data.setBarWidth(barWidth);

        chart.setData(data);
        chart.groupBars(0f, groupSpace, barSpace);

        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setCenterAxisLabels(true);
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(index);

        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);

        chart.invalidate();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
