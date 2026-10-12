# SDK do bitubi-inference-4j: experiência do desenvolvedor (DX)

Data: 2026-10-12. Estado: **proposta, aguardando aprovação do autor** (nada implementado).

## 1. Objetivo

Quem integra o motor num app Java (Spring Boot, Quarkus, Micronaut ou `main` simples) deve chegar ao
primeiro token com poucas linhas, sem conhecer tensores, `DType` nem flags da JVM, e com erros que
dizem o que fazer. O projeto é novo; a retrocompatibilidade é mantida onde é barata (a API atual
continua a funcionar), mas a API nova é a porta de entrada documentada.

## 2. O que existe hoje (lido no código)

| Ponto | Situação | Efeito para quem integra |
|---|---|---|
| Entrada | Três estáticos soltos: `SafeTensorSupport.maybeDownloadModel(dir, id)`, `ModelSupport.loadModel(File, DType, DType)` e `AbstractModel.generate(UUID, PromptContext, float, int, BiConsumer)` | O iniciante precisa descobrir a ordem e o significado de `DType.F32, DType.I8` |
| Tipo central | `AbstractModel` expõe `forward`, `batchForward`, `makeTensor`, `sample`, `getLogits` | Superfície pública enorme e interna misturada com a de uso; difícil de manter estável |
| Limite de tokens | O 4.º argumento de `generate` é o limite **total** (prompt incluído); valor baixo gera `IllegalArgumentException: Prompt exceeds max tokens` | Armadilha silenciosa (acontece com 16 num prompt normal) |
| Streaming | Só callback `BiConsumer<String, Float>`; sem cancelamento | Não encaixa em `Flux`, SSE, `Flow` ou `Stream` |
| Ciclo de vida | `Generator extends Closeable` (bom), mas sem aviso quando se esquece de fechar | Fuga de memória off-heap |
| JVM | Exige `--add-modules jdk.incubator.vector` (e `--enable-native-access`) | Falha em runtime se o app não passar as flags; mensagem pouco guiada |
| Configuração | Propriedades `-D` estáticas (`bi4j.*`), lidas na carga da classe | Não se configura por código nem por `application.yml` |
| Erros | `IOException` e `IllegalArgumentException` genéricos | Sem tipos para "modelo não encontrado", "formato não suportado", "sem memória" |
| Empacotamento | Um artefato `-core` (Panama por padrão; o nativo é opcional); sem BOM | Aceitável; falta BOM e módulos de integração |
| Integrações | A integração LangChain4j existente aponta para os artefatos antigos do Jlama | Quebra com o rename; precisa de adaptador próprio |

## 3. API proposta (camada fina por cima da atual)

```java
try (Bi4j bi4j = Bi4j.builder()
        .model("tjake/Llama-3.2-1B-Instruct-JQ4")   // id do Hugging Face ou caminho local
        .modelsDir(Path.of("models"))
        .build()) {                                  // baixa e carrega; escolhe os DType certos

    ChatResponse r = bi4j.chat()
        .system("Você é um assistente curto.")
        .user("Qual a melhor época para plantar abacate?")
        .maxNewTokens(256)                           // tokens NOVOS, sem a armadilha do total
        .temperature(0.2f)
        .generate();
    System.out.println(r.text());

    bi4j.chat().user("Olá").stream()                 // Flow.Publisher<Token>, cancelável
        .subscribe(token -> System.out.print(token.text()));

    float[] vector = bi4j.embed("texto");            // modelos de embedding
}
```

Princípios:
- **Um ponto de entrada** (`Bi4j`), imutável e seguro entre threads depois de construído; `AutoCloseable`.
- **Padrões inteligentes:** `DType` escolhido pelo modelo e pelo hardware (`MachineSpec`); o iniciante não vê tensores.
- **Limite em tokens novos** (`maxNewTokens`); o limite total fica na API antiga.
- **Streaming reativo:** `Flow.Publisher<Token>` (JDK, sem dependência) e um `Iterator`/`Stream` bloqueante.
- **Erros tipados** (`ModelNotFoundException`, `UnsupportedModelException`, `EngineSetupException`) com a causa e a correção na mensagem.
- **Falha cedo e guiada:** ao construir, se `jdk.incubator.vector` não estiver ativo, a mensagem diz exatamente a flag e onde pôr (Maven Surefire, Spring Boot plugin, `JAVA_TOOL_OPTIONS`).
- A API antiga (`ModelSupport`, `AbstractModel.generate`) não muda de comportamento; a nova fica em cima dela.

## 4. Módulos novos

| Módulo | Conteúdo | Observação |
|---|---|---|
| `bitubi-inference-4j-bom` | Gerencia as versões de todos os artefatos | Uma linha no `dependencyManagement` do cliente |
| `bitubi-inference-4j-sdk` | `Bi4j`, builders, `Token`, `ChatResponse`, exceções | Depende só do `-core` |
| `bitubi-inference-4j-spring-boot-starter` | Auto-configuração: `bi4j.model`, `bi4j.models-dir`, bean `Bi4j`, health indicator | Alvo Spring Boot 4 (alinhado ao sub-projeto 2) |
| `bitubi-inference-4j-langchain4j` | Adaptador `ChatModel`/`EmbeddingModel` | Substitui a integração antiga do Jlama |
| `examples/` | `main` simples, Spring Boot e streaming | Compilados na CI para não apodrecerem |

Spring AI e Quarkus ficam para depois da validação do starter.

## 5. Critérios de aceitação (mensuráveis)

- Do zero ao primeiro token em **≤ 10 linhas** de código de aplicação (sem contar imports).
- Um app Spring Boot de exemplo responde a um prompt com **só** a dependência do starter e `bi4j.model=...`.
- Esquecer `--add-modules jdk.incubator.vector` produz **uma** mensagem de erro com a correção, testada.
- `maxNewTokens` nunca dispara `Prompt exceeds max tokens` por causa do tamanho do prompt (testado com prompt longo).
- Javadoc completo nos tipos públicos do `-sdk`; os exemplos compilam na CI em Linux, Windows e macOS.
- Nenhuma regressão: a suíte atual e o teste de fumaça (saída igual à referência) continuam verdes.

## 6. Fases

1. `-sdk` com `Bi4j`, chat síncrono, streaming, embeddings, erros tipados e a verificação da flag (sem Spring).
2. BOM e exemplos compilados na CI.
3. Starter Spring Boot (junto ao upgrade para Spring Boot 4 e ao bytecode 25).
4. Adaptador LangChain4j.

## 7. Questões em aberto

- A flag `--add-modules` não pode ser ativada por código em apps de classpath: o starter pode apenas validar e orientar. Confirmar se há mecanismo viável por manifesto ou plugin antes de prometer "zero flags".
- Cancelamento do streaming: o motor atual não expõe interrupção a meio da geração; verificar o custo de a introduzir no `generate`.
- Nome do pacote público: `ao.bitubi.inference4j` (atual) ou um pacote `...sdk` dedicado para a API nova.
- Escolha de `DType` automática: precisa de critério por modelo e por RAM; definir com o `engine-perf`.
