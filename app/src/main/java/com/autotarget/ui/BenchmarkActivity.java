package com.autotarget.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.util.RMAAnalysis;
import com.autotarget.util.ThreadAffinityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Benchmark com comparação de speedup por afinidade de núcleos e métricas de resposta.
 */
public class BenchmarkActivity extends AppCompatActivity {

    private static final int DURACAO_BENCHMARK_MS = 30000;

    private TextView tvNumAlvos, tvResultados, tvStatus;
    private SeekBar sbAlvos;
    private Button btnIniciar;
    private BenchmarkChartView chartView;
    private Handler handler;

    private int numAlvos = 50;
    private boolean rodando = false;

    private final List<String> resultadosTexto = new ArrayList<>();
    private final List<BenchmarkSample> samples = new ArrayList<>();

    private static final class BenchmarkSample {
        final int cores;
        final long elapsedMs;
        final double speedup;
        final double speedupTeorico;
        final double pEstimado;
        final int deadlineMisses;
        final boolean affinityOk;
        final String affinityMethod;

        BenchmarkSample(int cores, long elapsedMs, double speedup, double speedupTeorico, double pEstimado,
                        int deadlineMisses, boolean affinityOk, String affinityMethod) {
            this.cores = cores;
            this.elapsedMs = elapsedMs;
            this.speedup = speedup;
            this.speedupTeorico = speedupTeorico;
            this.pEstimado = pEstimado;
            this.deadlineMisses = deadlineMisses;
            this.affinityOk = affinityOk;
            this.affinityMethod = affinityMethod;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Benchmark AV2/AV4");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        handler = new Handler(Looper.getMainLooper());
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scrollView.setPadding(24, 24, 24, 24);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Speedup por afinidade + tempos de resposta");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 16);
        layout.addView(title);

        tvNumAlvos = new TextView(this);
        tvNumAlvos.setText("Número de alvos: 50");
        tvNumAlvos.setTextColor(Color.parseColor("#00B4D8"));
        layout.addView(tvNumAlvos);

        sbAlvos = new SeekBar(this);
        sbAlvos.setMax(90);
        sbAlvos.setProgress(40);
        sbAlvos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                numAlvos = progress + 10;
                tvNumAlvos.setText("Número de alvos: " + numAlvos);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(sbAlvos);

        tvStatus = new TextView(this);
        tvStatus.setText("Pronto para iniciar");
        tvStatus.setTextColor(Color.parseColor("#FF9800"));
        tvStatus.setPadding(0, 16, 0, 16);
        layout.addView(tvStatus);

        btnIniciar = new Button(this);
        btnIniciar.setText("Iniciar Benchmark");
        btnIniciar.setBackgroundColor(Color.parseColor("#E94560"));
        btnIniciar.setTextColor(Color.WHITE);
        btnIniciar.setOnClickListener(v -> iniciarBenchmark());
        layout.addView(btnIniciar);

        chartView = new BenchmarkChartView(this);
        chartView.setMinimumHeight(420);
        layout.addView(chartView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 420));

        tvResultados = new TextView(this);
        tvResultados.setTextColor(Color.WHITE);
        tvResultados.setTextSize(13);
        tvResultados.setPadding(0, 24, 0, 0);
        layout.addView(tvResultados);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void iniciarBenchmark() {
        if (rodando) return;
        rodando = true;
        btnIniciar.setEnabled(false);
        resultadosTexto.clear();
        samples.clear();

        resultadosTexto.add("═══ BENCHMARK ESCALABILIDADE ═══");
        resultadosTexto.add("Alvos: " + numAlvos + " | Duração: 30s");
        int availableCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        resultadosTexto.add("Configurações: 1, 2, todos os núcleos reais (" + availableCores + ")");
        resultadosTexto.add("");

        new Thread(() -> {
            List<Integer> coreConfigs = new ArrayList<>();
            coreConfigs.add(1);
            if (availableCores >= 2) {
                coreConfigs.add(2);
            }
            if (!coreConfigs.contains(availableCores)) {
                coreConfigs.add(availableCores);
            }

            long t1 = 0;
            StringBuilder metricsByConfig = new StringBuilder();
            for (int i = 0; i < coreConfigs.size(); i++) {
                int cores = coreConfigs.get(i);
                int mask = buildAffinityMask(cores, availableCores);
                updateStatus("Rodando com " + cores + " core(s)...");
                RMAAnalysis.resetRuntimeStats();

                boolean affinityOk = ThreadAffinityHelper.trySetAffinityPreferProcessApi(android.os.Process.myTid(), mask);
                String affinityMethod = ThreadAffinityHelper.getLastAffinityMethod();
                String affinityError = ThreadAffinityHelper.getLastAffinityError();

                long elapsed = executarCargaTrabalho();
                String runtimeMetrics = RMAAnalysis.getRuntimeMetricsReport();
                int deadlineMisses = contarDeadlineMisses(runtimeMetrics);
                if (i == 0) t1 = elapsed;

                double speedup = t1 > 0 ? (double) t1 / elapsed : 1.0;
                double pEstimado = 0;
                if (cores > 1 && speedup > 1.0) {
                    pEstimado = (1.0 / speedup - 1.0) / (1.0 / cores - 1.0);
                }
                double pHipotetico = 0.7;
                double speedupTeorico = 1.0 / ((1 - pHipotetico) + pHipotetico / cores);

                samples.add(new BenchmarkSample(cores, elapsed, speedup, speedupTeorico, pEstimado,
                        deadlineMisses, affinityOk, affinityMethod));
                resultadosTexto.add(String.format(Locale.US,
                        "N=%d T=%dms S=%.2f S_teo=%.2f P=%.3f misses=%d affinity=%s(%s)%s",
                        cores, elapsed, speedup, speedupTeorico, pEstimado,
                        deadlineMisses, affinityMethod, affinityOk ? "ok" : "fail",
                        affinityOk ? "" : " err=" + affinityError));
                metricsByConfig.append(String.format(Locale.US, "## CORES=%d\n", cores));
                metricsByConfig.append(runtimeMetrics).append("\n");
                updateResults();
                updateChart();
            }

            resultadosTexto.add("");
            resultadosTexto.add("Tabela de tarefas (Pi,Ci,Di,Ji,deps):");
            resultadosTexto.add(RMAAnalysis.getTaskTableSummary());
            resultadosTexto.add("Métricas de resposta observadas por configuração:");
            resultadosTexto.add(metricsByConfig.toString());

            exportarParaCSV(samples, metricsByConfig.toString());

            updateResults();
            updateStatus("Benchmark concluído!");

            handler.post(() -> {
                rodando = false;
                btnIniciar.setEnabled(true);
            });
        }, "BenchmarkRunner").start();
    }

    private int buildAffinityMask(int coresToUse, int availableCores) {
        int efetivos = Math.max(1, Math.min(coresToUse, availableCores));
        if (efetivos >= Integer.SIZE - 1) {
            return -1;
        }
        return (1 << efetivos) - 1;
    }

    private long executarCargaTrabalho() {
        Random rng = new Random();
        CopyOnWriteArrayList<Alvo> alvos = new CopyOnWriteArrayList<>();
        Object lock = new Object();

        for (int i = 0; i < numAlvos; i++) {
            float x = 30 + rng.nextFloat() * 940;
            float y = 30 + rng.nextFloat() * 1800;
            Alvo alvo = new AlvoComum(x, y, 30f, 3f, 1080, 1920);
            alvos.add(alvo);
            alvo.start();
        }

        long totalNs = 0;
        long start = System.currentTimeMillis();
        long lastSensorTick = start;

        while (System.currentTimeMillis() - start < DURACAO_BENCHMARK_MS) {
            long frameStart = System.nanoTime();
            synchronized (lock) {
                for (Alvo a : alvos) {
                    if (!a.isAtivo()) continue;
                    float dist = (float) Math.sqrt(a.getX() * a.getX() + a.getY() * a.getY());
                    if (dist < 0) break;
                }
            }
            long frameElapsedMs = (System.nanoTime() - frameStart) / 1_000_000;
            RMAAnalysis.recordExecution("T1-Physics", frameElapsedMs);
            totalNs += System.nanoTime() - frameStart;

            long now = System.currentTimeMillis();
            if (now - lastSensorTick >= 1000) {
                long sensorStart = System.nanoTime();
                int ativos = 0;
                for (Alvo a : alvos) if (a.isAtivo()) ativos++;
                if (ativos < 0) break;
                long sensorElapsedMs = (System.nanoTime() - sensorStart) / 1_000_000;
                RMAAnalysis.recordExecution("T7-Sensor", sensorElapsedMs);
                lastSensorTick = now;
            }

            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        for (Alvo a : alvos) {
            a.setAtivo(false);
            a.interrupt();
        }

        return totalNs / 1_000_000;
    }

    private void exportarParaCSV(List<BenchmarkSample> listSamples, String rmaMetrics) {
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "benchmark_report.csv");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.append("cores,elapsed_ms,speedup,speedup_teorico,p_estimado,deadline_misses,affinity_ok,affinity_method\n");
            for (BenchmarkSample s : listSamples) {
                writer.append(String.format(java.util.Locale.US, "%d,%d,%.3f,%.3f,%.3f,%d,%s,%s\n",
                        s.cores, s.elapsedMs, s.speedup, s.speedupTeorico, s.pEstimado,
                        s.deadlineMisses, s.affinityOk ? "true" : "false", s.affinityMethod));
            }
            writer.append("\n");
            writer.append(rmaMetrics);
            writer.flush();
            writer.close();
            android.util.Log.i("Benchmark", "Relatório CSV exportado para: " + file.getAbsolutePath());
            resultadosTexto.add("\nRelatório salvo em: " + file.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("Benchmark", "Erro ao exportar CSV", e);
            resultadosTexto.add("\nErro ao salvar CSV: " + e.getMessage());
        }
    }

    private void updateStatus(String msg) {
        handler.post(() -> tvStatus.setText(msg));
    }

    private void updateResults() {
        handler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String s : resultadosTexto) {
                sb.append(s).append("\n");
            }
            tvResultados.setText(sb.toString());
        });
    }

    private void updateChart() {
        handler.post(() -> chartView.setData(new ArrayList<>(samples)));
    }

    private int contarDeadlineMisses(String runtimeReport) {
        if (runtimeReport == null || runtimeReport.trim().isEmpty()) return 0;
        int total = 0;
        String[] linhas = runtimeReport.split("\n");
        for (String linha : linhas) {
            if (linha.startsWith("task,") || linha.startsWith("RUNTIME_METRICS") || linha.trim().isEmpty()) {
                continue;
            }
            String[] cols = linha.split(",");
            if (cols.length < 6) continue;
            try {
                total += Integer.parseInt(cols[5].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return total;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class BenchmarkChartView extends View {
        private final Paint paintAxis = new Paint();
        private final Paint paintSpeedup = new Paint();
        private final Paint paintSpeedupTeorico = new Paint();
        private final Paint paintTempo = new Paint();
        private final Paint paintDeadlineMiss = new Paint();
        private final Paint paintText = new Paint();
        private final Paint paintLegenda = new Paint();
        private final Paint paintGrid = new Paint();
        private List<BenchmarkSample> data = new ArrayList<>();

        BenchmarkChartView(android.content.Context context) {
            super(context);
            paintAxis.setColor(Color.parseColor("#607D8B"));
            paintAxis.setStrokeWidth(2f);
            paintSpeedup.setColor(Color.parseColor("#00B4D8"));
            paintSpeedup.setStyle(Paint.Style.FILL);
            paintSpeedupTeorico.setColor(Color.parseColor("#00B4D8"));
            paintSpeedupTeorico.setStyle(Paint.Style.STROKE);
            paintSpeedupTeorico.setStrokeWidth(3f);
            paintTempo.setColor(Color.parseColor("#E94560"));
            paintTempo.setStyle(Paint.Style.FILL);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(22f);
            paintText.setAntiAlias(true);
            paintDeadlineMiss.setColor(Color.RED);
            paintDeadlineMiss.setStrokeWidth(2f);
            paintDeadlineMiss.setStyle(Paint.Style.STROKE);
            paintLegenda.setColor(Color.parseColor("#AAAAAA"));
            paintLegenda.setTextSize(14f);
            paintLegenda.setAntiAlias(true);
            paintGrid.setColor(Color.parseColor("#33FFFFFF"));
            paintGrid.setStrokeWidth(1f);
        }

        void setData(List<BenchmarkSample> data) {
            this.data = data;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float left = 70f;
            float right = w - 20f;
            float top = 60f;
            float bottom = h - 100f;

            canvas.drawColor(Color.parseColor("#16213E"));

            // Título
            canvas.drawText("Speedup: Real vs Amdahl (P=0.7)", left, 25, paintText);
            canvas.drawText("■ Real (azul)  ■ Teórico Amdahl (azul tracejado)  ■ Tempo (vermelho)",
                    left, 45, paintLegenda);

            // Grid horizontal
            for (int i = 0; i <= 5; i++) {
                float y = top + (bottom - top) * i / 5f;
                canvas.drawLine(left, y, right, y, paintGrid);
            }

            // Eixos
            canvas.drawLine(left, top, left, bottom, paintAxis);
            canvas.drawLine(left, bottom, right, bottom, paintAxis);

            if (data == null || data.isEmpty()) {
                canvas.drawText("Aguardando dados do benchmark...", left + 20, (top + bottom) / 2, paintText);
                return;
            }

            double maxSpeedup = 1.0;
            long maxTempo = 1;
            int maxCores = 1;
            for (BenchmarkSample s : data) {
                maxSpeedup = Math.max(maxSpeedup, Math.max(s.speedup, s.speedupTeorico));
                maxTempo = Math.max(maxTempo, s.elapsedMs);
                maxCores = Math.max(maxCores, s.cores);
            }
            // Garantir margem no eixo Y
            maxSpeedup = Math.max(maxSpeedup * 1.1, 1.5);

            // Labels do eixo Y (speedup)
            for (int i = 0; i <= 5; i++) {
                float y = bottom - (bottom - top) * i / 5f;
                double val = maxSpeedup * i / 5.0;
                canvas.drawText(String.format(Locale.US, "%.1f×", val), 5, y + 5, paintLegenda);
            }

            float slot = (right - left) / data.size();
            float groupW = slot * 0.7f;
            float barW = groupW * 0.35f;

            for (int i = 0; i < data.size(); i++) {
                BenchmarkSample s = data.get(i);
                float groupX = left + i * slot + (slot - groupW) / 2f;

                // ── Barra Speedup Real (azul sólido) ──
                float speedupH = (float) (s.speedup / maxSpeedup) * (bottom - top);
                canvas.drawRect(groupX, bottom - speedupH, groupX + barW, bottom, paintSpeedup);

                // Valor sobre a barra
                canvas.drawText(String.format(Locale.US, "%.2f×", s.speedup),
                        groupX + barW / 2f - 15, bottom - speedupH - 5, paintLegenda);

                // ── Barra Speedup Teórico Amdahl (azul tracejado) ──
                float teoricoH = (float) (s.speedupTeorico / maxSpeedup) * (bottom - top);
                canvas.drawRect(groupX + barW + 4, bottom - teoricoH,
                        groupX + barW * 2 + 4, bottom, paintSpeedupTeorico);

                // Valor teórico
                canvas.drawText(String.format(Locale.US, "%.2f×", s.speedupTeorico),
                        groupX + barW + 4, bottom - teoricoH - 5, paintLegenda);

                // ── Barra de Tempo (vermelho, lado direito do grupo) ──
                float tempoH = (float) s.elapsedMs / maxTempo * (bottom - top) * 0.5f;
                float tempoX = groupX + groupW + 4;
                canvas.drawRect(tempoX, bottom - tempoH, tempoX + barW * 0.6f, bottom, paintTempo);

                // ── Indicador Deadline Miss ──
                if (s.deadlineMisses > 0) {
                    canvas.drawRect(groupX - 3, bottom - Math.max(speedupH, teoricoH) - 8,
                            tempoX + barW * 0.6f + 3, bottom + 3, paintDeadlineMiss);
                    canvas.drawText("⚠ " + s.deadlineMisses + " misses",
                            groupX, bottom + 18, paintDeadlineMiss);
                }

                // ── Label do eixo X (N cores) ──
                canvas.drawText("N=" + s.cores, groupX + barW / 2f - 10, bottom + 35, paintText);

                // ── P estimado ──
                if (s.pEstimado > 0) {
                    canvas.drawText(String.format(Locale.US, "P=%.2f", s.pEstimado),
                            groupX, bottom + 55, paintLegenda);
                }
            }

            // Rodapé
            canvas.drawText("Lei de Amdahl: S(N) = 1 / ((1-P) + P/N) | P=serial estimado",
                    left, h - 15, paintLegenda);
        }
    }
}
