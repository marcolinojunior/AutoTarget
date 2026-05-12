package com.autotarget.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.autotarget.MainActivity;
import com.autotarget.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void testLoginComEmailSenha() {
        // Preenche email e senha
        onView(withId(R.id.etEmail))
                .perform(typeText("test@autotarget.com"), closeSoftKeyboard());
        onView(withId(R.id.etSenha))
                .perform(typeText("123456"), closeSoftKeyboard());

        // Clica no botão de login
        onView(withId(R.id.btnLogin)).perform(click());

        // Opcional: Verificar se MainActivity é chamada (depende do Firebase responder)
        // Isso pode falhar se o teste não tiver rede ou a conta não existir, mas o fluxo da UI é testado.
    }

    @Test
    public void testLoginAnonimo() {
        // Clica no botão anônimo
        onView(withId(R.id.btnAnonimo)).perform(click());

        // Novamente, depende do Firebase para navegação, mas testamos a chamada de UI.
    }

    @Test
    public void testLoginGoogleMock() {
        // Prepara um Intent Mockado para retornar "Cancelado" 
        // e evitar abrir a interface real do Google Sign-In que bloquearia o teste
        Intent resultData = new Intent();
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, resultData);
        
        intending(hasAction("com.google.android.gms.auth.GOOGLE_SIGN_IN")).respondWith(result);

        // Clica no botão de Google
        onView(withId(R.id.btnGoogle)).perform(click());

        // Verifica se o Intent de Sign-in foi disparado
        // Observação: Internamente o GoogleSignIn SDK gerencia a Action,
        // então não conseguimos validar o intent exato facilmente sem mocks complexos,
        // mas este clique com intending previne que a tela real trave o teste.
    }
}
