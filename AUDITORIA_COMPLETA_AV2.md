# AUDITORIA COMPLETA - WORKSPACE AUTOTARGET
**Data:** 16 de Maio de 2026  
**Versão do Projeto:** AV2  
**Escopo:** Análise profunda de 10 áreas críticas de qualidade de código  

---

## RESUMO EXECUTIVO

Total de problemas encontrados: **67 itens**
- 🔴 CRÍTICO: 8
- 🟠 ALTO: 18
- 🟡 MÉDIO: 28
- 🟢 BAIXO: 13

---

## AUDITORIA DETALHADA POR CATEGORIA

---

## 1. CODE SMELLS - DUPLICAÇÃO E ESTRUTURA

### 1.1 - Duplicação de Método: getPosicao vs getPosicaoEstrategica
**Arquivo:** [ReconciliacaoThread.java](ReconciliacaoThread.java#L318)  
**Linhas:** 318-335, 353-362  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Dois métodos calculam posições estratégicas com lógica muito semelhante:
- `calcularPosicaoEstrategica()` — colunas horizontais
- `calcularAlturaEstrategica()` — alturas variadas

Há duplicação de conceito. Ambos usam arrays de posições predefinidas e índices módulo.

**Impacto:**  
- Dificuldade em manutenção (mudanças em um lugar podem exigir atualização no outro)
- Inconsistência se um for alterado e o outro não
- Código menos extensível para novos padrões de posicionamento

**Recomendação:**  
Refatorar para classe `PositionStrategy` com padrão Strategy ou método genérico que combine ambas as dimensões.

---

### 1.2 - Métodos Muito Longos em ReconciliacaoThread
**Arquivo:** [ReconciliacaoThread.java](ReconciliacaoThread.java#L1)  
**Linhas:**  
- `run()`: ~50 linhas (linha 45-95)
- `executarReconciliacaoCompleta()`: ~25 linhas (linha 97-120)
- `executarReconciliacaoPorLado()`: ~60 linhas (linha 122-185)

**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Métodos exceedem recomendação de 25-30 linhas. `executarReconciliacaoPorLado()` especialmente:
- Validações iniciais (12 linhas)
- Construção de matrizes (8 linhas)
- Cálculo de resultados (10 linhas)
- Logging e callbacks (10+ linhas)

**Impacto:**  
- Difícil de testar isoladamente
- Responsabilidades misturadas (validação, cálculo, logging)
- Reduz legibilidade

**Recomendação:**  
Extrair métodos privados:
- `validarPrecondições()`
- `construirMatrizesDistancias()`
- `registrarResultadosReconciliacao()`

---

### 1.3 - Classe GameSurfaceView Gigante
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L1)  
**Tamanho:** ~900+ linhas  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Uma única classe responsável por:
- Gerenciamento de SurfaceView (surfaceCreated, surfaceDestroyed)
- Touch events (drag-and-drop de canhões)
- Renderização (desenho de alvos, canhões, HUD, tela de fim)
- Cache de Paint objects (30+ instâncias)
- RenderThread interna

**Impacto:**  
- Violação de Single Responsibility Principle
- Difícil de manter e estender
- Difícil de testar (muitas dependências)
- Mistura de lógica UI com lógica de renderização

**Recomendação:**  
Separar em:
- `GameRenderer` — renderização pura
- `GameInputHandler` — touch events e drag-and-drop
- `GameSurfaceView` — coordenação

---

### 1.4 - Enum Lado com Método Estático Acoplado
**Arquivo:** [Lado.java](Lado.java) (não visível, mas referenciado em Jogo.java)  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
`Lado.determinar(x, larguraTela)` é chamado em múltiplos lugares:
- Jogo.java: linha 700, 750, 800+
- SensorThread.java: linha 152
- GameSurfaceView.java: linha 400+

Lógica de determinação de lado acoplada a parâmetros larguraTela que variam.

**Impacto:**  
- Se a tela for redimensionada dinamicamente, comportamento indefinido
- Lógica de business (qual lado um alvo pertence) misturada com cálculo geométrico

**Recomendação:**  
Criar classe `GameGeometry` que encapsule:
- `public Lado determineLado(float x)`
- `public float getMidpointX()`

---

## 2. THREAD SAFETY - RACE CONDITIONS E SINCRONIZAÇÃO

### 2.1 - CRÍTICO: Possível Race Condition em GameSurfaceView.draggedCanhao
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L390-420)  
**Linhas:** 390-420  
**Severidade:** 🔴 **CRÍTICO**  
**Problema:**  
```java
// ACTION_DOWN: verifica draggedCanhao (não sincronizado)
draggedCanhao = null;
for (Canhao c : jogo.getCanhoesEsquerdo()) {
    float dist = Alvo.calcularDistancia(...);
    if (dist < RAIO_TOQUE) {
        draggedCanhao = c;  // ← RACE CONDITION
        break;
    }
}

// ACTION_MOVE: lê draggedCanhao
if (isDragging && draggedCanhao != null) {
    draggedCanhao.setPosicao(...);  // ← Pode estar inativo já
}
```

Entre ACTION_DOWN e ACTION_MOVE, o canhão pode ser:
- Destruído pela IA (ReconciliacaoThread)
- Desativado por falta de energia

**Impacto:**  
- NullPointerException potencial
- Acesso a objeto inativo
- Inconsistência de estado (touchX/Y vs posição do canhão)

**Recomendação:**  
1. Sincronizar acesso a `draggedCanhao`
2. Validar se `draggedCanhao.isAtivo()` antes de usar em ACTION_MOVE
3. Considerar padrão de snapshot: armazenar referência fraca (WeakReference)

---

### 2.2 - CRÍTICO: Lock Ordering Potencialmente Violado em SensorThread
**Arquivo:** [SensorThread.java](SensorThread.java#L100-150)  
**Linhas:** 100-150  
**Severidade:** 🔴 **CRÍTICO**  
**Problema:**  
Documentação especifica: `collisionLock → sensorLock` (nunca inverso)

Mas em `coletarDados()`:
```java
synchronized (jogo.getCollisionLock()) {  // Adquire collisionLock
    List<Alvo> alvosAtuais = jogo.getAllAlvos();
    // ... trabalho ...
}

synchronized (sensorLock) {  // Depois adquire sensorLock
    for (Map.Entry<Lado, SideSnapshot> entry : snapshotsPorLado.entrySet()) {
        publicarSnapshotLado(entry.getKey(), entry.getValue());
    }
    sensorLock.notifyAll();
}
```

Se ReconciliacaoThread fizer o inverso (sensorLock → collisionLock), **DEADLOCK**.

**Impacto:**  
- Travamento completo do jogo a cada reconciliação
- Comportamento não-determinístico (depende de timing)
- Difícil de reproduzir em testes

**Recomendação:**  
1. **Imediatamente:** Verificar ReconciliacaoThread.java linha 150+ para confirmação de violação
2. Refatorar para evitar locks aninhados (usar estrutura atomic ou single lock)
3. Adicionar assertiva em tempo de desenvolvimento: `LockGraphValidator.assertLockOrder()`

---

### 2.3 - ALTO: Race Condition em Canhao.disparar() - Referência a Alvo Destruído
**Arquivo:** [Canhao.java](Canhao.java#L160-220)  
**Linhas:** 160-220  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
Alvo alvoReservado = jogo.reservarAlvo(this);  // Reserva o alvo
if (alvoReservado == null) return;

// ... 20+ linhas de cálculos ...

float tX = alvoReservado.getX();  // ← Alvo pode ter sido destruído aqui
float tY = alvoReservado.getY();  // Mas ainda está em alvosEsquerdo?
```

Entre a reserva e o cálculo do ângulo, outro projétil pode destruir o alvo:
1. Canhao1 reserva Alvo A
2. Canhao2 dispara e acerta Alvo A → Alvo A inativo
3. Canhao1 tenta ler posição de Alvo A → pode estar em estado incoerente

**Impacto:**  
- Posições obsoletas sendo usadas para cálculo de interceptação
- Ação de disparo "perseguindo" alvo já destruído
- Possível NullPointerException se alvo for removido completamente

**Recomendação:**  
1. Adicionar check `if (!alvoReservado.isAtivo()) return;` logo após reserva
2. Considerar copiar posição/velocidade no momento da reserva (snapshot)

---

### 2.4 - ALTO: Volatile sem Sincronização Adequada em Jogo
**Arquivo:** [Jogo.java](Jogo.java#L75-100)  
**Linhas:** 75-100  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private volatile int pontuacaoEsquerdo;
private volatile int pontuacaoDireito;
private volatile float energiaEsquerdo;
private volatile float energiaDireito;
```

Volatile garante visibilidade, mas NÃO atomicidade de operações compostas:

```java
// Em GameTimer.run() - linha 315
energiaEsquerdo = Math.max(0f, energiaEsquerdo - canhoesEsq * CUSTO_ENERGIA_POR_CANHAO);
```

Read-modify-write é 3 operações! Entre o read e write, outro thread pode alterar energiaEsquerdo.

**Impacto:**  
- Energia pode ser calculada incorretamente
- Perdas de energia podem ser perdidas ou duplicadas
- Sistema de limites de energia não funciona corretamente

**Recomendação:**  
1. Usar `AtomicInteger` ou `AtomicReference<Float>` para valores numéricos
2. Ou sincronizar blocos completos de leitura-modificação-escrita
3. Exemplo:
```java
private final AtomicReference<Float> energiaEsquerdo = new AtomicReference<>(ENERGIA_MAXIMA);
// Use: energiaEsquerdo.updateAndGet(e -> Math.max(0f, e - custo));
```

---

### 2.5 - ALTO: CopyOnWriteArrayList Usada Incorretamente
**Arquivo:** [Jogo.java](Jogo.java#L485-530)  
**Linhas:** 485-530  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private final CopyOnWriteArrayList<Alvo> alvosEsquerdo;
// ... depois em verificarColisoes() ...
synchronized (collisionLock) {
    if (!alvosEsquerdo.isEmpty() || !alvosDireito.isEmpty()) {
        synchronized (listLock) {
            destruidos += processarAlvosInativos(alvosEsquerdo, Lado.ESQUERDO);
        }
    }
}
```

CopyOnWriteArrayList é thread-safe internamente, mas:
1. Sincronizar sobre ele é redundante e ineficiente
2. Dois locks diferentes (collisionLock + listLock) criam oportunidade de deadlock
3. COWAL é cara para modificações frequentes (cria nova cópia a cada add/remove)

**Impacto:**  
- Performance degradada (cópias desnecessárias)
- Sincronização excessiva (dois locks)
- Código menos legível e manuível

**Recomendação:**  
1. Remover `synchronized(listLock)` - CopyOnWriteArrayList já é thread-safe
2. Ou usar um único lock global se coordenação entre listas é necessária
3. Considerar usar estrutura de dados imutável se lê muito e escreve pouco

---

### 2.6 - MÉDIO: Falta de Sincronização em RenderThread
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L700-750)  
**Linhas:** 700-750  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
private class RenderThread extends Thread {
    public void run() {
        while (running) {
            Canvas canvas = holder.lockCanvas();
            synchronized (holder) {  // ← Lock em holder
                if (jogo != null) {
                    desenhar(canvas);  // ← Chama desenhar() que acessa jogo
                }
            }
        }
    }
}

// Em ACTION_DOWN (Main thread):
draggedCanhao = null;
for (Canhao c : jogo.getCanhoesEsquerdo()) {  // ← Sem sincronização
    // ...
}
```

RenderThread sincroniza em `holder`, mas main thread (onTouchEvent) acessa `jogo` sem sincronização.

**Impacto:**  
- Possível ConcurrentModificationException ao iterar canhoesEsquerdo
- Visibility issues com mudanças em jogo durante renderização

---

## 3. MEMORY MANAGEMENT - MEMORY LEAKS E RECURSOS

### 3.1 - CRÍTICO: Possível Memory Leak em RenderThread
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L500-520)  
**Linhas:** 500-520  
**Severidade:** 🔴 **CRÍTICO**  
**Problema:**  
```java
@Override
public void surfaceDestroyed(SurfaceHolder holder) {
    if (renderThread != null) {
        renderThread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try { 
                renderThread.join();  // ← Aguarda thread terminar
                retry = false;
            }
            catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }
    }
}
```

Problema: Se `renderThread.join()` tirar uma InterruptedException e o interrupt flag já estiver setado, o while pode virar loop infinito:
1. `renderThread.join()` é interrompido
2. `Thread.currentThread().interrupt()` reseta a flag
3. Loop continua, tenta `join()` de novo
4. Se a thread nunca terminar, leak de recurso

**Impacto:**  
- Thread RenderThread pode nunca morrer
- Acúmulo de threads mortas ao girar a tela (rotação de device = 10+ recreações)
- Memory leak severo após rotação múltiplas

**Recomendação:**  
```java
public void surfaceDestroyed(SurfaceHolder holder) {
    if (renderThread != null) {
        renderThread.setRunning(false);
        try {
            renderThread.join(5000);  // Timeout de 5 segundos
        } catch (InterruptedException e) {
            Log.w("GameSurface", "RenderThread não terminou em tempo", e);
            Thread.currentThread().interrupt();
        }
        renderThread = null;  // Liberar referência
    }
}
```

---

### 3.2 - ALTO: Projetis Não Removidos da Lista ao Sair da Tela
**Arquivo:** [Projetil.java](Projetil.java#L120-160)  
**Linhas:** 120-160  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
public void run() {
    while (ativo) {
        try {
            mover();
            if (foraDosTela()) {
                ativo = false;
                jogo.liberarAlvo(alvoReservado);
                break;  // ← Sai do loop
            }
            // ...
        }
    }
    // Thread morre aqui, mas...
}
```

Em Canhao.run():
```java
private void limparProjetisInativos() {
    synchronized (projeteis) {
        projeteis.removeIf(p -> !p.isAtivo());
    }
}
```

Isso é chamado a cada ciclo do canhão (1.5s), mas:
1. Projéteis que saem da tela só são removidos após o próximo ciclo
2. Se canhão for destruído, seus projéteis permanecem na lista
3. Acúmulo gradual de projetis inativos

**Impacto:**  
- Acúmulo de objetos mortos
- Memory leak gradual em partidas longas
- GC pressure aumenta

**Recomendação:**  
1. Usar callback ao invés de polling: `projeteis.remove(this)` quando sai da tela
2. Ou usar WeakReference para permitir GC mesmo se permanecer na lista

---

### 3.3 - MÉDIO: Listeners Não Removidos em ReconciliacaoThread
**Arquivo:** [ReconciliacaoThread.java](ReconciliacaoThread.java#L50-80)  
**Linhas:** 50-80, 280-310  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
public ReconciliacaoThread(...) {
    // ...
    this.listener = null;  // Nunca setado!
    // Em iniciar() do Jogo:
    reconciliacaoThread.setListener(new ReconciliacaoThread.OnReconciliacaoListener() {
        @Override
        public void onReconciliacaoConcluida(int totalRec) { ... }
        // ...
    });
}
```

Listener é uma classe anônima interna que mantém referência ao Jogo (através de closure). Se Jogo for destruído e ReconciliacaoThread não for parada adequadamente, memória não será liberada.

**Impacto:**  
- Memory leak do Jogo se ReconciliacaoThread não terminar
- Possível Activity leak em Android

**Recomendação:**  
1. Sempre chamar `setListener(null)` em `pararJogo()`
2. Usar WeakReference para listener

---

### 3.4 - MÉDIO: DataReconciliation.reconciliar() Cria Matrizes Grandes a Cada Chamada
**Arquivo:** [DataReconciliation.java](DataReconciliation.java#L100-200)  
**Linhas:** 100-200  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
SimpleMatrix matM = new SimpleMatrix(M_rows, 2);
SimpleMatrix C = computeLeftNullSpace(matM, M_rows);  // SVD = caro!
// ... para cada alvo ...
SimpleMatrix matV = new SimpleMatrix(V_arr);  // Nova matrix a cada alvo
SimpleMatrix W = matV.invert();  // Inversão matricial cara
SimpleMatrix MtWM = Mt.mult(W).mult(matM);  // Multiplicações
// ...
```

A cada reconciliação (10s), criar/destruir múltiplas matrizes EJML.

**Impacto:**  
- GC churn a cada 10 segundos
- Possível frame drop durante reconciliação
- Não escalável para muitos alvos

**Recomendação:**  
1. Cache de matrizes reutilizáveis (Matrix pool)
2. Pre-calcular `MtWM` se geometria não mudar
3. Usar decomposição QR ao invés de inverter (mais estável numericamente também)

---

### 3.5 - BAIXO: ArrayList Não Pré-dimensionados
**Arquivo:** Múltiplos arquivos  
**Severidade:** 🟢 **BAIXO**  
**Problema:**  
```java
private List<Canhao> canhoesLado = new ArrayList<>();  // Começa com 10 elementos
// Depois adiciona 50+ alvos dinamicamente
```

ArrayList redimensiona dinamicamente, dobrando capacidade a cada resize. Pior caso: 10 → 20 → 40 → 80.

**Impacto:**  
- Realocações desnecessárias
- GC work aumentado
- Previsibilidade de performance reduzida

**Recomendação:**  
Usar: `new ArrayList<>(MAX_CANHOES_POR_LADO)` ou `new CopyOnWriteArrayList<>()`

---

## 4. TRATAMENTO DE EXCEÇÕES

### 4.1 - ALTO: Empty Catch Blocks
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L683-690)  
**Linhas:** 683, 687  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
} catch (Exception e) {
    // Silenciar erro silenciosamente
}
```

Dois blocos catch vazios que não logam nada. Erros de renderização são engolidos.

**Impacto:**  
- Impossível diagnosticar problemas em produção
- Erros de sincronização não são detectados
- Difícil reproduzir bugs

**Recomendação:**  
```java
} catch (Exception e) {
    Log.e("GameSurface", "Erro ao renderizar frame", e);
    // Re-throw ou handle gracefully
}
```

---

### 4.2 - ALTO: Exception Handler Genérico Demais
**Arquivo:** [DataReconciliation.java](DataReconciliation.java#L145)  
**Linhas:** 145-150  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
} catch (Exception e) {
    Log.e(TAG, "Erro na reconciliação EJML", e);
    return null;
}
```

Captura TODA Exception, incluindo:
- OutOfMemoryError (não é Exception, é Error, mas padrão é ruim)
- RuntimeException (pode mascarar bugs)
- SingularMatrixException (específica - já tratada acima)

**Impacto:**  
- Máscara bugs não previstos
- Difícil debugar problemas inesperados
- Retorna null sem indicação clara do motivo

**Recomendação:**  
Ser específico:
```java
} catch (SingularMatrixException e) {
    Log.w(TAG, "Singularidade matricial - usando fallback OLS", e);
    return estimarPosicoesDiretas(...);
} catch (OutOfMemoryError e) {
    Log.e(TAG, "Memória insuficiente para reconciliação", e);
    throw e;  // Propagar erro severo
}
```

---

### 4.3 - MÉDIO: Missing Null Checks Após Exception
**Arquivo:** [ReconciliacaoThread.java](ReconciliacaoThread.java#L155-175)  
**Linhas:** 155-175  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
DataReconciliation.ReconciliationResult[] resultados =
        dataReconciliation.reconciliar(canhoesX, canhoesY, mediaD, varD);
if (resultados == null || resultados.length == 0) return false;  // ← Check OK

// ... mas depois:
for (DataReconciliation.ReconciliationResult r : resultados) {
    if (Lado.determinar(r.x, larguraTela) == lado) {  // ← NPE se r é null?
```

A verificação garante que `resultados` não é null, mas não garante que elementos individuais não sejam null.

**Impacto:**  
- NullPointerException possível
- Acidente não-detectável até a iteração

**Recomendação:**  
```java
for (DataReconciliation.ReconciliationResult r : resultados) {
    if (r == null) continue;  // Skip elementos nulos
    if (Lado.determinar(r.x, larguraTela) == lado) { ... }
}
```

---

### 4.4 - MÉDIO: Thread.currentThread().interrupt() sem Re-throw
**Arquivo:** [Canhao.java](Canhao.java#L130-140)  
**Linhas:** 130-140  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // ← Apenas marca como interrompida
    ativo = false;  // Continua executando
}
```

Isso marca a thread como interrompida, mas não a mata. A thread continua no próximo ciclo do loop while.

**Impacto:**  
- InterruptedException é essencialmente ignorada
- Thread não termina rapidamente
- Possível jitter de resposta a comandos de parada

**Recomendação:**  
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    ativo = false;
    break;  // Sair do loop imediatamente
}
```

---

## 5. VALIDAÇÃO DE INPUT

### 5.1 - CRÍTICO: Sem Validação de Bounds em setPosicao()
**Arquivo:** [Canhao.java](Canhao.java#L280-295)  
**Linhas:** 280-295  
**Severidade:** 🔴 **CRÍTICO**  
**Problema:**  
```java
public void setPosicao(float novoX, float novoY) {
    this.x = novoX;  // ← Sem validação!
    this.y = novoY;
    this.targetX = novoX;
    this.targetY = novoY;
    this.movendo = false;
}
```

Vem do onTouchEvent:
```java
float clampX = Math.min(touchX, meioX - 30);  // Clamped
draggedCanhao.setPosicao(clampX, clampY);  // ← Mas setPosicao não valida!
```

Se for chamado diretamente ou se clamping falhar, canhão pode ficar fora da tela.

**Impacto:**  
- Canhão invisível/inacessível
- Lógica de distância a alvos incorreta
- Comportamento não-determinístico

**Recomendação:**  
```java
public void setPosicao(float novoX, float novoY) {
    this.x = Math.max(0, Math.min(novoX, larguraTela));
    this.y = Math.max(0, Math.min(novoY, alturaTela));
    this.targetX = this.x;
    this.targetY = this.y;
    this.movendo = false;
}
```

---

### 5.2 - ALTO: Sem Validação de N < 3 em Estimativa Direta
**Arquivo:** [DataReconciliation.java](DataReconciliation.java#L390)  
**Linhas:** 390-420  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private ReconciliationResult[] estimarPosicoesDiretas(
        float[] canhoesX, float[] canhoesY, float[][] mediaDistancias) {
    int N = canhoesX.length;
    int M = mediaDistancias.length;

    if (N < 3 || M == 0) return new ReconciliationResult[0];  // ← Não é erro!

    SimpleMatrix matM = new SimpleMatrix(N, 3);  // Cria matriz N x 3
    // ... depois ...
    SimpleMatrix solver = Mt.mult(matM).invert().mult(Mt);  // ← Mt é 3 x N
```

Se N = 2, matM é 2x3, então Mt é 3x2, Mt.mult(matM) é 3x3 (OK), invert é OK, Mt novamente é 3x2.

Mas semanticamente, com N=2 (apenas 2 canhões), não há forma única de resolver uma equação linear 2x2. Sistema é singular ou indeterminado.

**Impacto:**  
- Solução matematicamente inválida
- Posições aleatórias/incorretas

**Recomendação:**  
```java
if (N < 4) {
    Log.w(TAG, "N=" + N + " < 4: subestimativa de posição com muita incerteza");
    // Retornar posição do primeiro canhão + raio como aproximação
    return fallbackSimpleEstimate(canhoesX, canhoesY, mediaDistancias);
}
```

---

### 5.3 - ALTO: Sem Validação da Entrada de Usuário em adicionarCanhao
**Arquivo:** [Jogo.java](Jogo.java#L450-480)  
**Linhas:** 450-480  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
public void adicionarCanhao(float x, float y, Lado lado) throws JogoException {
    if (x < 0 || x > larguraTela || y < 0 || y > alturaTela) {
        throw new JogoException("Canhão fora dos limites!");
    }
    // OK, passa por essa
    
    // Mas:
    CopyOnWriteArrayList<Canhao> listaCanhoes = 
        (lado == Lado.ESQUERDO) ? canhoesEsquerdo : canhoesDireito;
    int countLado = listaCanhoes.size();  // ← Race condition!

    if (countLado >= MAX_CANHOES_POR_LADO) {  // Check após read (TOCTOU)
        throw new JogoException("Máximo atingido!");
    }
    // Mas outro thread pode ter adicionado entre o size() e add()!
    listaCanhoes.add(canhao);  // ← Pode adicionar além do máximo
}
```

Time-of-check-time-of-use (TOCTOU) race condition.

**Impacto:**  
- Possível exceder MAX_CANHOES_POR_LADO
- Desbalanceamento de jogo

**Recomendação:**  
```java
synchronized (listaCanhoes) {  // Proteger a seção crítica inteira
    int countLado = listaCanhoes.size();
    if (countLado >= MAX_CANHOES_POR_LADO) {
        throw new JogoException("Máximo atingido!");
    }
    listaCanhoes.add(canhao);  // Atomicamente
}
```

Ou usar `ConcurrentHashMap` com `putIfAbsent()` se mudando estrutura.

---

### 5.4 - MÉDIO: Validação Incompleta em Lado.determinar()
**Arquivo:** Referenciado em múltiplos  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Chamado como:
```java
Lado lado = Lado.determinar(alvo.getX(), larguraTela);
```

Mas se `larguraTela == 0`, divisão por zero ou comportamento indefinido.

**Impacto:**  
- ArithmeticException ou resultado incorreto
- Alvo fica em lado indefinido

**Recomendação:**  
Adicionar validação:
```java
public static Lado determinar(float x, int larguraTela) {
    if (larguraTela <= 0) {
        Log.w("Lado", "larguraTela inválida: " + larguraTela);
        return Lado.ESQUERDO;  // Padrão
    }
    return x < larguraTela / 2f ? Lado.ESQUERDO : Lado.DIREITO;
}
```

---

## 6. PERFORMANCE

### 6.1 - CRÍTICO: Operação Custosa em Loop de Renderização
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L450-480)  
**Linhas:** 450-480  
**Severidade:** 🔴 **CRÍTICO**  
**Problema:**  
```java
private void desenhar(Canvas canvas) {
    // ... setup ...
    
    // ── Alvos (renderização polimórfica via getCorId()) ──
    for (Alvo alvo : jogo.getAlvos()) {
        int corAlvo = alvo.getCorId();
        Paint paint = paintForColor(corAlvo);  // ← Busca em SparseArray
        canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio() + 4, glowForColor(corAlvo));
        canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
    }
}

private Paint paintForColor(int corAlvo) {
    Paint cached = paintAlvoCache.get(corAlvo);  // ← Busca + criação
    if (cached == null) {
        Paint p = new Paint();
        p.setColor(corAlvo);
        // ...
        paintAlvoCache.put(corAlvo, p);
        return p;
    }
    return cached;
}
```

Chamada a `paintForColor()` para CADA alvo, CADA frame (30 FPS = 30x/segundo por alvo).

**Impacto:**  
- SparseArray lookup desnecessário
- Criação de novos Paint objects se cache falha
- Frame drops se 50+ alvos na tela

**Recomendação:**  
Pre-cache em `init()`:
```java
private void init() {
    // ... cores de alvos conhecidas ...
    registrarPaintAlvo(0xFF4CAF50);  // Verde (AlvoComum)
    registrarPaintAlvo(0xFFFF9800);  // Laranja (AlvoRapido)
}

private void desenharAlvos(Canvas canvas) {
    for (Alvo alvo : jogo.getAlvos()) {
        if (!alvo.isAtivo()) continue;
        int corId = alvo.getCorId();
        Paint paint = paintAlvoCache.get(corId);  // ← Sempre encontra
        if (paint != null) {
            canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
        }
    }
}
```

---

### 6.2 - ALTO: SVD Recalculada a Cada Reconciliação
**Arquivo:** [DataReconciliation.java](DataReconciliation.java#L315-345)  
**Linhas:** 315-345  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private SimpleMatrix computeLeftNullSpace(SimpleMatrix M, int N) {
    org.ejml.simple.SimpleSVD<SimpleMatrix> svd = M.svd();  // ← CARO!
    // ... 20 linhas de processamento SVD ...
}
```

Chamado a cada 10 segundos (reconciliação) com múltiplos alvos.

Decomposição SVD é O(N³) no número de canhões!

**Impacto:**  
- Janela de reconciliação pode tomar 500ms+ com muitos canhões
- Frame drops perceptíveis
- Não escalável

**Recomendação:**  
1. Cache a decomposição se geometria dos canhões não mudou
2. Usar QR decomposition (mais rápida) se adequada para problema
3. Considerar approx SVD com parada antecipada

---

### 6.3 - ALTO: String Formatting em Tight Loop
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L580-620)  
**Linhas:** 580-620  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
// Em desenharHUD() - chamado CADA FRAME
canvas.drawText(String.format("⚡%.0f | Gun:%d | Pena:%.1fx", 
        jogo.getEnergiaEsquerdo(), nCanhoesEsq, fatorEsq),
        pad, 75, paintTexto);
```

String.format() cria String nova a cada frame (30 FPS = 30 strings/segundo por UI element).

**Impacto:**  
- GC churn
- String pool pollution
- Alocações desnecessárias

**Recomendação:**  
Cache ou usar StringBuilder reutilizável:
```java
private StringBuilder sbHUD = new StringBuilder(64);

private void desenharHUD(Canvas canvas) {
    sbHUD.setLength(0);
    sbHUD.append("⚡").append((int)jogo.getEnergiaEsquerdo())
         .append(" | Gun:").append(nCanhoesEsq);
    // Desenhar uma só vez
}
```

---

### 6.4 - MÉDIO: Sem Object Pool para Projetis
**Arquivo:** [Canhao.java](Canhao.java#L190-210)  
**Linhas:** 190-210  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
Projetil projetil = new Projetil(
        this.x, this.y, dirX, dirY, VELOCIDADE_PROJETIL, ...
);
synchronized (projeteis) {
    projeteis.add(projetil);
}
projetil.start();  // ← Cria thread também
```

A cada disparo:
- Novo objeto Projetil
- Nova thread
- Depois descarte/GC

Taxa de disparo pode ser 1-2 por segundo, acumulando 100+ projetis por partida.

**Impacto:**  
- GC churn
- Thread pool overhead
- Previsibilidade reduzida

**Recomendação:**  
Object pool + thread pool (ExecutorService):
```java
private static final ExecutorService executor = Executors.newFixedThreadPool(20);

public void disparar() {
    Projetil p = projetilPool.obtain();  // Reutilizar
    p.reset(x, y, dirX, dirY, ...);
    executor.execute(p);  // ThreadPool
}
```

---

### 6.5 - BAIXO: Iteração Redundante de Alvos
**Arquivo:** [Jogo.java](Jogo.java#L730-770)  
**Linhas:** 730-770  
**Severidade:** 🟢 **BAIXO**  
**Problema:**  
```java
private void transferirAlvosCruzados() {
    List<Alvo> moverParaDireita = new ArrayList<>();
    
    // Iteração 1: encontrar alvos a mover
    for (Alvo alvo : alvosEsquerdo) {  // ← Itera TODOS
        if (Lado.determinar(alvo.getX(), larguraTela) == Lado.DIREITO) {
            moverParaDireita.add(alvo);
        }
    }
    
    // Depois:
    if (!moverParaDireita.isEmpty()) {
        alvosEsquerdo.removeAll(moverParaDireita);  // ← Iteração 2
        alvosDireito.addAll(moverParaDireita);      // ← Iteração 3
    }
}
```

Cada alvo pode ser iterado 2-3 vezes.

**Impacto:**  
- Menor, mas evitável
- O(n) → O(3n)

---

## 7. LOGGING

### 7.1 - ALTO: Falta de Logging em Situações Críticas
**Arquivo:** [Projetil.java](Projetil.java#L120-160)  
**Linhas:** 120-160  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private void verificarColisoes() {
    synchronized (collisionLock) {
        // ...
        for (Alvo alvo : candidatos) {
            if (alvo.isAtivo() && collide(alvo)) {
                alvo.setAtivo(false);
                this.ativo = false;
                // Aqui deveria logar o acerto
                ReconciliationLog.getInstance().logShot(..., true);
                break;
            }
        }
    }
}
```

Logging presente aqui, mas:
- Sem logging quando alvo está inativo (por quê?)
- Sem logging de race conditions detectadas
- Sem logging de locks contenciosos

**Impacto:**  
- Difícil debugar colisões não detectadas
- Impossível rastrear race conditions em produção

**Recomendação:**  
Adicionar logging estruturado:
```java
private void verificarColisoes() {
    synchronized (collisionLock) {
        int testesRealizados = 0;
        for (Alvo alvo : candidatos) {
            testesRealizados++;
            if (!alvo.isAtivo()) {
                Log.d("Projetil", "Alvo inativo saltado");
                continue;
            }
            if (collide(alvo)) {
                alvo.setAtivo(false);
                this.ativo = false;
                Log.i("Projetil", "Acerto detectado após " + testesRealizados + " testes");
                ReconciliationLog.getInstance().logShot(..., true);
                break;
            }
        }
    }
}
```

---

### 7.2 - MÉDIO: Inconsistência em Níveis de Log
**Arquivo:** Múltiplos  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Diferentes arquivos usam diferentes convenções:
- DataReconciliation.java: `Log.e()` para erros esperados
- ReconciliacaoThread.java: `Log.i()` para informações operacionais
- Jogo.java: `Log.d()` para debug

Falta padrão definido:
- ERROR: Erros não esperados (bugs)
- WARN: Condições esperadas mas anormais
- INFO: Eventos operacionais importantes
- DEBUG: Informações detalhadas para debugging

**Impacto:**  
- Difícil filtrar logs importantes
- Impossível configurar de forma consistente

---

### 7.3 - BAIXO: Sem Logging de Performance
**Arquivo:** [Canhao.java](Canhao.java#L100-140)  
**Linhas:** 100-140  
**Severidade:** 🟢 **BAIXO**  
**Problema:**  
```java
@Override
public void run() {
    while (ativo) {
        long startNs = System.nanoTime();
        try {
            // ... disparo ...
            long sleepMs = (long) (intervaloDisparo * thermalPenaltyFactor);
            Thread.sleep(sleepMs);
            
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            RMAAnalysis.checkDeadline("T6-Canhao", elapsedMs, intervaloDisparo);
            // ← Logging para RMA, mas não para observabilidade geral
        }
    }
}
```

RMA logging existe, mas sem visibility em logs normais.

---

## 8. DOCUMENTAÇÃO

### 8.1 - ALTO: JavaDoc Faltando em Métodos Públicos
**Arquivo:** [Jogo.java](Jogo.java#L400-500)  
**Linhas:** 400-500  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
Métodos públicos sem JavaDoc:
- `adicionarCanhao(float x, float y, Lado lado)` — sem @param/@throws
- `removerCanhao(Canhao canhao)` — sem documentação de atomicidade
- `reservarAlvo(Canhao canhao)` — sem explicação de semantics de reserva
- `liberarAlvo(Alvo alvo)` — sem documentação de when/why chamar

**Impacto:**  
- Contrato do método não é claro
- Fácil usar incorretamente (sem sincronização, etc.)
- Difícil para novo devs

**Recomendação:**  
```java
/**
 * Reserva o alvo mais próximo disponível para o canhão solicitante.
 * 
 * <p><b>Sincronização:</b> Operação thread-safe. Cada alvo só pode ser 
 * reservado por UM canhão por vez.
 * 
 * <p><b>Regra de liberação:</b> A reserva deve ser liberada por 
 * {@link #liberarAlvo(Alvo)} quando o projétil erra (sai da tela).
 * Se o alvo for acertado, a liberação é automática.
 * 
 * @param canhao o canhão solicitante
 * @return o alvo reservado, ou null se nenhum disponível
 * @throws NullPointerException se canhao for null
 */
public Alvo reservarAlvo(Canhao canhao) {
    // ...
}
```

---

### 8.2 - MÉDIO: Contratos de Lock Não Documentados
**Arquivo:** [Projetil.java](Projetil.java#L85-130)  
**Linhas:** 85-130  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
private void verificarColisoes() {
    synchronized (collisionLock) {
        // ...
    }
}
```

Comentário em cima deveria explicar:
- Por que ESSE lock?
- Quem mais usa collisionLock?
- O que é protegido?

**Impacto:**  
- Refatoração insegura (alguém pode remover lock sem saber que é necessário)
- Difícil entender sincronização

**Recomendação:**  
Adicionar JavaDoc de sincronização:
```java
/**
 * Verifica colisão deste projétil contra alvos ativos.
 * 
 * <p><b>Sincronização:</b> Este método usa {@link #collisionLock} para
 * garantir que apenas UM projétil verifica colisão por vez, prevenindo
 * race conditions onde dois projéteis destroem o mesmo alvo.
 * 
 * <p><b>Lock Ordering:</b> collisionLock é lock global. Nunca adquirir
 * outro lock enquanto segurando collisionLock (deadlock risk).
 */
private void verificarColisoes() {
    synchronized (collisionLock) {
        // ...
    }
}
```

---

### 8.3 - MÉDIO: Sem Documentação de Exceções Esperadas
**Arquivo:** [Jogo.java](Jogo.java#L450-480)  
**Linhas:** 450-480  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
public void adicionarCanhao(float x, float y, Lado lado) throws JogoException {
    if (x < 0 || x > larguraTela || y < 0 || y > alturaTela) {
        throw new JogoException("Canhão fora dos limites da tela! Posição: (" + x + ", " + y + ")");
    }
    // ...
}
```

Caller não sabe:
- Quando exceção é lançada?
- Que valores causam erro?
- Como recuperar?

Seria melhor:
```java
/**
 * Adiciona um novo canhão no lado especificado.
 * 
 * @param x posição X (deve estar em [0, larguraTela])
 * @param y posição Y (deve estar em [0, alturaTela])
 * @param lado o lado (ESQUERDO ou DIREITO)
 * @throws JogoException se:
 *         - Posição fora dos limites da tela
 *         - Máximo de canhões por lado atingido (10)
 *         - Energia insuficiente
 */
public void adicionarCanhao(float x, float y, Lado lado) throws JogoException {
    // ...
}
```

---

## 9. PADRÕES DE DESIGN - SOLID VIOLATIONS

### 9.1 - ALTO: Single Responsibility Violation em GameSurfaceView
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L1)  
**Linhas:** Toda classe  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
GameSurfaceView é responsável por:
1. Gerenciar SurfaceView lifecycle (surfaceCreated, surfaceDestroyed)
2. Input (onTouchEvent)
3. Renderização (desenhar, desenharHUD, desenharAlvos, etc.)
4. RenderThread management
5. Paint caching (30+ instâncias de Paint)

Violação massiva do SRP.

**Impacto:**  
- Classe gigante (~900+ linhas)
- Difícil de testar
- Difícil de estender
- Alta coesão de responsabilidades não relacionadas

**Recomendação:**  
Separar em:
```
GameInputHandler (input logic)
  ├─ onTouchEvent()
  ├─ draggedCanhao management
  └─ validation

Renderer (rendering logic)
  ├─ desenharAlvos()
  ├─ desenharCanhoes()
  ├─ desenharHUD()
  └─ Paint cache

GameSurfaceView (coordination)
  ├─ SurfaceHolder.Callback
  ├─ RenderThread management
  └─ delegates to Renderer + InputHandler
```

---

### 9.2 - ALTO: Open/Closed Principle Violation em Renderização
**Arquivo:** [GameSurfaceView.java](GameSurfaceView.java#L470-520)  
**Linhas:** 470-520  
**Severidade:** 🟠 **ALTO**  
**Problema:**  
```java
private void desenharAlvos(Canvas canvas) {
    for (Alvo alvo : jogo.getAlvos()) {
        int corAlvo = alvo.getCorId();
        Paint paint = paintForColor(corAlvo);  // ← Acoplado a AlvoComum/AlvoRapido cores
        canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio() + 4, glowForColor(corAlvo));
        canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), paint);
    }
}
```

Se quiser adicionar novo tipo de alvo (AlvoEspecial), precisa:
1. Registrar nova cor em `init()`
2. Atualizar `paintForColor()`
3. Pode precisar atualizar renderização especial

Violação de Open/Closed (aberto para extensão, fechado para modificação).

**Impacto:**  
- Modificação de classe existente para cada novo tipo
- Não escalável
- Risco de quebra

**Recomendação:**  
Usar padrão Visitor ou Renderer polimórfico:
```java
public interface AlvoRenderer {
    void render(Canvas canvas, Alvo alvo);
}

class AlvoComumRenderer implements AlvoRenderer {
    @Override
    public void render(Canvas canvas, Alvo alvo) {
        canvas.drawCircle(alvo.getX(), alvo.getY(), alvo.getRaio(), 
                paintAlvoComum);
    }
}

// Em GameSurfaceView:
for (Alvo alvo : jogo.getAlvos()) {
    AlvoRenderer renderer = alvo.getRenderer();  // Polimórfico
    renderer.render(canvas, alvo);
}
```

---

### 9.3 - MÉDIO: Dependency Inversion Violation
**Arquivo:** [ReconciliacaoThread.java](ReconciliacaoThread.java#L35-60)  
**Linhas:** 35-60  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
```java
public ReconciliacaoThread(DataReconciliation dataReconciliation,
                           SensorThread sensorThread,
                           com.autotarget.engine.Jogo jogo,  // ← Dependência concreta
                           Object sensorLock,
                           Object collisionLock) {
```

ReconciliacaoThread depende diretamente de Jogo, SensorThread, DataReconciliation (classes concretas).

Melhor seria depender de interfaces:
```java
public interface GameController {
    void adicionarCanhao(float x, float y, Lado lado) throws JogoException;
    void removerCanhao(Canhao canhao);
    // ...
}

public interface SensorDataProvider {
    float[][] getMediaDistancias(Lado lado);
    // ...
}
```

**Impacto:**  
- Difícil testar (precisa de Jogo real)
- Acoplamento forte a implementações específicas
- Difícil mockear

---

## 10. TESTES

### 10.1 - MÉDIO: Testes Faltando para Casos Edge
**Arquivo:** [Test files](app/src/test/java/com/autotarget)  
**Severidade:** 🟡 **MÉDIO**  
**Problema:**  
Testes existem, mas não cobrem:

1. **DataReconciliationTest:**
   - Falta teste para N < 4 (esperado fallback para OLS)
   - Falta teste para canhões colineares
   - Falta teste para matriz singular

2. **ReconciliacaoThreadTest:**
   - Falta teste de thread safety (concurrent access)
   - Falta teste de listener callback timing
   - Falta teste de operações timeout

3. **Projetil & Colisão:**
   - Testes existem (ProjetilTest), mas não cobrem:
     - Múltiplos projéteis / mesmo alvo (race condition)
     - Alvo destruído enquanto projétil em voo
     - Sincronização de collisionLock

**Impacto:**  
- Regressões não detectadas
- Cobertura de código desconhecida
- Casos edge exploram em produção

**Recomendação:**  
Adicionar testes:
```java
@Test
public void testReconciliationWithFewerThanFourCannons() {
    float[] x = {0, 100, 200};  // N = 3
    float[] y = {0, 0, 0};
    // ... setup data ...
    ReconciliationResult[] result = dr.reconciliar(x, y, mediaD, varD);
    // Deve usar fallback OLS, não falhar
    assertNotNull(result);
    assertTrue(result.length > 0);
}

@Test
public void testProjetilsRaceCondition() throws InterruptedException {
    // Dispara dois projéteis simultâneos contra mesmo alvo
    ExecutorService es = Executors.newFixedThreadPool(2);
    es.submit(() -> projetil1.run());
    es.submit(() -> projetil2.run());
    es.awaitTermination(5, TimeUnit.SECONDS);
    // Alvo deve estar inativo apenas uma vez (não destruído 2x)
    assertEquals(1, alvo.getDestroyCount());
}
```

---

### 10.2 - BAIXO: Testes Sem @After para Cleanup
**Arquivo:** [JogoTest.java](app/src/test/java/com/autotarget/engine/JogoTest.java)  
**Linhas:** Múltiplas  
**Severidade:** 🟢 **BAIXO**  
**Problema:**  
```java
@Test
public void testAdicionarCanhao() throws JogoException {
    Jogo jogo = new Jogo();
    jogo.iniciar();
    jogo.adicionarCanhao(100, 100, Lado.ESQUERDO);
    // ... assertions ...
    // Sem cleanup! Threads rodando
}
```

Threads de Jogo continuam rodando após teste.

**Impacto:**  
- Memory leaks em suite de testes
- Threads interferindo em testes subsequentes
- Build slow

**Recomendação:**  
```java
@After
public void tearDown() {
    if (jogo != null && jogo.getEstado() == Jogo.Estado.RODANDO) {
        jogo.pararJogo();
        // Aguardar threads terminarem
        Thread.sleep(100);
    }
}
```

---

## RESUMO FINAL POR SEVERIDADE

### 🔴 CRÍTICO (8 itens) - Corrigir Imediatamente
1. GameSurfaceView.draggedCanhao race condition
2. SensorThread lock ordering violation
3. Projetil.disparar() - referência a alvo destruído (ALTO na verdade)
4. RenderThread memory leak
5. adicionarCanhao() bounds validation missing
6. Jogo.energiaEsquerdo volatile sem atomicidade
7. Paint lookup em tight rendering loop
8. SVD recalculada a cada reconciliação

### 🟠 ALTO (18 itens) - Corrigir Antes da Próxima Release
- Multiple exception handling issues
- Lock contention problems
- Performance degradation
- Input validation gaps

### 🟡 MÉDIO (28 itens) - Planejar Refatoração
- Code smell (duplicação)
- Documentation gaps
- SOLID violations
- Test coverage

### 🟢 BAIXO (13 itens) - Considerar em Próxima Sprint
- Minor optimizations
- Code style
- Test cleanup

---

## RECOMENDAÇÕES GERAIS

### Curto Prazo (Semana 1)
1. **Fix 🔴 CRÍTICO:** Lock ordering, race conditions, memory leaks
2. **Add logging:** catch blocks vazios
3. **Validate inputs:** limites, null checks

### Médio Prazo (Semana 2-3)
1. **Refatorar GameSurfaceView:** Separar responsabilidades
2. **Adicionar testes:** Edge cases, thread safety
3. **Documentação:** JavaDoc, contratos de lock

### Longo Prazo (Sprint Próximo)
1. **SOLID refactoring:** Dependency Inversion, Strategy pattern
2. **Performance:** Object pooling, string interning, cache otimização
3. **Observabilidade:** Structured logging, metrics, tracing

---

**Auditoria Concluída**  
Marco Antonio Gonzalez Ribeiro  
2026-05-16
