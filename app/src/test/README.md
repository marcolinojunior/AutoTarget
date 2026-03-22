# Testes Unitários - Projeto AutoTarget

Este diretório contém a suíte de testes unitários do projeto **AutoTarget**, desenvolvida com JUnit 4. Os testes foram projetados para validar os requisitos críticos do sistema, incluindo lógica de movimento, detecção de colisões, sincronização e polimorfismo.

## 🎯 Objetivo dos Testes
Conforme os requisitos do projeto (Seção 6.1.7), os testes focam em métodos críticos para garantir a estabilidade do motor de jogo e a correção dos cálculos físicos e matemáticos.

---

## 🧪 Estrutura e Explicação dos Testes

### 1. `AlvoTest.java`
Foca na hierarquia da classe `Alvo` e no comportamento dinâmico dos inimigos.
*   **`testCriacaoAlvoComum`**: Garante que os atributos iniciais (posição, raio, velocidade) são atribuídos corretamente.
*   **`testAlvoRapidoTemVelocidadeSuperior`**: Valida a regra de negócio onde o `AlvoRapido` deve possuir um multiplicador de velocidade (2x) em relação ao alvo comum.
*   **`testPolimorfismoMover`**: Verifica se o método `mover()` comporta-se de forma distinta e correta para cada subclasse, essencial para o requisito de polimorfismo.
*   **`testCalcularDistancia...`**: Conjunto de testes para o método estático de distância euclidiana, fundamental para a detecção de colisões e mira dos canhões.
*   **`testAlvoEhThread`**: Confirma que a arquitetura de concorrência está sendo respeitada (cada alvo deve ser uma `Thread`).

### 2. `CanhaoTest.java`
Valida a lógica de defesa e as regras de restrição por lado (Esquerdo/Direito).
*   **`testCriacaoCanhao...`**: Verifica o posicionamento e a atribuição do `Lado` (Esquerdo/Direito).
*   **`testDisparoComAlvoMesmoLado` vs `testDisparoIgnoraAlvoOutroLado`**: Valida a lógica competitiva onde um canhão só deve disparar contra alvos que cruzaram para o seu respectivo campo de atuação.
*   **`testPenalidadeAumentaIntervaloDisparo`**: Verifica o requisito de "custo" do sistema: se houver excesso de recursos, a taxa de disparo deve diminuir (dobrando o intervalo).
*   **`testDeterminacaoLado`**: Testa a função utilitária que divide a tela verticalmente para atribuir a posse do alvo.

### 3. `ProjetilTest.java`
Valida a física dos projéteis e a precisão da detecção de impacto.
*   **`testColisaoDetectada`**: Verifica se o impacto é registrado quando as coordenadas do projétil e do alvo se sobrepõem (baseado em seus raios).
*   **`testColisaoNaFronteiraDaDistancia`**: Um teste de caso de borda para garantir que a colisão seja detectada exatamente no limite dos raios (`distancia == raio1 + raio2`).
*   **`testMovimentoDiagonal`**: Valida o cálculo de vetores, garantindo que o projétil se desloque corretamente em qualquer ângulo baseado em sua direção normalizada.

### 4. `JogoExceptionTest.java`
*   **`testLancamentoExcecao...`**: Valida o requisito 6.1.6, garantindo que o sistema lance e trate corretamente a exceção personalizada `JogoException` em cenários inválidos (ex: adicionar canhão fora da tela).

---

## 🛠️ Como Executar os Testes
Você pode executar os testes diretamente pelo Android Studio ou via linha de comando utilizando o Gradle:

```bash
./gradlew :app:testDebugUnitTest
```

## 🛡️ Sincronização e Concorrência
Os testes também servem para validar indiretamente a robustez das regiões críticas (`synchronized`). Ao instanciar `Alvo` e `Canhao` com objetos de trava (`collisionLock`), garantimos que a lógica de detecção de colisão não cause condições de corrida (`race conditions`) durante as verificações de estado.
