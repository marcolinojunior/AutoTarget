package com.autotarget.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.R;
import com.autotarget.MainActivity;
import com.google.firebase.auth.FirebaseAuth;

public class CadastroActivity extends AppCompatActivity {

    private static final String TAG = "CadastroActivity";
    private FirebaseAuth auth;
    private EditText etEmail, etSenha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro);

        auth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmailCadastro);
        etSenha = findViewById(R.id.etSenhaCadastro);
        Button btnEfetuarCadastro = findViewById(R.id.btnEfetuarCadastro);
        Button btnVoltarLogin = findViewById(R.id.btnVoltarLogin);

        btnEfetuarCadastro.setOnClickListener(v -> registrar());
        btnVoltarLogin.setOnClickListener(v -> finish());
    }

    private void registrar() {
        String email = etEmail.getText().toString().trim();
        String senha = etSenha.getText().toString().trim();

        if (email.isEmpty() || senha.length() < 6) {
            Toast.makeText(this, "Email e senha (mín. 6 caracteres)", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Registro bem-sucedido");
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.e(TAG, "Erro no registro", task.getException());
                        Toast.makeText(this, "Erro: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
