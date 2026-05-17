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

        String finalReport = report;
        btnCopyLogs.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Relatorio AutoTarget", finalReport);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show();
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

        List<Entry> entriesAtual = new ArrayList<>();
        List<Entry> entriesMais1 = new ArrayList<>();
        List<Entry> entriesMenos1 = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            ReconciliationLog.UtilitySample s = samples.get(i);
            entriesAtual.add(new Entry(i, (float) s.uAtual));
            if (s.uMais1 != null) {
                entriesMais1.add(new Entry(i, s.uMais1.floatValue()));
            }
            if (s.uMenos1 != null) {
                entriesMenos1.add(new Entry(i, s.uMenos1.floatValue()));
            }
        }

        LineDataSet setAtual = new LineDataSet(entriesAtual, "U(N)");
        setAtual.setColor(Color.parseColor("#00B4D8"));
        setAtual.setCircleColor(Color.parseColor("#00B4D8"));
        setAtual.setLineWidth(2f);
        setAtual.setValueTextColor(Color.WHITE);

        LineDataSet setMais1 = new LineDataSet(entriesMais1, "U(N+1)");
        setMais1.setColor(Color.parseColor("#4CAF50"));
        setMais1.setCircleColor(Color.parseColor("#4CAF50"));
        setMais1.setLineWidth(2f);
        setMais1.setValueTextColor(Color.WHITE);

        LineDataSet setMenos1 = new LineDataSet(entriesMenos1, "U(N-1)");
        setMenos1.setColor(Color.parseColor("#FF9800"));
        setMenos1.setCircleColor(Color.parseColor("#FF9800"));
        setMenos1.setLineWidth(2f);
        setMenos1.setValueTextColor(Color.WHITE);

        chart.setData(new LineData(setAtual, setMais1, setMenos1));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private void setupConditioningChart(LineChart chart) {
        List<ReconciliationLog.SensorCountSample> samples = ReconciliationLog.getInstance().getSensorCountSamples();
        if (samples.isEmpty()) {
            chart.setNoDataText("Nenhum dado de quantidade de alvos registrado.");
            chart.setNoDataTextColor(Color.WHITE);
            return;
        }

        List<Entry> entriesEsq = new ArrayList<>();
        List<Entry> entriesDir = new ArrayList<>();
        int idxEsq = 0;
        int idxDir = 0;

        for (ReconciliationLog.SensorCountSample s : samples) {
            if ("ESQUERDO".equalsIgnoreCase(s.lado)) {
                entriesEsq.add(new Entry(idxEsq++, s.alvos));
            } else if ("DIREITO".equalsIgnoreCase(s.lado)) {
                entriesDir.add(new Entry(idxDir++, s.alvos));
            }
        }

        LineDataSet setEsq = new LineDataSet(entriesEsq, "Alvos Esq");
        setEsq.setColor(Color.parseColor("#00B4D8"));
        setEsq.setCircleColor(Color.parseColor("#00B4D8"));
        setEsq.setLineWidth(2f);
        setEsq.setValueTextColor(Color.WHITE);

        LineDataSet setDir = new LineDataSet(entriesDir, "Alvos Dir");
        setDir.setColor(Color.parseColor("#E94560"));
        setDir.setCircleColor(Color.parseColor("#E94560"));
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

    private String buildAdaptiveSummary() {
        List<ReconciliationLog.EnergyPenaltySample> energySamples = ReconciliationLog.getInstance().getEnergySamples();
        List<ReconciliationLog.ReconSample> reconSamples = ReconciliationLog.getInstance().getReconSamples();
        List<ReconciliationLog.UtilitySample> utilitySamples = ReconciliationLog.getInstance().getUtilitySamples();
        List<ReconciliationLog.ConditioningSample> conditioningSamples = ReconciliationLog.getInstance().getConditioningSamples();

        if (energySamples.isEmpty() && reconSamples.isEmpty() && utilitySamples.isEmpty() && conditioningSamples.isEmpty()) {
            return "Leitura automática: sem dados suficientes para inferir uma estratégia.\n" +
                    "Ação sugerida: execute uma partida completa para gerar energia, reconciliação e utilidade.";
        }

        float energiaInicial = energySamples.isEmpty() ? 0f : energySamples.get(0).energiaEsq + energySamples.get(0).energiaDir;
        ReconciliationLog.EnergyPenaltySample ultimoEnergy = energySamples.isEmpty() ? null : energySamples.get(energySamples.size() - 1);
        float energiaFinal = ultimoEnergy == null ? energiaInicial : ultimoEnergy.energiaEsq + ultimoEnergy.energiaDir;
        double taxaQuedaEnergia = energySamples.size() > 1
                ? (energiaInicial - energiaFinal) / Math.max(1, energySamples.size() - 1)
                : 0.0;

        double mediaReducaoRecon = 0.0;
        double mediaErroPos = 0.0;
        if (!reconSamples.isEmpty()) {
            for (ReconciliationLog.ReconSample sample : reconSamples) {
                double reducao = sample.mseBruto > 0 ? ((sample.mseBruto - sample.mseRecon) / sample.mseBruto) * 100.0 : 0.0;
                mediaReducaoRecon += reducao;
                mediaErroPos += sample.erroPos;
            }
            mediaReducaoRecon /= reconSamples.size();
            mediaErroPos /= reconSamples.size();
        }

        double mediaDeltaMais1 = 0.0;
        double mediaDeltaMenos1 = 0.0;
        int utilMais1 = 0;
        int utilMenos1 = 0;
        for (ReconciliationLog.UtilitySample sample : utilitySamples) {
            if (sample.uMais1 != null) {
                mediaDeltaMais1 += (sample.uMais1 - sample.uAtual);
                utilMais1++;
            }
            if (sample.uMenos1 != null) {
                mediaDeltaMenos1 += (sample.uMenos1 - sample.uAtual);
                utilMenos1++;
            }
        }
        if (utilMais1 > 0) mediaDeltaMais1 /= utilMais1;
        if (utilMenos1 > 0) mediaDeltaMenos1 /= utilMenos1;

        int condicionamentosAltos = 0;
        int fallbacks = 0;
        for (ReconciliationLog.ConditioningSample sample : conditioningSamples) {
            if (!Double.isFinite(sample.conditionNumber) || sample.conditionNumber > 1.0e12) {
                condicionamentosAltos++;
            }
            if (sample.usouFallback) {
                fallbacks++;
            }
        }

        String verdict;
        String acao;
        String motivo;

        boolean energiaApertada = energiaFinal < Math.max(20f, energiaInicial * 0.25f) || taxaQuedaEnergia > 2.0;
        boolean reconEficiente = mediaReducaoRecon > 15.0 && mediaErroPos < 35.0;
        boolean geometriaInstavel = condicionamentosAltos > 0 || fallbacks > 0;
        boolean adicionarCompensa = mediaDeltaMais1 > 0.01 && !energiaApertada && !geometriaInstavel;
        boolean removerCompensa = mediaDeltaMenos1 > 0.01 || energiaApertada;

        if (adicionarCompensa && !removerCompensa) {
            verdict = "Diagnóstico: o sistema está em zona de expansão controlada.";
            acao = "Próxima ação: considerar adicionar canhões nos clusters com maior ganho marginal.";
            motivo = String.format(java.util.Locale.US,
                    "Utilidade média favorece U(N+1) em %.3f, com energia ainda folgada e condicionamento estável.",
                    mediaDeltaMais1);
        } else if (removerCompensa) {
            verdict = "Diagnóstico: o sistema está operando perto do limite energético.";
            acao = "Próxima ação: remover ou realocar canhões de menor contribuição antes que o desempenho caia.";
            motivo = String.format(java.util.Locale.US,
                    "U(N-1) ficou melhor em %.3f e a energia final caiu para %.1f.",
                    mediaDeltaMenos1, energiaFinal);
        } else {
            verdict = "Diagnóstico: o sistema está em equilíbrio tático.";
            acao = "Próxima ação: manter a frota atual e priorizar realocação fina.";
            motivo = String.format(java.util.Locale.US,
                    "Reconciliação média de %.1f%% e utilidade marginal próxima do limiar indicam estabilidade.",
                    mediaReducaoRecon);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(verdict).append('\n');
        sb.append(acao).append('\n');
        sb.append(motivo).append('\n');
        sb.append(String.format(java.util.Locale.US,
                "Energia inicial/final: %.1f -> %.1f | Queda média: %.2f por amostra\n",
                energiaInicial, energiaFinal, taxaQuedaEnergia));
        sb.append(String.format(java.util.Locale.US,
                "Reconciliação: redução média de MSE %.1f%% | erro posicional médio %.2f px\n",
                mediaReducaoRecon, mediaErroPos));
        sb.append(String.format(java.util.Locale.US,
                "Utilidade: ΔU(N+1)=%.3f | ΔU(N-1)=%.3f | Fallbacks numéricos=%d\n",
                mediaDeltaMais1, mediaDeltaMenos1, fallbacks));

        if (geometriaInstavel) {
            sb.append("Atenção: a geometria está numericamente sensível; redistribuir canhões ajuda a estabilizar a reconciliação.");
        } else {
            sb.append("A geometria está estável o suficiente para apoiar decisões táticas automáticas.");
        }

        return sb.toString();
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
