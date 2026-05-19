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
        LineChart lineChartUtility = findViewById(R.id.lineChartUtility);
        LineChart lineChartConditioning = findViewById(R.id.lineChartConditioning);
        LineChart lineChartPositionError = findViewById(R.id.lineChartPositionError);

        String finalReport = report;
        btnCopyLogs.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Relatorio AutoTarget", finalReport);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show();
        });

        // AV2 EXCELENTE: Botão para exportar telemetria em CSV
        Button btnExportCSV = findViewById(R.id.btnExportCSV);
        btnExportCSV.setOnClickListener(v -> {
            java.util.List<String> csvFiles = ReconciliationLog.getInstance().exportarCSV(this);
            com.autotarget.util.RMAAnalysis.exportDeadlineMissesToCSV(this);
            if (csvFiles.isEmpty()) {
                Toast.makeText(this, "Nenhum dado para exportar.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "✅ " + csvFiles.size() + " CSVs exportados para armazenamento interno!",
                        Toast.LENGTH_LONG).show();
            }
        });

        TextView tvResumoAdaptativo = new TextView(this);
        tvResumoAdaptativo.setTextSize(14f);
        tvResumoAdaptativo.setLineSpacing(8f, 1.05f);
        tvResumoAdaptativo.setTextColor(Color.WHITE);
        tvResumoAdaptativo.setPadding(20, 20, 20, 20);
        tvResumoAdaptativo.setBackgroundColor(Color.parseColor("#1F2A44"));
        tvResumoAdaptativo.setText(buildAdaptiveSummary());
        layoutReportText.addView(tvResumoAdaptativo, 0);

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
        setupUtilityChart(lineChartUtility);
        setupConditioningChart(lineChartConditioning);
        setupPositionErrorChart(lineChartPositionError);
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

    private void setupUtilityChart(LineChart chart) {
        List<ReconciliationLog.UtilitySample> samples = ReconciliationLog.getInstance().getUtilitySamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de utilidade registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        // Cores padronizadas: Ciano = Esquerdo, Vermelho = Direito
        final int COLOR_ESQ = Color.parseColor("#00B4D8");
        final int COLOR_DIR = Color.parseColor("#E94560");

        List<Entry> entriesEsq = new ArrayList<>();
        List<Entry> entriesDir = new ArrayList<>();
        int idxEsq = 0, idxDir = 0;

        for (int i = 0; i < samples.size(); i++) {
            ReconciliationLog.UtilitySample s = samples.get(i);
            if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                entriesEsq.add(new Entry(idxEsq++, (float) s.uAtual));
            } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                entriesDir.add(new Entry(idxDir++, (float) s.uAtual));
            }
        }

        LineDataSet setEsq = new LineDataSet(entriesEsq, "U(N) Esq");
        setEsq.setColor(COLOR_ESQ);
        setEsq.setCircleColor(COLOR_ESQ);
        setEsq.setLineWidth(2f);
        setEsq.setValueTextColor(Color.WHITE);

        LineDataSet setDir = new LineDataSet(entriesDir, "U(N) Dir");
        setDir.setColor(COLOR_DIR);
        setDir.setCircleColor(COLOR_DIR);
        setDir.setLineWidth(2f);
        setDir.setValueTextColor(Color.WHITE);

        chart.setData(new LineData(setEsq, setDir));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private void setupConditioningChart(LineChart chart) {
        List<ReconciliationLog.ConditioningSample> samples = ReconciliationLog.getInstance().getConditioningSamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de condicionamento registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        final int COLOR_ESQ = Color.parseColor("#00B4D8");
        final int COLOR_DIR = Color.parseColor("#E94560");

        List<Entry> entriesEsq = new ArrayList<>();
        List<Entry> entriesDir = new ArrayList<>();
        int idxEsq = 0, idxDir = 0;

        for (int i = 0; i < samples.size(); i++) {
            ReconciliationLog.ConditioningSample s = samples.get(i);
            float valor = Double.isFinite(s.conditionNumber)
                    ? (float) Math.log10(Math.max(1.0, s.conditionNumber))
                    : 12f;
            if (s.contexto != null && (s.contexto.contains("ESQ") || s.contexto.endsWith("_ESQUERDO"))) {
                entriesEsq.add(new Entry(idxEsq++, valor));
            } else if (s.contexto != null && (s.contexto.contains("DIR") || s.contexto.endsWith("_DIREITO"))) {
                entriesDir.add(new Entry(idxDir++, valor));
            }
        }

        LineDataSet setEsq = new LineDataSet(entriesEsq, "log10(cond) Esq");
        setEsq.setColor(COLOR_ESQ);
        setEsq.setCircleColor(COLOR_ESQ);
        setEsq.setLineWidth(2f);
        setEsq.setValueTextColor(Color.WHITE);

        LineDataSet setDir = new LineDataSet(entriesDir, "log10(cond) Dir");
        setDir.setColor(COLOR_DIR);
        setDir.setCircleColor(COLOR_DIR);
        setDir.setLineWidth(2f);
        setDir.setValueTextColor(Color.WHITE);

        chart.setData(new LineData(setEsq, setDir));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private void setupPositionErrorChart(LineChart chart) {
        List<ReconciliationLog.ReconSample> samples = ReconciliationLog.getInstance().getReconSamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de erro posicional registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        final int COLOR_ESQ = Color.parseColor("#00B4D8");
        final int COLOR_DIR = Color.parseColor("#E94560");

        // 2 séries: erro posicional reconciliado por lado (mais limpo visualmente)
        List<Entry> entriesEsq = new ArrayList<>();
        List<Entry> entriesDir = new ArrayList<>();
        int idxEsq = 0, idxDir = 0;
        int step = Math.max(1, samples.size() / 80);

        for (int i = 0; i < samples.size(); i += step) {
            ReconciliationLog.ReconSample s = samples.get(i);
            if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                entriesEsq.add(new Entry(idxEsq++, (float) s.erroPos));
            } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                entriesDir.add(new Entry(idxDir++, (float) s.erroPos));
            }
        }

        LineDataSet setEsq = new LineDataSet(entriesEsq, "Erro Posicional Esq (px)");
        setEsq.setColor(COLOR_ESQ);
        setEsq.setCircleColor(COLOR_ESQ);
        setEsq.setLineWidth(2f);
        setEsq.setValueTextColor(Color.WHITE);
        setEsq.setDrawCircles(false);

        LineDataSet setDir = new LineDataSet(entriesDir, "Erro Posicional Dir (px)");
        setDir.setColor(COLOR_DIR);
        setDir.setCircleColor(COLOR_DIR);
        setDir.setLineWidth(2f);
        setDir.setValueTextColor(Color.WHITE);
        setDir.setDrawCircles(false);

        chart.setData(new LineData(setEsq, setDir));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private String buildAdaptiveSummary() {
        List<ReconciliationLog.EnergyPenaltySample> energySamples = ReconciliationLog.getInstance().getEnergySamples();
        List<ReconciliationLog.ReconSample> reconSamples = ReconciliationLog.getInstance().getReconSamples();
        List<ReconciliationLog.UtilitySample> utilitySamples = ReconciliationLog.getInstance().getUtilitySamples();
        List<ReconciliationLog.ConditioningSample> conditioningSamples = ReconciliationLog.getInstance().getConditioningSamples();

        if (energySamples.isEmpty() && reconSamples.isEmpty() && utilitySamples.isEmpty() && conditioningSamples.isEmpty()) {
            return "Leitura automática: sem dados suficientes para inferir uma estratégia.\n" +
                    "Ação sugerida: execute uma partida completa para gerar energia, reconciliação e utilidade.";
        }

        // ── Energia por lado ──
        float energiaInicialEsq = energySamples.isEmpty() ? 0f : energySamples.get(0).energiaEsq;
        float energiaInicialDir = energySamples.isEmpty() ? 0f : energySamples.get(0).energiaDir;
        ReconciliationLog.EnergyPenaltySample ultimoEnergy = energySamples.isEmpty() ? null : energySamples.get(energySamples.size() - 1);
        float energiaFinalEsq = ultimoEnergy == null ? energiaInicialEsq : ultimoEnergy.energiaEsq;
        float energiaFinalDir = ultimoEnergy == null ? energiaInicialDir : ultimoEnergy.energiaDir;
        int canhoesFinaisEsq = ultimoEnergy == null ? 0 : ultimoEnergy.canhoesEsq;
        int canhoesFinaisDir = ultimoEnergy == null ? 0 : ultimoEnergy.canhoesDir;

        // ── Reconciliação por lado ──
        double reducaoEsq = 0, reducaoDir = 0, erroPosEsq = 0, erroPosDir = 0;
        int contEsq = 0, contDir = 0;
        if (!reconSamples.isEmpty()) {
            for (ReconciliationLog.ReconSample s : reconSamples) {
                double reducao = s.mseBruto > 0 ? ((s.mseBruto - s.mseRecon) / s.mseBruto) * 100.0 : 0.0;
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) { reducaoEsq += reducao; erroPosEsq += s.erroPos; contEsq++; }
                else if ("DIREITO".equalsIgnoreCase(s.lado)) { reducaoDir += reducao; erroPosDir += s.erroPos; contDir++; }
            }
            if (contEsq > 0) { reducaoEsq /= contEsq; erroPosEsq /= contEsq; }
            if (contDir > 0) { reducaoDir /= contDir; erroPosDir /= contDir; }
        }

        // ── Utilidade por lado ──
        double deltaMais1Esq = 0, deltaMenos1Esq = 0, deltaMais1Dir = 0, deltaMenos1Dir = 0;
        int contUtilEsq = 0, contUtilDir = 0;
        for (ReconciliationLog.UtilitySample s : utilitySamples) {
            if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                if (s.uMais1 != null) deltaMais1Esq += (s.uMais1 - s.uAtual);
                if (s.uMenos1 != null) deltaMenos1Esq += (s.uMenos1 - s.uAtual);
                contUtilEsq++;
            } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                if (s.uMais1 != null) deltaMais1Dir += (s.uMais1 - s.uAtual);
                if (s.uMenos1 != null) deltaMenos1Dir += (s.uMenos1 - s.uAtual);
                contUtilDir++;
            }
        }
        if (contUtilEsq > 0) { deltaMais1Esq /= contUtilEsq; deltaMenos1Esq /= contUtilEsq; }
        if (contUtilDir > 0) { deltaMais1Dir /= contUtilDir; deltaMenos1Dir /= contUtilDir; }

        // ── Condicionamento ──
        int fallbacks = 0;
        for (ReconciliationLog.ConditioningSample s : conditioningSamples) {
            if (s.usouFallback) fallbacks++;
        }

        // ── Diagnóstico por lado ──
        StringBuilder sb = new StringBuilder();
        sb.append("=== DIAGNÓSTICO POR LADO ===\n\n");

        // ESQUERDO
        sb.append("[ESQUERDO]\n");
        sb.append(String.format(java.util.Locale.US,
                "  Energia: %.1f -> %.1f | Canhões finais: %d\n", energiaInicialEsq, energiaFinalEsq, canhoesFinaisEsq));
        sb.append(String.format(java.util.Locale.US,
                "  Recon: redução %.1f%% | Erro posicional: %.1f px\n", reducaoEsq, erroPosEsq));
        sb.append(String.format(java.util.Locale.US,
                "  Utilidade: ΔU(N+1)=%.3f | ΔU(N-1)=%.3f\n", deltaMais1Esq, deltaMenos1Esq));
        boolean esqEnergiaCrítica = energiaFinalEsq < 10f;
        boolean esqReconFraca = reducaoEsq < 5.0;
        boolean esqStarvation = canhoesFinaisEsq == 0;
        if (esqStarvation) sb.append("  ⚠ ALERTA: STARVATION — sem canhões ativos!\n");
        else if (esqEnergiaCrítica) sb.append("  ⚠ Energia crítica — risco de parada iminente.\n");
        if (esqReconFraca && contEsq > 0) sb.append("  ⚠ Reconciliação ineficiente — verificar geometria dos canhões.\n");

        sb.append("\n[DIREITO]\n");
        sb.append(String.format(java.util.Locale.US,
                "  Energia: %.1f -> %.1f | Canhões finais: %d\n", energiaInicialDir, energiaFinalDir, canhoesFinaisDir));
        sb.append(String.format(java.util.Locale.US,
                "  Recon: redução %.1f%% | Erro posicional: %.1f px\n", reducaoDir, erroPosDir));
        sb.append(String.format(java.util.Locale.US,
                "  Utilidade: ΔU(N+1)=%.3f | ΔU(N-1)=%.3f\n", deltaMais1Dir, deltaMenos1Dir));
        boolean dirEnergiaCrítica = energiaFinalDir < 10f;
        boolean dirReconFraca = reducaoDir < 5.0;
        boolean dirStarvation = canhoesFinaisDir == 0;
        if (dirStarvation) sb.append("  ⚠ ALERTA: STARVATION — sem canhões ativos!\n");
        else if (dirEnergiaCrítica) sb.append("  ⚠ Energia crítica — risco de parada iminente.\n");
        if (dirReconFraca && contDir > 0) sb.append("  ⚠ Reconciliação ineficiente — verificar geometria dos canhões.\n");

        // ── Veredicto global ──
        sb.append("\n[VEREDICTO GLOBAL]\n");
        boolean algumStarvation = esqStarvation || dirStarvation;
        boolean algumaReconFraca = (esqReconFraca && contEsq > 0) || (dirReconFraca && contDir > 0);
        if (algumStarvation) {
            sb.append("⚠ Um dos lados sofreu STARVATION total. Verificar equilíbrio de energia e número de canhões.\n");
        } else if (algumaReconFraca) {
            sb.append("⚠ Reconciliação ineficiente em pelo menos um lado. Redistribuir canhões pode melhorar.\n");
        } else {
            sb.append("✓ Sistema operando dentro dos parâmetros esperados.\n");
        }
        sb.append(String.format(java.util.Locale.US, "Fallbacks numéricos: %d\n", fallbacks));

        return sb.toString();
    }

    private void setupReconChart(BarChart chart) {
        List<ReconciliationLog.ReconSample> samples = ReconciliationLog.getInstance().getReconSamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de reconciliação registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        final int COLOR_ESQ = Color.parseColor("#00B4D8");
        final int COLOR_DIR = Color.parseColor("#E94560");

        // Gráfico simplificado: MSE reconciliado por lado (barras agrupadas por slot de tempo)
        // Cada slot no eixo X representa uma amostra temporal; barras lado a lado = Esq vs Dir
        List<BarEntry> entriesEsq = new ArrayList<>();
        List<BarEntry> entriesDir = new ArrayList<>();

        int maxSlots = 15;
        int step = Math.max(1, samples.size() / maxSlots);

        int idx = 0;
        for (int i = 0; i < samples.size() && idx < maxSlots; i += step) {
            // Para cada slot, calcular média de MSE reconciliado de cada lado
            double somaEsq = 0, somaDir = 0;
            int contEsq = 0, contDir = 0;
            int windowEnd = Math.min(i + step, samples.size());
            for (int j = i; j < windowEnd; j++) {
                ReconciliationLog.ReconSample s = samples.get(j);
                if ("ESQUERDO".equalsIgnoreCase(s.lado)) { somaEsq += s.mseRecon; contEsq++; }
                else if ("DIREITO".equalsIgnoreCase(s.lado)) { somaDir += s.mseRecon; contDir++; }
            }
            if (contEsq > 0) entriesEsq.add(new BarEntry(idx, (float) (somaEsq / contEsq)));
            if (contDir > 0) entriesDir.add(new BarEntry(idx, (float) (somaDir / contDir)));
            idx++;
        }

        BarDataSet setEsq = new BarDataSet(entriesEsq, "MSE Recon Esq");
        setEsq.setColor(COLOR_ESQ);
        setEsq.setValueTextColor(Color.WHITE);

        BarDataSet setDir = new BarDataSet(entriesDir, "MSE Recon Dir");
        setDir.setColor(COLOR_DIR);
        setDir.setValueTextColor(Color.WHITE);

        float groupSpace = 0.30f;
        float barSpace = 0.05f;
        float barWidth = 0.30f;

        BarData data = new BarData(setEsq, setDir);
        data.setBarWidth(barWidth);

        chart.setData(data);
        chart.groupBars(0f, groupSpace, barSpace);

        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setCenterAxisLabels(true);
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(idx);

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
