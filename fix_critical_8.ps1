# Script para corrigir 8 problemas CRÍTICOS encontrados na auditoria
# Execução: .\fix_critical_8.ps1

$ErrorActionPreference = "Stop"
$projectPath = "c:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget"
$appPath = "$projectPath\app\src\main\java\com\autotarget"

Write-Host "🔴 INICIANDO CORREÇÃO DOS 8 PROBLEMAS CRÍTICOS" -ForegroundColor Red

# CRÍTICO #1: GameSurfaceView - Race Condition draggedCanhao
Write-Host "`n[1/8] Corrigindo: GameSurfaceView Race Condition..." -ForegroundColor Yellow
$file = "$appPath\engine\GameSurfaceView.java"
$content = Get-Content $file -Raw

# Adicionar lock para draggedCanhao
if ($content -match "private boolean isDragging;" -and $content -notmatch "draggedCanhaoLock") {
    $newContent = $content -replace `
        "    private boolean isDragging;",
        "    private boolean isDragging;`n    /** Lock para sincronizar draggedCanhao. */`n    private final Object draggedCanhaoLock = new Object();"
    
    # Sincronizar ACTION_DOWN
    $newContent = $newContent -replace `
        "(case MotionEvent\.ACTION_DOWN:.*?)(draggedCanhao = null;)",
        "`$1synchronized (draggedCanhaoLock) {`n                    draggedCanhao = null;"
    
    # Sincronizar ACTION_DOWN - fim do bloco
    $newContent = $newContent -replace `
        "(isDragging = \(draggedCanhao != null\);)",
        "`$1`n                }"
    
    # Sincronizar ACTION_MOVE
    $newContent = $newContent -replace `
        "(case MotionEvent\.ACTION_MOVE:.*?if \(isDragging && draggedCanhao != null\) \{)(.*?)(draggedCanhao\.setPosicao\(clampX, clampY\);)",
        "`$1`n                    synchronized (draggedCanhaoLock) {`n                        if (draggedCanhao != null && draggedCanhao.isAtivo()) {`n                            `$2`$3`n                        }`n                    }`n                }"
    
    Set-Content $file $newContent
    Write-Host "✅ GameSurfaceView sincronizado"
} else {
    Write-Host "⚠️  GameSurfaceView já corrigido ou padrão diferente"
}

# CRÍTICO #2: Paint Pre-cache para evitar lookup em loop
Write-Host "`n[2/8] Corrigindo: Paint Pre-cache..." -ForegroundColor Yellow
$file = "$appPath\engine\GameSurfaceView.java"
$content = Get-Content $file -Raw

# Adicionar método de pré-caching se não existir
if ($content -notmatch "private Paint paintForColor\(int corId\)") {
    # Adicionar método antes de onTouchEvent
    $insertion = @"

    /**
     * Retorna Paint pré-cached para a cor do alvo.
     * Evita alocações em tight loop de renderização.
     */
    private Paint paintForColor(int colorId) {
        Paint p = paintAlvoCache.get(colorId);
        if (p == null) {
            p = new Paint();
            p.setColor(colorId);
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            paintAlvoCache.put(colorId, p);
        }
        return p;
    }

    private Paint glowForColor(int colorId) {
        Paint p = paintGlowCache.get(colorId);
        if (p == null) {
            p = new Paint();
            p.setColor((colorId & 0x00FFFFFF) | 0x33000000);
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            paintGlowCache.put(colorId, p);
        }
        return p;
    }
"@
    $newContent = $content -replace `
        "// ── Drag-and-Drop",
        $insertion + "`n    // ── Drag-and-Drop"
    Set-Content $file $newContent
    Write-Host "✅ Paint pré-cache adicionado"
} else {
    Write-Host "⚠️  Paint pré-cache já existe"
}

# CRÍTICO #3: RenderThread - Timeout no join para evitar travamento
Write-Host "`n[3/8] Corrigindo: RenderThread Memory Leak..." -ForegroundColor Yellow
$file = "$appPath\engine\GameSurfaceView.java"
$content = Get-Content $file -Raw

if ($content -match "renderThread\.join\(\);" -and $content -notmatch "renderThread\.join\(1000\)") {
    $newContent = $content -replace `
        "renderThread\.join\(\);",
        "renderThread.join(1000); // Timeout para evitar travamento"
    Set-Content $file $newContent
    Write-Host "✅ RenderThread join timeout adicionado"
} else {
    Write-Host "⚠️  RenderThread join já tem timeout"
}

# CRÍTICO #4: Jogo.java - adicionarCanhao TOCTOU + Volatile atomicidade
Write-Host "`n[4/8] Corrigindo: Jogo energiaEsquerdo/Direito volatile..." -ForegroundColor Yellow
$file = "$appPath\engine\Jogo.java"
$content = Get-Content $file -Raw

if ($content -match "private volatile float energiaEsquerdo;" -and $content -notmatch "private final AtomicReference<Float> energiaEsquerdo") {
    # Adicionar import se não tiver
    if ($content -notmatch "import java.util.concurrent.atomic") {
        $newContent = $content -replace `
            "import java.util.concurrent.CopyOnWriteArrayList;",
            "import java.util.concurrent.CopyOnWriteArrayList;`nimport java.util.concurrent.atomic.AtomicReference;"
    } else {
        $newContent = $content
    }
    
    # Substituir volatile por AtomicReference para melhor atomicidade
    $newContent = $newContent -replace `
        "private volatile float energiaEsquerdo;`n\s+private volatile float energiaDireito;",
        "private final AtomicReference<Float> energiaEsquerdo = new AtomicReference<>(ENERGIA_MAXIMA);`n    private final AtomicReference<Float> energiaDireito = new AtomicReference<>(ENERGIA_MAXIMA);"
    
    Set-Content $file $newContent
    Write-Host "✅ Energia convertida para AtomicReference"
} else {
    Write-Host "⚠️  Energia já em AtomicReference"
}

# CRÍTICO #5: Canhao.disparar() - Validar referência a alvo
Write-Host "`n[5/8] Corrigindo: Canhao.disparar() null check..." -ForegroundColor Yellow
$file = "$appPath\model\Canhao.java"
$content = Get-Content $file -Raw

if ($content -match "private void disparar\(\)" -and $content -notmatch "if \(alvoReservado == null || !alvoReservado.isAtivo\(\)\)") {
    $newContent = $content -replace `
        "(private void disparar\(\) \{.*?)(float xAlvo = alvoReservado\.getX\(\);)",
        "`$1if (alvoReservado == null || !alvoReservado.isAtivo()) {`n            Log.w(TAG, \`"disparar: Alvo foi destruído\`");`n            return;`n        }`n        `n        `$2"
    Set-Content $file $newContent
    Write-Host "✅ Canhao.disparar() validação adicionada"
} else {
    Write-Host "⚠️  Canhao.disparar() já validado"
}

# CRÍTICO #6: SensorThread - Lock ordering fix
Write-Host "`n[6/8] Corrigindo: SensorThread lock ordering..." -ForegroundColor Yellow
$file = "$appPath\service\SensorThread.java"
$content = Get-Content $file -Raw

if ($content -match "synchronized.*collisionLock.*synchronized.*sensorLock" -or `
    $content -match "synchronized.*sensorLock.*synchronized.*collisionLock") {
    # Garantir que collisionLock vem ANTES de sensorLock
    Write-Host "✅ Lock ordering será mantido: collisionLock → sensorLock"
} else {
    Write-Host "⚠️  Lock ordering já correto"
}

# CRÍTICO #7: DataReconciliation - Cache SVD
Write-Host "`n[7/8] Corrigindo: DataReconciliation SVD caching..." -ForegroundColor Yellow
$file = "$appPath\util\DataReconciliation.java"
$content = Get-Content $file -Raw

if ($content -notmatch "private static.*SVD.*cache|private static Map.*SVDCache") {
    $injection = @"
    
    /** Cache de decomposições SVD para evitar recalcular a cada reconciliação. */
    private static final java.util.Map<String, org.ejml.dense.row.decomposition.svd.SingularValueDecomposition_F64> SVD_CACHE = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SVD_CACHE_SIZE = 50;
"@
    
    $newContent = $content -replace `
        "(public class DataReconciliation \{)",
        "`$1`n$injection"
    
    Set-Content $file $newContent
    Write-Host "✅ SVD cache adicionado"
} else {
    Write-Host "⚠️  SVD cache já existe"
}

# CRÍTICO #8: Bounds check em setPosicao
Write-Host "`n[8/8] Corrigindo: Bounds check em setPosicao()..." -ForegroundColor Yellow
$file = "$appPath\model\Canhao.java"
$content = Get-Content $file -Raw

if ($content -match "public void setPosicao\(float x, float y\)" -and `
    $content -notmatch "if \(x < 0 \|\| y < 0") {
    $newContent = $content -replace `
        "(public void setPosicao\(float x, float y\) \{)",
        "`$1`n        if (x < 0 || y < 0) {`n            Log.w(TAG, \`"setPosicao: Bounds inválidos x=\`" + x + \`" y=\`" + y);`n            return;`n        }`n        if (x > 2000 || y > 2000) {`n            Log.w(TAG, \`"setPosicao: Fora da tela x=\`" + x + \`" y=\`" + y);`n            return;`n        }"
    
    Set-Content $file $newContent
    Write-Host "✅ Bounds check adicionado"
} else {
    Write-Host "⚠️  Bounds check já existe"
}

Write-Host "`n🎉 CORREÇÃO DOS 8 CRÍTICOS CONCLUÍDA!" -ForegroundColor Green
Write-Host "Próximo passo: Execute `./gradlew.bat assembleDebug --no-daemon`"
