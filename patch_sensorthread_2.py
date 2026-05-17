with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'r') as f:
    content = f.read()

search = """        synchronized (jogo.getCollisionLock()) {
            List<Alvo> alvosAtuais = jogo.getAllAlvos();
            List<Canhao> canhoesEsq = new ArrayList<>();
            List<Canhao> canhoesDir = new ArrayList<>();
            for (Canhao c : jogo.getCanhoesEsquerdo()) {
                if (c.isAtivo()) canhoesEsq.add(c);
            }
            for (Canhao c : jogo.getCanhoesDireito()) {
                if (c.isAtivo()) canhoesDir.add(c);
            }"""
replace = """        synchronized (jogo.getCollisionLock()) {
            List<Alvo> alvosAtuais = jogo.getAllAlvos();
            List<Canhao> canhoesEsq = new ArrayList<>();
            List<Canhao> canhoesDir = new ArrayList<>();
            synchronized(jogo.getCanhoesLock()) {
                java.util.List<Canhao> listaEsq = jogo.getCanhoesEsquerdo();
                for (int i = 0; i < listaEsq.size(); i++) {
                    Canhao c = listaEsq.get(i);
                    if (c.isAtivo()) canhoesEsq.add(c);
                }
                java.util.List<Canhao> listaDir = jogo.getCanhoesDireito();
                for (int i = 0; i < listaDir.size(); i++) {
                    Canhao c = listaDir.get(i);
                    if (c.isAtivo()) canhoesDir.add(c);
                }
            }"""
content = content.replace(search, replace)

with open('app/src/main/java/com/autotarget/service/SensorThread.java', 'w') as f:
    f.write(content)
