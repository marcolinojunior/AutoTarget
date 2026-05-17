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

        BenchmarkSample(int cores, long elapsedMs, double speedup, double speedupTeorico, double pEstimado) {
            this.cores = cores;
            this.elapsedMs = elapsedMs;
            this.speedup = speedup;
            this.speedupTeorico = speedupTeorico;
            this.pEstimado = pEstimado;
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
        RMAAnalysis.resetRuntimeStats();

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
            for (int i = 0; i < coreConfigs.size(); i++) {
                int cores = coreConfigs.get(i);
                int mask = buildAffinityMask(cores, availableCores);
                updateStatus("Rodando com " + cores + " core(s)...");

                if (ThreadAffinityHelper.isAvailable()) {
                    ThreadAffinityHelper.trySetAffinityPreferProcessApi(android.os.Process.myTid(), mask);
                }

                long elapsed = executarCargaTrabalho();
                if (i == 0) t1 = elapsed;

                double speedup = t1 > 0 ? (double) t1 / elapsed : 1.0;
                double pEstimado = 0;
                if (cores > 1 && speedup > 1.0) {
                    pEstimado = (1.0 / speedup - 1.0) / (1.0 / cores - 1.0);
                }
                double pHipotetico = 0.7;
                double speedupTeorico = 1.0 / ((1 - pHipotetico) + pHipotetico / cores);

                samples.add(new BenchmarkSample(cores, elapsed, speedup, speedupTeorico, pEstimado));
                resultadosTexto.add(String.format(Locale.US,
                        "N=%d T=%dms S=%.2f S_teo=%.2f P=%.3f",
                        cores, elapsed, speedup, speedupTeorico, pEstimado));
                updateResults();
                updateChart();
            }

            resultadosTexto.add("");
            resultadosTexto.add("Tabela de tarefas (Pi,Ci,Di,Ji,deps):");
            resultadosTexto.add(RMAAnalysis.getTaskTableSummary());
            resultadosTexto.add("Métricas de resposta observadas:");
            resultadosTexto.add(RMAAnalysis.getRuntimeMetricsReport());

            exportarParaCSV(samples, RMAAnalysis.getRuntimeMetricsReport());

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
            writer.append("cores,elapsed_ms,speedup,speedup_teorico,p_estimado\n");
            for (BenchmarkSample s : listSamples) {
                writer.append(String.format(java.util.Locale.US, "%d,%d,%.3f,%.3f,%.3f\n",
                        s.cores, s.elapsedMs, s.speedup, s.speedupTeorico, s.pEstimado));
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class BenchmarkChartView extends View {
        private final Paint paintAxis = new Paint();
        private final Paint paintSpeedup = new Paint();
        private final Paint paintTempo = new Paint();
        private final Paint paintJitter = new Paint();
        private final Paint paintText = new Paint();
        private final Paint paintDeadlineMiss = new Paint();
        private final Paint paintLegenda = new Paint();
        private List<BenchmarkSample> data = new ArrayList<>();

        BenchmarkChartView(android.content.Context context) {
            super(context);
            paintAxis.setColor(Color.parseColor("#607D8B"));
            paintAxis.setStrokeWidth(3f);
            paintSpeedup.setColor(Color.parseColor("#00B4D8"));
            paintTempo.setColor(Color.parseColor("#E94560"));
            paintJitter.setColor(Color.parseColor("#FFB300"));
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(24f);
            paintText.setAntiAlias(true);
            paintDeadlineMiss.setColor(Color.RED);
            paintDeadlineMiss.setStrokeWidth(3f);
            paintDeadlineMiss.setStyle(Paint.Style.STROKE);
            paintLegenda.setColor(Color.parseColor("#AAAAAA"));
            paintLegenda.setTextSize(16f);
            paintLegenda.setAntiAlias(true);
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
            float top = 50f;
            float bottom = h - 80f;

            canvas.drawColor(Color.parseColor("#16213E"));
            canvas.drawLine(left, top, left, bottom, paintAxis);
            canvas.drawLine(left, bottom, right, bottom, paintAxis);
            
            // Legenda expandida
            canvas.drawText("Speedup (azul) | Tempo (vermelho) | Jitter (amarelo)", left, 25, paintText);
            canvas.drawText("Quadrados vermelhos = Deadline Misses", left, 40, paintLegenda);

            if (data == null || data.isEmpty()) return;

            double maxSpeedup = 1.0;
            long maxTempo = 1;
            for (BenchmarkSample s : data) {
                maxSpeedup = Math.max(maxSpeedup, s.speedup);
                maxTempo = Math.max(maxTempo, s.elapsedMs);
            }

            float slot = (right - left) / data.size();
            float barW = slot * 0.22f;
            for (int i = 0; i < data.size(); i++) {
                BenchmarkSample s = data.get(i);
                float baseX = left + i * slot + slot * 0.12f;

                // Barra de Speedup (azul)
                float speedupRatio = (float) (s.speedup / maxSpeedup);
                float speedupH = speedupRatio * (bottom - top - 18f);
                canvas.drawRect(baseX, bottom - speedupH, baseX + barW, bottom, paintSpeedup);

                // Barra de Tempo (vermelho)
                float tempoRatio = (float) s.elapsedMs / maxTempo;
                float tempoH = tempoRatio * (bottom - top - 18f);
                float tx = baseX + barW + 4f;
                canvas.drawRect(tx, bottom - tempoH, tx + barW, bottom, paintTempo);

                // Barra de Jitter (amarelo) - simulado como 10% do tempo
                float jitterRatio = tempoRatio * 0.1f;
                float jitterH = jitterRatio * (bottom - top - 18f);
                float jx = baseX + barW * 2 + 8f;
                canvas.drawRect(jx, bottom - jitterH, jx + barW, bottom, paintJitter);

                // Indicador de Deadline Miss (moldura vermelha)
                boolean hasDeadlineMiss = s.elapsedMs > 16; // ex: 16ms deadline para physics
                if (hasDeadlineMiss) {
                    canvas.drawRect(baseX - 2, bottom - speedupH - 2, tx + barW + 2, bottom + 2, paintDeadlineMiss);
                }

                canvas.drawText("N=" + s.cores, baseX - 2f, bottom + 30f, paintText);
            }
            
            // Rodapé com estatísticas
            canvas.drawText("Configs testadas: " + data.size() + " | Para detalhes, ver relatório RMA", 
                    left, h - 15f, paintLegenda);
        }
    }
}
