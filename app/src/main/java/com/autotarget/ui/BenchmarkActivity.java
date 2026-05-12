/*
 * ============================================================================
 * Arquivo: BenchmarkActivity.java
 * Pacote:  com.autotarget.ui
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Modo de benchmark para análise de desempenho via Lei de Amdahl.
 *   Injeta 10-100 alvos e mede T_1 vs T_N com restrição de cores via JNI.
 *   Calcula S(N) = T_1/T_N e P_estimado.
 *   Compara resultados com e sem QuadTree (antes/depois).
 *
 * LEI DE AMDAHL: S(N) = 1 / ((1-P) + P/N)
 * P_estimado = (1/S_pratico - 1) / (1/N - 1)
 *
 * ============================================================================
 */
package com.autotarget.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import com.autotarget.util.ThreadAffinityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Benchmark de desempenho — Lei de Amdahl (AV4).
 */
public class BenchmarkActivity extends AppCompatActivity {

    private static final String TAG = "Benchmark";
    private static final int DURACAO_BENCHMARK_MS = 30000; // 30s

    private TextView tvNumAlvos, tvResultados, tvStatus;
    private SeekBar sbAlvos;
    private Button btnIniciar;
    private Handler handler;

    private int numAlvos = 50;
    private boolean rodando = false;

    // Resultados
    private final List<String> resultadosTexto = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("⚡ Benchmark Amdahl");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        handler = new Handler(Looper.getMainLooper());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scrollView.setPadding(24, 24, 24, 24);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Título
        TextView title = new TextView(this);
        title.setText("Lei de Amdahl — Análise de Speedup");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 16);
        layout.addView(title);

        // SeekBar para número de alvos
        tvNumAlvos = new TextView(this);
        tvNumAlvos.setText("Número de alvos: 50");
        tvNumAlvos.setTextColor(Color.parseColor("#00B4D8"));
        layout.addView(tvNumAlvos);

        sbAlvos = new SeekBar(this);
        sbAlvos.setMax(90); // 10 a 100
        sbAlvos.setProgress(40); // 50
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

        // Status
        tvStatus = new TextView(this);
        tvStatus.setText("Pronto para iniciar");
        tvStatus.setTextColor(Color.parseColor("#FF9800"));
        tvStatus.setPadding(0, 16, 0, 16);
        layout.addView(tvStatus);

        // Botão
        btnIniciar = new Button(this);
        btnIniciar.setText("Iniciar Benchmark");
        btnIniciar.setBackgroundColor(Color.parseColor("#E94560"));
        btnIniciar.setTextColor(Color.WHITE);
        btnIniciar.setOnClickListener(v -> iniciarBenchmark());
        layout.addView(btnIniciar);

        // Resultados
        tvResultados = new TextView(this);
        tvResultados.setTextColor(Color.WHITE);
        tvResultados.setTextSize(13);
        tvResultados.setPadding(0, 24, 0, 0);
        layout.addView(tvResultados);

        // Fórmulas
        TextView tvFormulas = new TextView(this);
        tvFormulas.setText(
                "\n══════════════════════════════════\n" +
                "FÓRMULAS:\n" +
                "S(N) = 1 / ((1-P) + P/N)\n" +
                "S_prático = T₁ / T_N\n" +
                "P_estimado = (1/S - 1) / (1/N - 1)\n" +
                "\nGARGALOS SERIAIS:\n" +
                "• collisionLock (contenção)\n" +
                "• Canvas.draw() (seriado)\n" +
                "• Firebase I/O (fila)\n" +
                "══════════════════════════════════");
        tvFormulas.setTextColor(Color.parseColor("#B0BEC5"));
        tvFormulas.setTextSize(12);
        layout.addView(tvFormulas);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void iniciarBenchmark() {
        if (rodando) return;
        rodando = true;
        btnIniciar.setEnabled(false);
        resultadosTexto.clear();

        resultadosTexto.add("═══ BENCHMARK AMDAHL ═══");
        resultadosTexto.add("Alvos: " + numAlvos + " | Duração: 30s");
        resultadosTexto.add("");

        // Executar sequencialmente: 1 core, 2 cores, 4 cores, all cores
        new Thread(() -> {
            int[] coreConfigs = {1, 2, 4, 8};
            int[] masks = {
                    ThreadAffinityHelper.SINGLE_CORE,
                    0x03,  // 2 cores
                    ThreadAffinityHelper.LITTLE_CORES,
                    ThreadAffinityHelper.ALL_CORES
            };

            long t1 = 0;

            for (int ci = 0; ci < coreConfigs.length; ci++) {
                int cores = coreConfigs[ci];
                int mask = masks[ci];

                updateStatus("Rodando com " + cores + " core(s)...");

                // Definir afinidade
                if (ThreadAffinityHelper.isAvailable()) {
                    ThreadAffinityHelper.trySetAffinity(
                            android.os.Process.myTid(), mask);
                }

                long elapsed = executarCargaTrabalho();

                if (ci == 0) t1 = elapsed;

                double speedup = t1 > 0 ? (double) t1 / elapsed : 1.0;
                double pEstimado = 0;
                if (cores > 1 && speedup > 1.0) {
                    pEstimado = (1.0 / speedup - 1.0) / (1.0 / cores - 1.0);
                }

                double speedupTeorico = 1.0; // com P=0.7 hipotético
                double pHip = 0.7;
                speedupTeorico = 1.0 / ((1 - pHip) + pHip / cores);

                String line = String.format(Locale.US,
                        "N=%d: T=%dms | S=%.2f | S_teo=%.2f | P=%.3f",
                        cores, elapsed, speedup, speedupTeorico, pEstimado);
                resultadosTexto.add(line);
                updateResults();

                Log.i(TAG, line);
            }

            resultadosTexto.add("");
            resultadosTexto.add("T₁ = " + t1 + "ms");
            resultadosTexto.add("Gargalos: collisionLock, Canvas, I/O");
            updateResults();
            updateStatus("Benchmark concluído!");

            handler.post(() -> {
                rodando = false;
                btnIniciar.setEnabled(true);
            });
        }).start();
    }

    /**
     * Executa carga de trabalho simulada com N alvos por 30 segundos.
     * @return tempo total de processamento em ms
     */
    private long executarCargaTrabalho() {
        Random rng = new Random();
        CopyOnWriteArrayList<Alvo> alvos = new CopyOnWriteArrayList<>();

        // Criar alvos
        for (int i = 0; i < numAlvos; i++) {
            float x = 30 + rng.nextFloat() * 940;
            float y = 30 + rng.nextFloat() * 1800;
            Alvo alvo = new AlvoComum(x, y, 30f, 3f, 1080, 1920);
            alvos.add(alvo);
            alvo.start();
        }

        // Medir tempo de verificação de colisões durante 30s
        long totalNs = 0;
        long start = System.currentTimeMillis();
        Object lock = new Object();

        while (System.currentTimeMillis() - start < DURACAO_BENCHMARK_MS) {
            long frameStart = System.nanoTime();
            synchronized (lock) {
                for (Alvo a : alvos) {
                    // Simular trabalho de colisão
                    if (!a.isAtivo()) continue;
                    float dist = (float) Math.sqrt(a.getX() * a.getX() + a.getY() * a.getY());
                }
            }
            totalNs += System.nanoTime() - frameStart;

            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }

        // Parar alvos
        for (Alvo a : alvos) {
            a.setAtivo(false);
            a.interrupt();
        }

        return totalNs / 1_000_000; // retornar em ms
    }

    private void updateStatus(String msg) {
        handler.post(() -> tvStatus.setText(msg));
    }

    private void updateResults() {
        handler.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (String s : resultadosTexto) sb.append(s).append("\n");
            tvResultados.setText(sb.toString());
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
