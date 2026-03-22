# AutoTarget

**Universidade Federal de Lavras (UFLA)**  
**Curso:** Engenharia de Controle e Automação  
**Disciplina:** GAT108 - Automação Avançada  

---

## Visão Geral do Jogo

O AutoTarget é um simulador de defesa em tela dividida onde canhões automáticos devem interceptar alvos móveis. O sistema exige uma forte gestão de recursos, visto que a movimentação e os disparos geram consumo de energia, requerendo otimização contínua para manter a defesa do sistema operante.

O ambiente de simulação opera em tempo real e lida com múltiplos objetos calculando simultaneamente o uso de recursos, verificação de trajetórias e a eficiência geral dos disparos.

## Status Atual (Foco na AV1)

A primeira avaliação do projeto (AV1) está em **andamento**. O foco central até o momento foi o desenvolvimento de um motor puramente multithread do zero, priorizando sólidas bases em engenharia de software e concorrência:

*   **Programação Orientada a Objetos:** Uso ostensivo de herança e polimorfismo, aplicados especialmente à modelagem dos diferentes comportamentos dos Alvos.
*   **Controle Manual e Rigoroso de Concorrência:** Para aprofundar o aprendizado em paralelismo, abolimos as coleções embutidas thread-safe. A sincronização de recursos compartilhados no jogo e a prevenção de falhas — como *Race Conditions* e `ConcurrentModificationException` — foram codificadas totalmente do zero, na mão, com intensa utilização de blocos `synchronized` e monitores.
*   **Separação Arquitetural:** O projeto desacopla perfeitamente a lógica visual da lógica matemática:
    *   A **Interface Gráfica** opera baseada em uma rotação de ciclos limpa conduzida pela `RenderThread`, cujo objetivo único é varrer o estado atual e renderizar componentes a frequências de ~30/60 FPS no Android.
    *   A **Lógica de Física** atualiza exaustivamente as mecânicas, velocidades espaciais e colisões de forma paralela através de um `PhysicsTimer` independente.

## Principais Componentes (Arquitetura)

A espinha dorsal das entidades vivas se baseia na descentralização:

*   As classes **Alvo**, **Canhao** e **Projetil** são totalmente autônomas, executando suas lógicas ativas atreladas às suas próprias *Threads*.
*   O objeto **Jogo** atua como o mediador global e gerenciador central. Ele inicializa as fases, orquestra o ciclo de vida dessas entidades em execução e gerencia interações vitais para unificar o fluxo, como as validações de acertos.

## Tecnologias Utilizadas

*   **Linguagem:** Java
*   **Renderização:** Android SDK (Canvas API via SurfaceView)
*   **Testes:** JUnit 4

## Próximos Passos (Trabalhos Futuros - AV2/AV3)

A base preparada durante a AV1 estrutura confortavelmente as próximas expansões arquiteturais. O Roadmap para as avaliações vindouras prevê:

*   Desenvolvimento do subsistema de **Reconciliação de Dados**.
*   Uso analítico e captação distribuída através de **Sensores Virtuais**.
*   Aplicações backend contemplando **Persistência de Dados e Telemetria** utilizando os serviços do Firebase.
*   Adição da camada protetiva de Criptografia para tráfego seguro de informações.

## Como Executar

1. Clone pelo terminal ou interface gráfica:
   ```bash
   git clone https://github.com/marcolinojunior/AutoTarget.git
   ```
2. Abra a pasta do projeto utilizando o **Android Studio**.
3. Deixe o **Gradle** construir a indexação e baixar requerimentos automaticamente.
4. Escolha ou levante um emulador de sua preferência (`AVD`), ou ainda conecte seu dispositivo físico (USB/Wi-Fi Debugging).
5. Pressione **Run (Shift + F10)** para compilar o APK, implantar no dispositivo e inicializar a simulação.
