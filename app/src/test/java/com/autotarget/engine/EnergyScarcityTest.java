package com.autotarget.engine;

import com.autotarget.model.Alvo;
import com.autotarget.model.AlvoComum;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.assertTrue;

public class EnergyScarcityTest {

    @Test
    public void testEnergyRewardCap() throws Exception {
        Jogo jogo = new Jogo();
        
        // Usar reflexão para testar o método privado calcularEnergiaRegenerada
        Method method = Jogo.class.getDeclaredMethod("calcularEnergiaRegenerada", Alvo.class);
        method.setAccessible(true);
        
        // Criar um alvo novo (idade < 2000ms)
        AlvoComum alvo = new AlvoComum(100, 100, 30, 3, 1000, 1000);
        
        float energia = (float) method.invoke(jogo, alvo);
        
        // Deve ser exatamente 1.0f (não 5.0f como antes)
        assertTrue("Energia regenerada deve ser <= 1.0f: " + energia, energia <= 1.0f);
    }
}