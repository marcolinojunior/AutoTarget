with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'r') as f:
    content = f.read()

search = """        // Adiciona posição dos canhões no ruído (para testes de calor)
        if (jogo != null) {
            for (Canhao c : jogo.getCanhoesEsquerdo()) {
                if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
            }
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
            }
        }"""
replace = """        // Adiciona posição dos canhões no ruído (para testes de calor)
        if (jogo != null) {
            synchronized(jogo.getCanhoesLock()) {
                java.util.List<Canhao> canhoesEsq = jogo.getCanhoesEsquerdo();
                for (int i = 0; i < canhoesEsq.size(); i++) {
                    Canhao c = canhoesEsq.get(i);
                    if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
                }
                java.util.List<Canhao> canhoesDir = jogo.getCanhoesDireito();
                for (int i = 0; i < canhoesDir.size(); i++) {
                    Canhao c = canhoesDir.get(i);
                    if (c.isAtivo()) canhoesPos.add(new float[]{c.getX(), c.getY()});
                }
            }
        }"""
content = content.replace(search, replace)

with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'w') as f:
    f.write(content)
