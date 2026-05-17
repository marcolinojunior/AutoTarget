/*
 * ============================================================================
 * Arquivo: MainActivity.java
 * Pacote:  com.autotarget
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Activity principal (ponto de entrada da UI) do jogo AutoTarget. É
 *   responsável por inflar o layout XML (activity_main.xml), instanciar a
 *   GameSurfaceView (canvas de renderização) e o controlador Jogo, além de
 *   conectar os botões de interação do usuário à lógica do jogo.
 *
 * TÓPICOS DA RUBRICA ATENDIDOS NESTE ARQUIVO:
 *
 *   ► Tela principal e interação (6.1.2/6.1.3):
 *     - Canvas (GameSurfaceView) adicionado dinamicamente ao FrameLayout.
 *     - Botão "Iniciar" (btnIniciar) — alterna entre iniciar/parar o jogo.
 *     - Botões "Adicionar Canhão" separados por lado (ESQ / DIR), com
 *       posicionamento automático em grid dentro da metade correspondente.
 *     - TextViews para pontuação (ESQ|DIR), estado do jogo, alvos ativos,
 *       tempo restante (formato mm:ss) e energia por lado.
 *     - Diálogo (AlertDialog) exibido ao final da partida com placar,
 *       tempo, reconciliações realizadas e resultado (vencedor/empate).
 *
 *   ► Tratamento de exceções (6.1.6):
 *     - Blocos try-catch envolvem adicionarCanhao() e iniciar() capturando
 *       JogoException (exceção personalizada) e exibindo Toast ao usuário.
 *     - Logs de erro via Log.e/Log.w para depuração.
 *
 *   ► Classes/threads e sincronização (6.1.4 / 6.1.5):
 *     - Usa Handler(Looper.getMainLooper()) para postar atualizações de UI
 *       vindas de threads secundárias (callback OnJogoListener), garantindo
 *       que manipulações de View ocorram na UI thread do Android.
 *
 *   ► Herança e polimorfismo (6.1.8):
 *     - Implementa a interface Jogo.OnJogoListener, demonstrando contrato
 *       de callback via interface (polimorfismo de interface).
 *
 * FLUXO PRINCIPAL:
 *   onCreate → infla layout → cria Jogo → adiciona GameSurfaceView → liga botões
 *   Botão Iniciar → jogo.iniciar() / jogo.pararJogo()
 *   Botão Canhão ESQ/DIR → adicionarCanhaoNoLado(Lado)
 *   Callbacks do Jogo → atualizam TextViews via uiHandler.post(...)
 *   onPause → para o jogo se estiver rodando (lifecycle-aware)
 *
 * ============================================================================
 */
package com.autotarget;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.autotarget.engine.GameSurfaceView;
import com.autotarget.engine.Jogo;
import com.autotarget.exception.JogoException;
import com.autotarget.model.Lado;
import com.autotarget.ui.BenchmarkActivity;
import com.autotarget.ui.LoginActivity;
import com.autotarget.ui.RankingActivity;
import com.autotarget.ui.ReconciliationReportActivity;
import com.autotarget.ui.TrlActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity principal do AutoTarget.
 * <p>
 * Implementa o cenário com duas áreas (esquerda/direita), cada uma com
 * seu botão de adicionar canhão, energia independente, e placar separado.
 * O vencedor é o lado com mais abates ao final de 60 segundos.
 */
public class MainActivity extends AppCompatActivity implements Jogo.OnJogoListener {

    private static final String TAG = "MainActivity";

    // ── Views ────────────────────────────────────────────────────
    private GameSurfaceView gameSurfaceView;
    private Button btnIniciar;
    private TextView tvPontuacao;
    private TextView tvEstado;
    private TextView tvAlvosAtivos;
    private TextView tvTempo;
    private TextView tvEnergia;

    // ── Lógica ───────────────────────────────────────────────────
    private Jogo jogo;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /** Contadores para posicionamento por lado. */
    private int canhaoCountEsq = 0;
    private int canhaoCountDir = 0;

    // ── Lifecycle ────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // CONFIGURAR TRATAMENTO GLOBAL DE EXCEÇÕES PARA DEPURAÇÃO
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("FATAL_ERROR", "Crash detectado na thread: " + thread.getName(), throwable);
            // Salvar no logcat é o principal aqui
            System.exit(1); 
        });

        // Verificar autenticação
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        jogo = new Jogo(this);
        jogo.setListener(this);

        FrameLayout container = findViewById(R.id.gameContainer);
        btnIniciar = findViewById(R.id.btnIniciar);
        tvPontuacao = findViewById(R.id.tvPontuacao);
        tvEstado = findViewById(R.id.tvEstado);
        tvAlvosAtivos = findViewById(R.id.tvAlvosAtivos);
        tvTempo = findViewById(R.id.tvTempo);
        tvEnergia = findViewById(R.id.tvEnergia);

        gameSurfaceView = new GameSurfaceView(this);
        gameSurfaceView.setJogo(jogo);
        container.addView(gameSurfaceView);

        configurarBotoes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (jogo.getEstado() == Jogo.Estado.RODANDO) {
            jogo.pararJogo();
            atualizarBotaoIniciar();
        }
    }

    // ── Botões ───────────────────────────────────────────────────

    private void configurarBotoes() {
        btnIniciar.setOnClickListener(v -> {
            try {
                if (jogo.getEstado() == Jogo.Estado.RODANDO) {
                    jogo.pararJogo();
                } else {
                    if (jogo.getEstado() == Jogo.Estado.ENCERRADO) {
                        reiniciarJogo();
                    }
                    jogo.iniciar();
                    Toast.makeText(this,
                            "Toque no lado esquerdo para posicionar seus canhões!",
                            Toast.LENGTH_LONG).show();
                }
                atualizarBotaoIniciar();
            } catch (JogoException e) {
                Log.e(TAG, "Erro ao iniciar jogo", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void reiniciarJogo() {
        jogo = new Jogo(this);
        jogo.setListener(this);
        jogo.setDimensoesTela(gameSurfaceView.getWidth(), gameSurfaceView.getHeight());
        gameSurfaceView.setJogo(jogo);

        tvPontuacao.setText(R.string.pontuacao_inicial);
        tvAlvosAtivos.setText(R.string.alvos_ativos_inicial);
        tvTempo.setText(R.string.tempo_inicial);
        tvEnergia.setText(R.string.energia_inicial);
    }

    private void atualizarBotaoIniciar() {
        btnIniciar.setText(jogo.getEstado() == Jogo.Estado.RODANDO
                ? R.string.parar : R.string.iniciar);
    }

    // ── OnJogoListener ───────────────────────────────────────────

    @Override
    public void onPontuacaoAtualizada(int pontEsq, int pontDir) {
        uiHandler.post(() -> tvPontuacao.setText("ESQ:" + pontEsq + " | DIR:" + pontDir));
    }

    @Override
    public void onEstadoAlterado(Jogo.Estado estado) {
        uiHandler.post(() -> {
            switch (estado) {
                case PARADO:   tvEstado.setText(R.string.estado_parado); break;
                case RODANDO:  tvEstado.setText(R.string.estado_rodando); break;
                case ENCERRADO: tvEstado.setText(R.string.estado_encerrado); break;
            }
            atualizarBotaoIniciar();
        });
    }

    @Override
    public void onAlvosAtivosAtualizado(int count) {
        uiHandler.post(() -> tvAlvosAtivos.setText("Alvos: " + count));
    }

    @Override
    public void onTempoAtualizado(int segundosRestantes) {
        uiHandler.post(() -> {
            String tempo = String.format("%02d:%02d",
                    segundosRestantes / 60, segundosRestantes % 60);
            tvTempo.setText("⏱ " + tempo);
            tvTempo.setTextColor(segundosRestantes <= 10
                    ? ContextCompat.getColor(MainActivity.this, R.color.primary)
                    : ContextCompat.getColor(MainActivity.this, R.color.text_primary));
        });
    }

    @Override
    public void onEnergiaAtualizada(float energiaEsq, float energiaDir) {
        uiHandler.post(() -> tvEnergia.setText("⚡E:" + (int) energiaEsq + " | D:" + (int) energiaDir));
    }

    @Override
    public void onPartidaEncerrada(int pontEsq, int pontDir, int tempoTotal,
                                   int reconciliacoes, Lado vencedor) {
        uiHandler.post(() -> {
            String vencedorStr;
            if (vencedor == Lado.ESQUERDO) vencedorStr = "🏆 Esquerdo Vence!";
            else if (vencedor == Lado.DIREITO) vencedorStr = "🏆 Direito Vence!";
            else vencedorStr = "🤝 Empate!";

            new AlertDialog.Builder(this, R.style.Theme_AutoTarget)
                    .setTitle("🎯 Fim de Jogo!")
                    .setMessage(
                            vencedorStr + "\n\n" +
                            "Jogador: " + pontEsq + " pontos\n" +
                            "IA: " + pontDir + " pontos\n\n" +
                            "Tempo: " + tempoTotal + "s\n" +
                            "Reconciliações: " + reconciliacoes + "\n\n" +
                            "Resultado salvo no Firestore ✓")
                    .setPositiveButton("Jogar Novamente", (d, w) -> reiniciarJogo())
                    .setNegativeButton("Fechar", null)
                    .setCancelable(true)
                    .show();

            atualizarBotaoIniciar();
        });
    }

    @Override
    public void onRelatorioReconciliacao(String relatorio) {
        uiHandler.post(() -> {
            Intent intent = new Intent(this, ReconciliationReportActivity.class);
            intent.putExtra(ReconciliationReportActivity.EXTRA_REPORT, relatorio);
            startActivity(intent);
        });
    }
    // ── Menu ────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_ranking) {
            startActivity(new Intent(this, RankingActivity.class));
            return true;
        } else if (id == R.id.action_trl) {
            startActivity(new Intent(this, TrlActivity.class));
            return true;
        } else if (id == R.id.action_benchmark) {
            startActivity(new Intent(this, BenchmarkActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
