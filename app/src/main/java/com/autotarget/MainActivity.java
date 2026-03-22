package com.autotarget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private Button btnCanhaoEsquerdo;
    private Button btnCanhaoDireito;
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
        setContentView(R.layout.activity_main);

        jogo = new Jogo();
        jogo.setListener(this);

        FrameLayout container = findViewById(R.id.gameContainer);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnCanhaoEsquerdo = findViewById(R.id.btnCanhaoEsquerdo);
        btnCanhaoDireito = findViewById(R.id.btnCanhaoDireito);
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
                }
                atualizarBotaoIniciar();
            } catch (JogoException e) {
                Log.e(TAG, "Erro ao iniciar jogo", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Canhão Esquerdo
        btnCanhaoEsquerdo.setOnClickListener(v -> adicionarCanhaoNoLado(Lado.ESQUERDO));

        // Canhão Direito
        btnCanhaoDireito.setOnClickListener(v -> adicionarCanhaoNoLado(Lado.DIREITO));
    }

    /**
     * Adiciona um canhão no lado especificado, posicionado automaticamente
     * na metade correspondente da tela em layout de grid.
     */
    private void adicionarCanhaoNoLado(Lado lado) {
        try {
            int largura = gameSurfaceView.getWidth();
            int altura = gameSurfaceView.getHeight();

            if (largura <= 0 || altura <= 0) {
                Toast.makeText(this, "Aguarde o canvas carregar", Toast.LENGTH_SHORT).show();
                return;
            }

            float meioX = largura / 2f;
            int colunas = 3;
            int count;
            float baseX;

            if (lado == Lado.ESQUERDO) {
                count = canhaoCountEsq;
                // Posicionar na metade esquerda
                int col = count % colunas;
                int row = count / colunas;
                float espaco = meioX / (colunas + 1);
                baseX = espaco * (col + 1);
                float y = altura - 80f - (row * 70f);

                jogo.adicionarCanhao(baseX, y, Lado.ESQUERDO);
                canhaoCountEsq++;
                Toast.makeText(this, "Canhão ESQ #" + canhaoCountEsq, Toast.LENGTH_SHORT).show();
            } else {
                count = canhaoCountDir;
                int col = count % colunas;
                int row = count / colunas;
                float espaco = meioX / (colunas + 1);
                baseX = meioX + espaco * (col + 1);
                float y = altura - 80f - (row * 70f);

                jogo.adicionarCanhao(baseX, y, Lado.DIREITO);
                canhaoCountDir++;
                Toast.makeText(this, "Canhão DIR #" + canhaoCountDir, Toast.LENGTH_SHORT).show();
            }
        } catch (JogoException e) {
            Log.w(TAG, "Erro ao adicionar canhão", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void reiniciarJogo() {
        jogo = new Jogo();
        jogo.setListener(this);
        jogo.setDimensoesTela(gameSurfaceView.getWidth(), gameSurfaceView.getHeight());
        gameSurfaceView.setJogo(jogo);
        canhaoCountEsq = 0;
        canhaoCountDir = 0;

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
                            "Esquerdo: " + pontEsq + " pontos\n" +
                            "Direito: " + pontDir + " pontos\n\n" +
                            "Tempo: " + tempoTotal + "s\n" +
                            "Reconciliações: " + reconciliacoes + "\n" +
                            "Canhões ESQ: " + canhaoCountEsq +
                            " | DIR: " + canhaoCountDir + "\n\n" +
                            "Resultado salvo no Firestore ✓")
                    .setPositiveButton("Jogar Novamente", (d, w) -> reiniciarJogo())
                    .setNegativeButton("Fechar", null)
                    .setCancelable(true)
                    .show();

            atualizarBotaoIniciar();
        });
    }
}
