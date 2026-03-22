Esta release marca a entrega oficial da primeira fase (AV1) do projeto AutoTarget. O foco desta versão foi estabelecer uma arquitetura sólida para o simulador de defesa, aplicando rigorosamente conceitos de Programação Orientada a Objetos, controle manual de concorrência (Threads e Sincronização) e separação de responsabilidades no Android.

✨ Principais Entregas e Funcionalidades
Concorrência Manual e Regiões Críticas: Implementação de controle de acesso simultâneo usando blocos synchronized e monitores (collisionLock) nas listas do jogo, substituindo coleções thread-safe automáticas para demonstrar o domínio do controle de concorrência.

Resolução de Race Conditions: Lógica atômica garantida na verificação de colisões e pontuação, utilizando Iterator nativo para manipulação segura da memória e prevenção de ConcurrentModificationException.

Separação de Arquitetura (Game Loop): Desacoplamento completo entre a renderização e a física do jogo. A RenderThread agora atua exclusivamente no desenho da UI da GameSurfaceView, enquanto o PhysicsTimer dedicado processa cálculos e colisões a ~60 FPS.

Lógica de Colisão Orientada a Objetos: O método run() da Thread do Alvo agora é responsável por detectar as colisões com a lista de projéteis (cumprindo estritamente a rubrica acadêmica).

🛠️ DevOps, Qualidade e Boas Práticas
Integração Contínua (CI): Adição de pipeline via GitHub Actions (android-ci.yml) para garantir build e execução de testes automatizados a cada novo push.

Testes Unitários (JUnit 4): Cobertura de métodos críticos do domínio, incluindo cálculos de distância (AlvoTest), detecção de sobreposição (ProjetilTest) e validação de regras de negócio (JogoExceptionTest).

Clean Code & Android Resources: Remoção de hardcoded strings e colors das classes Java, centralizando-os nos arquivos strings.xml e colors.xml.

Documentação Profissional: Inclusão de Javadocs detalhados nos construtores e métodos públicos do domínio (com.autotarget.model e com.autotarget.engine).

🚀 Próximos Passos (Roadmap para AV2/AV3)
A base desta release já pavimenta o caminho para as próximas fases do projeto, que incluirão:

Implementação dos Sensores Virtuais para coleta de telemetria.

Algoritmos de Reconciliação de Dados.

Persistência na nuvem com Firebase Firestore e criptografia.
