# AutoTarget - Wiki & Documentação do Projeto

## 1. O que é o AutoTarget? (O Porquê do Projeto)
O AutoTarget é um simulador de sistema de defesa automatizado desenvolvido nativamente para dispositivos Android, utilizando a linguagem de programação Java.

**Objetivo Intelectual e Prático (Foco AV1):**
O desenvolvimento da versão inicial (**AV1**) foca na fundação escalável do jogo, aplicando na prática:
- **Programação Concorrente & Threads:** Múltiplas entidades (Alvos, Canhões, Projéteis, Jogo) operando em tempo real como threads independentes.
- **Sincronização:** Uso de `synchronized` e travas para proteger o acesso às listas globais (Região Crítica), evitando *Race Conditions*.
- **POO Avançada:** Aplicação de herança, polimorfismo (`AlvoComum` vs `AlvoRapido`), e tratamento de erros customizado com a classe `JogoException`.
- **Testes Unitários:** Validação das regras de negócio e de movimentação com **JUnit**.
- **Serviços em Background:** Integração inicial de simulação de sensores, reconciliação de dados e comunicação segura (Criptografia) com Firestore (Firebase).

O cenário base da **AV1** consiste num canvas unificado onde alvos circulares surgem aleatoriamente e o jogador pode posicionar canhões automáticos (que disparam proativamente nas ameaças mais próximas).

---

## 2. Como foi feito? (Arquitetura e Concorrência)
O jogo foi construído utilizando o **Android Studio**, com a interface de jogo (UI) principal renderizada num Canvas através do componente nativo `SurfaceView`.
Para gerenciar a lógica de milhares de interações espaciais, o AutoTarget adota uma arquitetura multi-threaded baseada no padrão produtor-consumidor e loop autônomo:

- **UI Thread (Renderização):** Apenas desenha os elementos no quadro (Canvas) em `GameSurfaceView`.
- **Thread Jogo (Loop Principal):** Controla o tempo de partida, orquestra colisões complexas e atua na energia/placar da tela (sempre de forma sincronizada).
- **Thread por Entidade (Alvo, Canhão, Projétil):** Cada instância criada no jogo atua iterativamente no seu próprio `run()`. Devido à alteração do mesmo espaço de memória (listas de projéteis e alvos), utilizamos blocos de sincronização (ex: `synchronized` e travas/locks) que tratam _Race Conditions_.
- **Threads de Backgound (Serviços):** Telemetria assíncrona; como os `SensorThread`, a `ReconciliacaoThread` atuando em cálculo pesado e a camada de Input/Output do `FirebaseIOThread`.

---

## 3. Estrutura de Arquivos e Código (A Wiki)

Abaixo está o detalhamento wiki de todo o código fonte e as responsabilidades dos arquivos implementados:

### 📦 Pacote `com.autotarget` (Módulo Principal)
- **`MainActivity.java`**: A porta de entrada (Activity Android). Responsável por inicializar e sobrepor views, instanciando o `GameSurfaceView` no ciclo de vida correto (onCreate/onResume).

### 📦 Pacote `com.autotarget.engine`
Núcleo do Game Engine 2D de tempo real.
- **`GameSurfaceView.java`**: Estende `SurfaceView` e encapsula a tela de jogo. Ele pinta no _Canvas_ a divisão de áreas, os canhões, e processa os toques de tela iniciais.
- **`Jogo.java`**: A Thread principal supervisora. Mantém e sincroniza o acesso às listas de alvos na tela, gerencia Game Over, tempos (60s) e pontuações de ambos os `Lados`.

### 📦 Pacote `com.autotarget.model`
Contém as regras vitais do simulador (Os "Models"). Como se trata de simulador autônomo de comportamento, as models são também `Threads`.
- **`Alvo.java`**: Classe base Thread, contém coordenadas `(x, y)`, raio de hitbox, flags de estado `ativo` e a base matemática de navegação.
- **`AlvoComum.java`**: Implementação base e herdeira de `Alvo`. Possui velocidade constante.
- **`AlvoRapido.java`**: (*Polimorfismo*): Implementação de Alvo que sobrescreve atributos de movimento para possuir navegação superior e mais ágil.
- **`Canhao.java`**: Fio de execução independente. Fica adormecido na tela (Thread suspensa ou em `sleep()`) de forma periódica até analisar e lançar autonomamente um novo `Projetil` com cálculos de seno/cosseno até o alvo ativo mais próximo.
- **`Projetil.java`**: Thread gerada por canhões. Representa o tiro, viaja vetorialmente em linha reta do canhão até a coordenada alvo. A verificação rigorosa de *hitbox* ocorre no método `collide()`.
- **`Lado.java`**: Enumeração prevista na arquitetura geral que ajuda a categorizar a posse das unidades, preparando o terreno para etapas competitivas.

### 📦 Pacote `com.autotarget.service`
Camadas asíncronas para nuvem e cálculos extras sem penalidade de frames (60 FPS).
- **`SensorThread.java`**: Fio de execução contínuo monitorando movimentações das coordenadas de forma periódica.
- **`DataReconciliation.java` & `ReconciliacaoThread.java`**: Algoritmo central de reconciliação de dados. Otimiza, limpa e recalcula anomalias de sensores garantindo decisões assertivas de CPU virtual dos "Canhões".
- **`Cryptography.java`**: Serviço de chaveação algorítmica para encriptar string JSON, focado em segurança orgânica da memória.
- **`FirestoreRepository.java` & `FirebaseIOThread.java`**: Camada que envia e serializa dados das rodadas para o Google Firebase por debaixo da mesa de trabalho, garantindo não interrupção na IU (Input/Output).

### 📦 Pacote `com.autotarget.exception`
- **`JogoException.java`**: Controla e encapsula quebras de regra do sistema. (ex: Criação de canhão excede limite ou saldo enérgico vazio).

### 📦 Diretório de Testes (`test/.../autotarget`)
Bateria de testes construída utilizando o framework JUnit para atestar o pleno funcionamento antes mesmo do build em dispositivo final:
- **`AlvoTest.java`**: Testa o avanço da geometria vetorial de movimento num plano X/Y abstrato.
- **`CanhaoTest.java`**: Testa lógicas de inicialização e se projeta ângulos corretamente em relação à hitbox do alvo inimigo.
- **`ProjetilTest.java`**: Testa a precisão teórica de verificação matemática de colisão da raio de interseção.
- **`JogoExceptionTest.java`**: Garante que os limites das restrições definidas não falhem no modo de execução.

---

## 4. Como Compilar (Build and Run)
1. Efetue um "Clone" ou Checkout deste projeto utilizando Git.
2. Abra seu **Android Studio (Bumblebee+ ou versão Hedgehog recomendada)**.
3. Permita que o Wrapper do **Gradle** conclua suas dependências sincronizando os repositórios Maven `(Sync Project)`.
4. Aperte o botão "Run / Play" (Shift + F10) na barra de ferramentas usando o app em Emulador ou Dispositivo Físico usando modo desenvolvedor e depuração USB.
