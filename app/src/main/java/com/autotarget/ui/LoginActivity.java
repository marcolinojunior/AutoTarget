/*
 * ============================================================================
 * Arquivo: LoginActivity.java
 * Pacote:  com.autotarget.ui
 * ============================================================================
 *
 * DESCRIÇÃO TÉCNICA:
 *   Tela de autenticação Firebase Auth com Email/Senha.
 *   Cada usuário tem seu próprio conjunto de partidas (Multi-Tenant).
 *
 * ============================================================================
 */
package com.autotarget.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.R;
import com.autotarget.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.app.Activity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Tela de login/registro com Firebase Authentication.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth auth;
    private EditText etEmail, etSenha;
    private GoogleSignInClient mGoogleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "Google SignIn ResultCode: " + result.getResultCode());
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            Log.d(TAG, "Google conta obtida: " + account.getEmail());
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.e(TAG, "Google sign in falhou. Código: " + e.getStatusCode(), e);
                        Toast.makeText(this, "Falha no login com Google (Erro: " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.w(TAG, "Login com Google cancelado ou UI falhou. ResultCode: " + result.getResultCode());
                    Toast.makeText(this, "Login com Google cancelado. ResultCode: " + result.getResultCode(), Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Verificar se já está logado
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            navegarParaMain();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etSenha = findViewById(R.id.etSenha);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnAbrirCadastro = findViewById(R.id.btnAbrirCadastro);
        Button btnAnonimo = findViewById(R.id.btnAnonimo);
        Button btnGoogle = findViewById(R.id.btnGoogle);

        btnLogin.setOnClickListener(v -> fazerLogin());
        btnAbrirCadastro.setOnClickListener(v -> startActivity(new Intent(this, CadastroActivity.class)));
        btnAnonimo.setOnClickListener(v -> loginAnonimo());
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
    }

    private void fazerLogin() {
        String email = etEmail.getText().toString().trim();
        String senha = etSenha.getText().toString().trim();

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha email e senha", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Login bem-sucedido");
                        navegarParaMain();
                    } else {
                        Log.e(TAG, "Erro no login", task.getException());
                        Toast.makeText(this, "Erro: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Login com Google bem-sucedido");
                        navegarParaMain();
                    } else {
                        Log.e(TAG, "Erro no login com Google", task.getException());
                        Toast.makeText(this, "Erro: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }


    private void loginAnonimo() {
        auth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Login anônimo");
                        navegarParaMain();
                    } else {
                        Log.e(TAG, "Erro no login anônimo", task.getException());
                        Toast.makeText(this, "Erro no login anônimo", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navegarParaMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
