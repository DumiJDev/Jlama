# aby-inference Fundação (sub-projeto 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar o fork do Jlama no `aby-inference` (rename, atribuição, build em JDK 25, CI em Linux, Windows e macOS, publicação), sem mudar comportamento.

**Architecture:** Mudanças mecânicas em commits pequenos e revisáveis. Primeiro limpar o ruído de fim de linha, depois fazer o build funcionar em JDK 25 (baseline), só então renomear, para que uma quebra de rename seja distinguível de uma quebra de build. Um teste de fumaça com modelo real prova que o motor e o código nativo continuam funcionando.

**Tech Stack:** Java 25 (mínimo), Maven (wrapper), JUnit 4, GitHub Actions, GitHub Packages.

**Spec:** `docs/superpowers/specs/2026-10-11-aby-inference-foundation-design.md`

## Global Constraints

- Pacote Java: `ao.bitubi.aby.inference` (o hífen não é válido em pacote).
- `groupId`: `ao.bitubi.aby-inference`; `artifactId`s: `aby-inference-parent`, `aby-inference-core`, `aby-inference-native`, `aby-inference-net`, `aby-inference-cli`, `aby-inference-tests`.
- CLI: comando `aby`; classe principal `ao.bitubi.aby.inference.cli.AbyCli` (hoje `JlamaCli`).
- Versão: `<revision>` = `0.1.0-SNAPSHOT`.
- JDK mínimo e único alvo: 25 (LTS). JDK 20, 21 e 22 deixam de ser suportados. A CI roda em Linux, Windows e macOS, sempre com JDK 25.
- Atribuição Apache 2.0: manter `LICENSE`; criar `NOTICE`; preservar cabeçalhos de copyright originais; marcar arquivos modificados; não usar nome/marca do autor original para sugerir endosso.
- Organização GitHub: `bitubi-solutions`; `upstream` aponta para `tjake/jlama`.
- Ids de modelo do Hugging Face (`tjake/...`) são nomes externos e permanecem; idem o clone `tjake/sdkman-for-toolchains` no Dockerfile.
- Nunca adicionar trailers do Claude (`Co-Authored-By`, `Claude-Session`) em commits ou PRs (regra do autor).
- Nenhuma mudança de lógica do motor. Cada task termina com o build verde.

## Review Focus

1. Caminho de arquivo com espaço, `$` ou CRLF no rename mecânico: arquivos `constants$0.java` (nomes com `$`) devem ser movidos e referenciados corretamente.
2. Referência ao pacote antigo em string (nome de logger em `logback*.xml`, `package` do `.proto`, `apiPackage` no `jlama-net/pom.xml`, `Main-Class` no manifesto): o rename não pode deixá-las apontando para classes inexistentes (falha só em runtime).
3. JAR multi-release do `jlama-native` sem as classes `java25/` (`META-INF/versions/25`): o motor cai silenciosamente para o caminho sem código nativo (mais lento), sem erro de build.
4. Binário nativo e classificador `${os.detected.name}-${os.detected.arch}` em macOS e Windows: o rename não deve quebrar `jlama-native` fora do Linux.
5. Usuário com JDK 26 ou superior: a Vector API muda entre rodadas de incubação e pode dar `NoSuchMethodError`. Só o JDK 25 é testado; registrar isso no README.

---

### Task 1: Normalizar fim de linha

**Files:**
- Create: `.gitattributes`

**Interfaces:**
- Produces: working tree sem diferenças fantasmas; todas as tasks seguintes assumem `git status` limpo.

- [ ] **Step 1: Criar a branch de trabalho**

```bash
git checkout docs/aby-foundation-spec
git checkout -b foundation/aby-rename
```

- [ ] **Step 2: Confirmar o estado inicial (só fim de linha)**

Run: `git diff --ignore-cr-at-eol --stat | tail -1`
Expected: sem saída (nenhuma diferença real de conteúdo).

- [ ] **Step 3: Criar `.gitattributes`**

```gitattributes
* text=auto eol=lf
*.sh text eol=lf
mvnw text eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.jpg binary
*.png binary
*.jar binary
```

- [ ] **Step 4: Renormalizar e verificar**

Run: `git add .gitattributes && git add --renormalize . && git status --short | grep -v '^??'`
Expected: apenas `A  .gitattributes` (nenhum arquivo `M`). Se aparecerem `M`, rode `git diff --ignore-cr-at-eol --stat` de novo: deve continuar vazio.

- [ ] **Step 5: Commit**

```bash
git commit -m "chore: normaliza fim de linha com .gitattributes
```

---

### Task 2: Build e testes verdes em JDK 25 (baseline antes do rename)

**Files:**
- Modify: `pom.xml` (compiler e surefire, linhas ~118-205)
- Modify: `jlama-native/pom.xml` (execuções `compile-jdk20/21/22`)
- Delete: `jlama-native/src/main/java20/`, `jlama-native/src/main/java21/`
- Rename: `jlama-native/src/main/java22/` para `jlama-native/src/main/java25/`
- Modify: `run-cli.sh`, `run-cli.bat` (o `.bat` não tem verificação de versão; só o `.sh` é alterado)
- Create (local, não versionado): `~/.m2/toolchains.xml`

**Interfaces:**
- Produces: `./mvnw -B verify` verde com um único toolchain, JDK 25 (o mínimo suportado); testes executados no JDK 25 (execução `default-test`).

Contexto: hoje o build exige toolchains JDK 20, 21 e 22 (não instalados nesta máquina: há 21, 24 e 25 em `~/.sdkman/candidates/java`). O `jlama-native` compila fontes por versão: `java20/` e `java21/` usam FFM em preview, `java22/` usa FFM final. Com o JDK 25 como mínimo, só o último fica; ele passa a se chamar `java25/` (o JAR multi-release usa `META-INF/versions/25`).

- [ ] **Step 1: Criar toolchain local**

Crie `~/.m2/toolchains.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides><version>25</version></provides>
    <configuration><jdkHome>/home/dumi/.sdkman/candidates/java/25.0.3-amzn</jdkHome></configuration>
  </toolchain>
</toolchains>
```

- [ ] **Step 2: Provar que o build atual falha (baseline)**

Run: `./mvnw -B -DskipTests package 2>&1 | tail -15`
Expected: FAIL com erro de toolchain (`Cannot find matching toolchain definitions ... jdk [ version='20' ]`, `'21'` ou `'22'`).

- [ ] **Step 3: Remover JDK 20 e 21 e renomear o diretório do 22**

```bash
git rm -r jlama-native/src/main/java20 jlama-native/src/main/java21
git mv jlama-native/src/main/java22 jlama-native/src/main/java25
```

Em `jlama-native/pom.xml`, apague as `<execution>` inteiras com `<id>compile-jdk20</id>` e `<id>compile-jdk21</id>`. Na execução `compile-jdk22`, troque `<id>` para `compile-jdk25`, `<release>22</release>` por `<release>25</release>`, o `<jdkToolchain><version>22</version></jdkToolchain>` por `<version>25</version>`, o `<compileSourceRoot>` para `${project.basedir}/src/main/java25` e remova `<arg>--enable-preview</arg>` (FFM é final desde o 22, e `--release 25` com `--enable-preview` não é necessário aqui).

- [ ] **Step 4: Compilar a base com o JDK 25**

Em `pom.xml`, no `maven-compiler-plugin`, troque:

```xml
<source>20</source>
<target>20</target>
...
<jdkToolchain>
  <version>22</version>
</jdkToolchain>
```

por:

```xml
<source>25</source>
<target>25</target>
...
<jdkToolchain>
  <version>25</version>
</jdkToolchain>
```

- [ ] **Step 4b: Manter o bytecode em 21 (desvio registrado)**

Com `source`/`target` 25 o `jlama-net` falha em runtime: o Spring Boot 3.3.x (ASM) não lê classes versão 69 (`Unsupported class file major version 69`, em `ChatApiTest`). Por isso, no `maven-compiler-plugin` do `pom.xml`, use `<source>21</source>` e `<target>21</target>` (não 25) com um comentário no XML explicando o motivo. O JDK 25 continua sendo o único JDK de build, teste e execução (toolchain, `java25/` do nativo, `run-cli`). Subir o alvo para 25 junto com o upgrade do Spring (sub-projeto 2).

- [ ] **Step 5: Surefire só no JDK 25**

Em `pom.xml`, deixe uma única `<execution>`, a `default-test`, com `<jdkToolchain><version>25</version></jdkToolchain>`; apague as execuções `jdk21-test` e `jdk22-test`. Mantenha o mesmo `argLine` (inclui `--enable-preview`).

- [ ] **Step 6: Ajustar `run-cli.sh` e `run-cli.bat`**

Em `run-cli.sh`, substitua o bloco de verificação de versão por:

```bash
# Verify Java version is JDK 25 or newer
JAVA=$(get_java_exec)
JAVA_MAJOR_VERSION=$(get_java_major_version $JAVA)
if [[ "$JAVA_MAJOR_VERSION" -lt 25 ]]; then
  echo "Error: JDK 25 or newer is required to run this application."
  exit 1
fi
```

Faça o equivalente em `run-cli.bat` (compare a versão principal com 25).

- [ ] **Step 7: Build com JDK 25 como `JAVA_HOME`**

Run: `JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B -DskipTests package 2>&1 | tail -20`
Expected: `BUILD SUCCESS`. Se o `jlama-native` falhar ao compilar o `java25/`, leia o erro: corrija só a flag/`release` em questão; não altere código Java. Confirme o JAR multi-release: `unzip -l jlama-native/target/jlama-native-*-linux-x86_64.jar | grep "META-INF/versions/25"` deve listar classes `cnative`.

- [ ] **Step 8: Rodar os testes**

Run: `JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B clean package 2>&1 | grep -E "Tests run:|BUILD" | tail -15`
Expected: `BUILD SUCCESS`. Use `package`, e não `test`: o `jlama-tests` precisa do JAR multi-release do `jlama-native` (com `META-INF/versions/25`); com `test` ele vê só `target/classes`, onde o Java não aplica multi-release, e os testes nativos falham com `UnsupportedOperationException: Not implemented for this JDK version`. Use `clean` ao mudar `source`/`target`, senão o Maven não recompila e as classes antigas ficam. Testes dependentes de modelos em `../models/` são pulados por `Assume` (não contam como falha). Alguns testes do `jlama-net` e do `TestSample` baixam modelos do Hugging Face (dezenas de minutos na primeira vez); uma falha `Failed to download file` / `Connection reset` é de rede: rode de novo, pois o modelo fica em cache.

- [ ] **Step 9: Commit**

```bash
git add -A pom.xml jlama-native run-cli.sh run-cli.bat
git commit -m "build: exige JDK 25, remove suporte a JDK 20, 21 e 22
```

---

### Task 3: Teste de fumaça com modelo real

**Files:**
- Create: `jlama-tests/src/test/java/com/github/tjake/jlama/model/SmokeTest.java` (o caminho muda no rename, Task 4)

**Interfaces:**
- Consumes: `Downloader(String workingDirectory, String model).huggingFaceModel()`, `ModelSupport.loadModel(File, DType, DType)`, `AbstractModel.generate(UUID, PromptContext, float, int, BiConsumer)` (como em `TestSample.java`).
- Produces: `SmokeTest.generatesSameTokensAsReference` e a referência `smoke-golden-25.txt` em `jlama-tests/src/test/resources/`; o teste prova que o motor e o nativo produzem a mesma saída após o rename.

- [ ] **Step 1: Ver como `TestSample.java` baixa e carrega o modelo**

Run: `sed -n 30,75p jlama-tests/src/test/java/com/github/tjake/jlama/model/TestSample.java`
Expected: usa `Downloader` e `ModelSupport.loadModel` com `tjake/Qwen2.5-0.5B-Instruct-JQ4`. Copie os imports exatos de lá.

- [ ] **Step 2: Escrever o teste (com comparação contra uma saída de referência)**

Com temperatura 0 a geração é determinística na mesma máquina. O teste compara o texto gerado com um arquivo de referência (`smoke-golden-25.txt`, nomeado pela versão do JDK). A referência vale só para a máquina onde foi gravada: o ponto flutuante pode diferir em outra CPU ou SO. Com `SMOKE_RECORD=1` ele grava a referência em vez de comparar.

```java
/*
 * Copyright 2024 T Jake Luciani
 * (cabeçalho Apache 2.0 idêntico ao de conf/license-header)
 */
package com.github.tjake.jlama.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

public class SmokeTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources");

    @Test
    public void generatesSameTokensAsReference() throws Exception {
        Assume.assumeTrue("SMOKE_MODEL=1 required", "1".equals(System.getenv("SMOKE_MODEL")));

        File dir = SafeTensorSupport.maybeDownloadModel("./models", "tjake/Qwen2.5-0.5B-Instruct-JQ4");
        AbstractModel m = ModelSupport.loadModel(dir, DType.F32, DType.I8);

        PromptContext ctx = m.promptSupport().get().builder()
            .addUserMessage("What is the capital of France?")
            .build();

        Generator.Response r = m.generate(UUID.randomUUID(), ctx, 0.0f, 96, (s, f) -> {});
        assertFalse("no tokens generated", r.responseText.isBlank());

        if ("1".equals(System.getenv("SMOKE_RELAXED"))) {
            return; // CI: hardware diferente do de referência; só exige texto não vazio
        }

        Path golden = GOLDEN_DIR.resolve("smoke-golden-" + Runtime.version().feature() + ".txt");
        if ("1".equals(System.getenv("SMOKE_RECORD"))) {
            Files.writeString(golden, r.responseText, StandardCharsets.UTF_8);
            return;
        }
        assertTrue("missing reference " + golden + " (run once with SMOKE_RECORD=1)", Files.exists(golden));
        assertEquals(Files.readString(golden, StandardCharsets.UTF_8), r.responseText);
    }
}
```

O quarto argumento de `generate` é o limite **total** de tokens (prompt incluído); 16 falha com `Prompt exceeds max tokens`. A API de download é `SafeTensorSupport.maybeDownloadModel` (o README, que cita `Downloader`, está desatualizado). Se o download pelo Java cair com `Connection reset`, baixe o `model.safetensors` com `curl -L -C -` para `jlama-tests/models/tjake_Qwen2.5-0.5B-Instruct-JQ4/`: o downloader pula arquivos com o tamanho esperado.

- [ ] **Step 3: Gravar as referências (antes de qualquer rename)**

Execute uma vez, na sua máquina:

Run: `SMOKE_MODEL=1 SMOKE_RECORD=1 JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B -pl jlama-tests -am test -Dtest=SmokeTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run|BUILD"; ls jlama-tests/src/test/resources/smoke-golden-*`
Expected: `BUILD SUCCESS` e o arquivo `smoke-golden-25.txt`, não vazio. (Baixa ~500 MB na primeira vez.) Se o download falhar (modelo removido do Hugging Face), troque o id por outro modelo pequeno que exista e anote no commit. Leia o arquivo: se o texto estiver sem sentido (lixo), pare e investigue antes de seguir.

- [ ] **Step 4: Rodar em modo de comparação**

Run: `SMOKE_MODEL=1 JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B -pl jlama-tests -am test -Dtest=SmokeTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run|BUILD"`
Expected: `Tests run: 1, Failures: 0` e `BUILD SUCCESS`. Rodar duas vezes seguidas deve passar nas duas (confirma o determinismo).

- [ ] **Step 5: Commit**

```bash
git add jlama-tests/src/test/java/com/github/tjake/jlama/model/SmokeTest.java jlama-tests/src/test/resources/smoke-golden-25.txt
git commit -m "test: teste de fumaça com saída de referência
```

---

### Task 3b: Baseline de desempenho (antes do rename)

**Files:**
- Create: `jlama-tests/src/test/java/com/github/tjake/jlama/model/PerfBaselineTest.java` (o caminho muda no rename, Task 4)
- Create: `docs/benchmarks/baseline-<hardware>.md` (o nome é escolhido no Step 3)
- Create: `docs/benchmarks/run-perf.sh`

**Interfaces:**
- Consumes: `Generator.Response` com os campos públicos `promptTokens`, `generatedTokens`, `promptTimeMs`, `generateTimeMs`.
- Produces: números de referência (tokens/s) por hardware, usados na Task 4 e nos sub-projetos 1, 1b e 2.

Métricas deste baseline (as de qualidade e as de GPU entram nos specs dos sub-projetos 1 e 1b): tokens/s do prompt (prefill), tokens/s da geração (decode), tempo de carga do modelo e memória de pico. Os números vêm do próprio `Generator.Response`, sem depender de JMH.

- [ ] **Step 1: Escrever o teste de medição**

```java
/*
 * Copyright 2024 T Jake Luciani
 * (cabeçalho Apache 2.0 idêntico ao de conf/license-header)
 */
package com.github.tjake.jlama.model;

import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import java.io.File;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

public class PerfBaselineTest {

    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final int MAX_TOKENS = 256; // limite total de tokens (prompt incluido), como em TestSample

    @Test
    public void measureTokensPerSecond() throws Exception {
        Assume.assumeTrue("PERF_BASELINE=1 required", "1".equals(System.getenv("PERF_BASELINE")));

        File dir = SafeTensorSupport.maybeDownloadModel("./models", "tjake/Qwen2.5-0.5B-Instruct-JQ4");

        long t0 = System.nanoTime();
        AbstractModel m = ModelSupport.loadModel(dir, DType.F32, DType.I8);
        long loadMs = (System.nanoTime() - t0) / 1_000_000;

        PromptContext ctx = m.promptSupport().get().builder()
            .addUserMessage("Write a short paragraph about the history of the Java programming language.")
            .build();

        for (int i = 0; i < WARMUP_RUNS; i++) {
            m.generate(UUID.randomUUID(), ctx, 0.0f, MAX_TOKENS, (s, f) -> {});
        }

        double prefillSum = 0;
        double decodeSum = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            Generator.Response r = m.generate(UUID.randomUUID(), ctx, 0.0f, MAX_TOKENS, (s, f) -> {});
            prefillSum += r.promptTokens / (double) Math.max(1, r.promptTimeMs) * 1000.0;
            decodeSum += r.generatedTokens / (double) Math.max(1, r.generateTimeMs) * 1000.0;
        }

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        System.out.printf(
            "PERF jdk=%d load_ms=%d prefill_tok_s=%.2f decode_tok_s=%.2f heap_used_mb=%d%n",
            Runtime.version().feature(), loadMs, prefillSum / MEASURED_RUNS, decodeSum / MEASURED_RUNS, usedMb);
    }
}
```

- [ ] **Step 2: Script de execução**

Crie `docs/benchmarks/run-perf.sh`:

```bash
#!/bin/bash
# Uso: docs/benchmarks/run-perf.sh <jdk-home>
set -e
JDK_HOME="${1:?uso: run-perf.sh <jdk-home>}"
echo "== hardware =="
lscpu | grep -E "Model name|^CPU\(s\)|Flags" | sed -E 's/(Flags:).*/\1 (ver avx abaixo)/'
grep -o -w -E "avx512[a-z_]*|avx2|avx|fma|neon" /proc/cpuinfo | sort -u | tr '\n' ' '; echo
echo "== medição =="
PERF_BASELINE=1 JAVA_HOME="$JDK_HOME" ./mvnw -B -pl jlama-tests -am test \
  -Dtest=PerfBaselineTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "^PERF |BUILD"
```

Run: `chmod +x docs/benchmarks/run-perf.sh`

- [ ] **Step 3: Medir e registrar**

Run: `docs/benchmarks/run-perf.sh /home/dumi/.sdkman/candidates/java/25.0.3-amzn | tee /tmp/perf-before.txt`
Expected: uma linha `PERF jdk=25 ...` e `BUILD SUCCESS`. Rode três vezes e anote a variação. Crie `docs/benchmarks/baseline-<hardware>.md` (escolha `<hardware>` pelo modelo da CPU, em minúsculas e com hifens) com: o bloco de hardware impresso pelo script, o commit (`git rev-parse --short HEAD`), o JDK, as três execuções e a faixa de variação observada (mín.-máx.). Esta variação define a tolerância usada na Task 4.

- [ ] **Step 4: Commit**

```bash
git add jlama-tests/src/test/java/com/github/tjake/jlama/model/PerfBaselineTest.java docs/benchmarks
git commit -m "test: baseline de desempenho (tokens/s) antes do rename
```

---

### Task 4: Rename mecânico (pacote, groupId, artefatos, CLI)

**Files:**
- Move: todos os diretórios `.../com/github/tjake/jlama/` para `.../ao/bitubi/aby/inference/` (em `jlama-core`, `jlama-native` (`java/`, `java25/`), `jlama-net`, `jlama-cli`, `jlama-tests`)
- Modify: todos os arquivos que contêm `com.github.tjake` (lista: `grep -rl`)
- Modify: `pom.xml`, `jlama-*/pom.xml`, `jlama-net/src/main/proto/JlamaService.proto`, `jlama-tests/src/test/resources/logback-test.xml`, `conf/logback.xml`, `.run/*.xml`
- Rename: classe `JlamaCli` para `AbyCli`

**Interfaces:**
- Produces: pacote `ao.bitubi.aby.inference.*`; classe principal `ao.bitubi.aby.inference.cli.AbyCli`; artefatos `aby-inference-*`.

- [ ] **Step 1: Lista do que será tocado (para comparar depois)**

Run: `grep -rl "com.github.tjake" . | grep -v '^./.git/' | wc -l`
Expected: `154` (os 154 do levantamento; se diferir, anote o número novo).

- [ ] **Step 2: Mover os diretórios**

```bash
for d in $(find . -type d -path '*/com/github/tjake/jlama' -not -path './.git/*'); do
  base="${d%/com/github/tjake/jlama}"
  mkdir -p "$base/ao/bitubi/aby"
  git mv "$d" "$base/ao/bitubi/aby/inference"
done
find . -type d -empty -path '*/com/*' -not -path './.git/*' -delete
```

Run: `find . -path '*/com/github/tjake*' -not -path './.git/*' | head`
Expected: sem saída.

- [ ] **Step 3: Substituir referências ao pacote em texto**

```bash
grep -rl "com\.github\.tjake\.jlama" . --exclude-dir=.git --exclude-dir=target \
  | xargs sed -i 's/com\.github\.tjake\.jlama/ao.bitubi.aby.inference/g'
grep -rl "com/github/tjake/jlama" . --exclude-dir=.git --exclude-dir=target \
  | xargs sed -i 's#com/github/tjake/jlama#ao/bitubi/aby/inference#g'
```

Isto cobre `package`/`import` Java, `Main-Class`, `apiPackage`/`modelPackage` em `jlama-net/pom.xml`, `package` do `.proto`, `logback*.xml` e as exclusões de `MemorySegment.class` no `jlama-native/pom.xml`.

- [ ] **Step 4: groupId e artifactIds**

```bash
grep -rl "com\.github\.tjake" . --exclude-dir=.git --exclude-dir=target \
  | xargs sed -i 's#<groupId>com\.github\.tjake</groupId>#<groupId>ao.bitubi.aby-inference</groupId>#g'
for m in parent core native net cli tests; do
  grep -rl "jlama-$m" . --include=pom.xml --exclude-dir=.git --exclude-dir=target \
    | xargs sed -i "s#jlama-$m#aby-inference-$m#g"
done
sed -i 's#<revision>0.8.4</revision>#<revision>0.1.0-SNAPSHOT</revision>#' pom.xml
```

Depois renomeie os diretórios de módulo para casar com o `artifactId` e atualize os `<module>` do `pom.xml`:

```bash
for m in core native net cli tests; do git mv jlama-$m aby-inference-$m; done
sed -i 's#<module>jlama-\(core\|native\|net\|cli\|tests\)</module>#<module>aby-inference-\1</module>#' pom.xml
```

Corrija as ocorrências restantes de `jlama-` em caminhos: `grep -rn "jlama-" --include=pom.xml --include=*.sh --include=*.bat --include=*.yaml --include=Dockerfile . | grep -v '^./.git/'` e ajuste cada uma para `aby-inference-` (ex.: `run-cli.sh` usa `./jlama-cli/target/jlama-cli.jar`; `<finalName>` no pom do cli).

- [ ] **Step 5: Renomear a CLI**

```bash
git mv aby-inference-cli/src/main/java/ao/bitubi/aby/inference/cli/JlamaCli.java \
       aby-inference-cli/src/main/java/ao/bitubi/aby/inference/cli/AbyCli.java
grep -rl "JlamaCli" . --exclude-dir=.git --exclude-dir=target | xargs sed -i 's/JlamaCli/AbyCli/g'
```

No `AbyCli.java`, mude o nome do comando do picocli para `aby` (procure `name = "jlama"` e troque por `name = "aby"`).

- [ ] **Step 6: Nenhuma referência ao pacote antigo sobrou**

Run: `grep -rn "com\.github\.tjake\|com/github/tjake" . --exclude-dir=.git --exclude-dir=target | grep -v "docs/superpowers"`
Expected: sem saída.

- [ ] **Step 7: Build e testes**

Run: `JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B clean verify 2>&1 | grep -E "Tests run:|BUILD|ERROR" | tail -15`
Expected: `BUILD SUCCESS`. Se o `jlama-core` falhar por `module-info`/recurso com o nome antigo, rode `grep -rn "jlama" --include=*.json --include=*.properties --include=*.xml --exclude-dir=target .` e corrija o nome do pacote.

- [ ] **Step 8: Comparação de saída (prova que o nativo e o motor sobreviveram)**

Não grave novas referências: o teste compara com as gravadas antes do rename (Task 3).

Run: `SMOKE_MODEL=1 JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B -pl aby-inference-tests -am test -Dtest=SmokeTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run|BUILD"`
Expected: `Tests run: 1, Failures: 0` e `BUILD SUCCESS`, ou seja, o texto gerado é idêntico ao de antes do rename. Se falhar com diferença de texto, o rename quebrou algo (provavelmente o caminho nativo); não regrave a referência.

Em seguida confirme que o caminho nativo carrega: `unzip -l aby-inference-native/target/aby-inference-native-*-linux-x86_64.jar | grep -E "META-INF/versions/25/ao/bitubi"` deve listar classes `cnative` (Review Focus 3).

- [ ] **Step 9: Comparação de desempenho**

Run: `docs/benchmarks/run-perf.sh /home/dumi/.sdkman/candidates/java/25.0.3-amzn | tee /tmp/perf-after.txt`
Expected: `prefill_tok_s` e `decode_tok_s` dentro da faixa mín.-máx. registrada em `docs/benchmarks/baseline-<hardware>.md` (Task 3b). Se ficar abaixo da faixa, rode mais duas vezes para descartar ruído; se persistir, o rename mudou o caminho de execução (por exemplo o nativo deixou de ser usado) e deve ser investigado antes do commit. Acrescente os números "depois" ao mesmo arquivo de baseline.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: renomeia para aby-inference (pacote, groupId, artefatos, CLI)
```

---

### Task 5: Atribuição e identidade (NOTICE, README, metadados)

**Files:**
- Create: `NOTICE`
- Modify: `README.md`, `pom.xml` (url, scm, developers, description), `conf/license-header`, `Dockerfile` (label `image.source`), `docker-compose.yaml`, `kubernetes/helm/jlama/values.yaml`, `kubernetes/gke_values.yaml`

**Interfaces:**
- Consumes: pacote e artefatos da Task 4.
- Produces: atribuição conforme a Apache 2.0.

- [ ] **Step 1: Criar `NOTICE`**

```text
aby-inference
Copyright 2026 Bitubi Solutions

Este produto inclui software derivado do Jlama
(https://github.com/tjake/jlama), Copyright 2024 T Jake Luciani e
colaboradores, licenciado sob a Apache License, Version 2.0.

Os arquivos originais mantêm seus cabeçalhos de copyright. Arquivos
modificados por Bitubi Solutions são marcados com um aviso de alteração.
```

- [ ] **Step 2: Atualizar o cabeçalho de licença novo**

Em `conf/license-header`, mantenha o cabeçalho existente (autor original) e acrescente ao final do bloco, antes de ` */`:

```text
 *
 * Modifications Copyright 2026 Bitubi Solutions
```

Atenção: o Spotless aplica este cabeçalho a todo `.java`. Por isso, não rode `spotless:apply` neste momento (reescreveria 150+ arquivos). Anote a decisão no commit.

- [ ] **Step 3: Metadados do `pom.xml`**

Troque, no `pom.xml` raiz: `<name>Jlama Parent</name>` por `<name>aby-inference Parent</name>`, `<url>` por `https://github.com/bitubi-solutions/aby-inference`, `scm` (`connection`, `developerConnection`, `url`) e `issueManagement` para `bitubi-solutions/aby-inference`, `<description>` por `aby-inference: motor de inferência de LLMs em Java (derivado do Jlama)`. Em `<developers>`, mantenha Jake Luciani e adicione um `<developer>` com `<organization>Bitubi Solutions</organization>`.

- [ ] **Step 4: README**

Reescreva o título e a introdução para `aby-inference` e acrescente, logo após os badges, uma seção:

```markdown
## Origem

aby-inference é um fork mantido do [Jlama](https://github.com/tjake/jlama)
(© T Jake Luciani e colaboradores, Apache 2.0). Veja o arquivo `NOTICE`.
```

Troque os comandos `jlama ...` por `aby ...`, o `groupId` nos exemplos Maven por `ao.bitubi.aby-inference` e os `artifactId` por `aby-inference-*`. Mantenha os ids de modelo `tjake/...` (são nomes do Hugging Face) e o link para `hf.co/tjake`. Remova a instalação por `jbang app install jlama@tjake` e deixe `jbang` como "em breve".

- [ ] **Step 5: Docker e Kubernetes**

Em `Dockerfile`, troque o `LABEL org.opencontainers.image.source` para `https://github.com/bitubi-solutions/aby-inference`. Em `kubernetes/helm/jlama/values.yaml` e `kubernetes/gke_values.yaml`, troque `repository: docker.io/tjake/jlama` por `ghcr.io/bitubi-solutions/aby-inference`. Não toque nos ids de modelo.

- [ ] **Step 6: Verificar o critério do spec**

Run: `grep -rniI "tjake" . --exclude-dir=.git --exclude-dir=target --exclude-dir=superpowers | grep -v "hf.co/tjake\|tjake/[A-Za-z0-9._-]*\(JQ4\|Jlama\|jlama\|Instruct\|gemma\|Q4\)\|sdkman-for-toolchains\|tjake/jlama"`
Expected: somente linhas legítimas (`NOTICE`, README "Origem", cabeçalhos de copyright com "T Jake Luciani", citação BibTeX). Qualquer outra ocorrência deve ser corrigida.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "docs: adiciona NOTICE e atribuição ao Jlama original
```

---

### Task 6: CI no GitHub Actions (JDK 25 em Linux, Windows e macOS)

**Files:**
- Modify: `.github/workflows/unit-tests.yaml`
- Modify: `docs/benchmarks/run-perf.sh` (hardware por SO)

**Interfaces:**
- Consumes: build verde (Task 2), `SmokeTest` (Task 3) e `PerfBaselineTest` (Task 3b).
- Produces: workflow `Unit Test CI` rodando em `ubuntu-latest`, `windows-latest`, `macos-latest` (ARM) e um runner macOS Intel, todos com JDK 25; resumo de desempenho por SO.

- [ ] **Step 1: Tornar `run-perf.sh` portável (Linux, macOS e Windows/Git Bash)**

Substitua o bloco `== hardware ==` do script por:

```bash
echo "== hardware =="
case "$(uname -s)" in
  Linux)
    lscpu | grep -E "Model name|^CPU\(s\)"
    grep -o -w -E "avx512[a-z_]*|avx2|avx|fma" /proc/cpuinfo | sort -u | tr '\n' ' '; echo ;;
  Darwin)
    sysctl -n machdep.cpu.brand_string
    echo "CPUs: $(sysctl -n hw.ncpu) arch: $(uname -m)" ;;
  MINGW*|MSYS*|CYGWIN*)
    echo "${PROCESSOR_IDENTIFIER:-unknown}"
    echo "CPUs: ${NUMBER_OF_PROCESSORS:-?}" ;;
esac
```

E troque a primeira linha de argumentos para aceitar `JAVA_HOME` do ambiente: `JDK_HOME="${1:-$JAVA_HOME}"`.

- [ ] **Step 2: Substituir o workflow**

```yaml
name: Unit Test CI

on:
  pull_request:
    types: [ opened, synchronize, reopened ]
  workflow_dispatch:
  push:
    branches:
      - main
    paths:
      - .github/workflows/unit-tests.yaml
      - '**.java'
      - '**/pom.xml'

jobs:
  build:
    strategy:
      fail-fast: false
      matrix:
        os: [ ubuntu-latest, windows-latest, macos-latest, macos-15-intel ]
    runs-on: ${{ matrix.os }}
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: 25
          distribution: temurin
          mvn-toolchain-vendor: Eclipse Adoptium
          cache: maven
      - name: Compile, run tests, and package (JDK 25)
        run: ./mvnw -B package
      - name: Cache small model
        uses: actions/cache@v4
        with:
          path: aby-inference-tests/models
          key: small-model-qwen2.5-0.5b-jq4
      - name: Smoke test with a small model
        env:
          SMOKE_MODEL: "1"
          SMOKE_RELAXED: "1"
        run: ./mvnw -B -pl aby-inference-tests -am test -Dtest=SmokeTest -Dsurefire.failIfNoSpecifiedTests=false
      - name: Performance snapshot (informational)
        run: |
          docs/benchmarks/run-perf.sh "$JAVA_HOME" | tee perf.txt
          { echo '### Desempenho em ${{ matrix.os }}'; echo '```'; cat perf.txt; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
```

Notas:
- `SMOKE_RELAXED=1` na CI: as referências de texto foram gravadas na sua máquina e o ponto flutuante pode diferir em outra CPU ou SO, então a CI só exige texto não vazio. A comparação estrita roda localmente (Tasks 3 e 4).
- O passo de desempenho é informativo (não falha o job): serve para coletar o baseline do Windows e do macOS. Depois da primeira execução verde, copie as linhas `PERF` de cada SO do resumo do job para `docs/benchmarks/baseline-ci-<os>.md`.
- `macos-latest` é ARM (arm64); `macos-15-intel` é x86_64. **Confirme em https://docs.github.com/actions/using-github-hosted-runners que o rótulo `macos-15-intel` existe**; se não existir, remova-o e registre que macOS Intel não é testado.
- Se o `aby-inference-native` não compilar em Windows ou em macOS ARM (o `Makefile` usa `gcc`/`make`/`ld`), a falha deve aparecer aqui. Não a esconda com `continue-on-error`: abra uma task própria para o suporte nativo naquele SO.

- [ ] **Step 3: Validar a sintaxe**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/unit-tests.yaml')); print('ok')"`
Expected: `ok`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/unit-tests.yaml docs/benchmarks/run-perf.sh
git commit -m "ci: JDK 25 em Linux, Windows e macOS, com teste de fumaça e desempenho
```

---

### Task 7: Publicação no GitHub Packages

**Files:**
- Modify: `pom.xml` (perfil `release`: `distributionManagement`, plugins de publicação)
- Modify: `.github/workflows/release.yaml`

**Interfaces:**
- Consumes: groupId `ao.bitubi.aby-inference` e as URLs `scm` da Task 5.
- Produces: perfil `release` que publica em `https://maven.pkg.github.com/bitubi-solutions/aby-inference`; workflow `Release` manual.

Decisão do autor: o release no Maven Central fica para depois (exige verificar o domínio `bitubi.ao`, chave GPG e o Central Portal). Por enquanto publica-se no GitHub Packages, que não exige verificação de domínio nem GPG. Limitação a registrar no README: o GitHub Packages exige autenticação (token com `read:packages`) até para ler pacotes públicos, então não serve como distribuição aberta ao público.

- [ ] **Step 1: Trocar o destino de publicação**

No perfil `release` do `pom.xml`: remova `<distributionManagement>` do OSSRH, o `nexus-staging-maven-plugin` e o plugin `maven-gpg-plugin` (mantenha `maven-source-plugin` e `maven-javadoc-plugin`). Adicione:

```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/bitubi-solutions/aby-inference</url>
  </repository>
</distributionManagement>
```

- [ ] **Step 2: Reescrever `release.yaml`**

```yaml
name: Release
on: workflow_dispatch

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 25
          distribution: temurin
          mvn-toolchain-vendor: Eclipse Adoptium
          cache: maven
          server-id: github
          server-username: GITHUB_ACTOR
          server-password: GITHUB_TOKEN
      - name: Publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./mvnw -B -DskipTests -Prelease deploy
```

Nota sobre as bibliotecas nativas: o job antigo publicava o classificador de cada SO (macOS, Windows) no mesmo repositório de staging. Aqui não há staging: o job acima publica só o classificador do Linux (`linux-x86_64`). Para os demais SOs, adicione depois jobs por SO que rodam `./mvnw -B -Prelease -am -pl aby-inference-native deploy`; antes de fazê-lo, verifique se o GitHub Packages aceita republicar o mesmo POM e JAR principal por outro job (pode responder 409). Registre no README que, no `0.1.0`, só o Linux x86_64 é publicado.

- [ ] **Step 3: Dry-run local (sem enviar nada)**

Run: `JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B -DskipTests -Prelease package 2>&1 | grep -E "BUILD|ERROR" | tail -5`
Expected: `BUILD SUCCESS` (gera sources e javadoc). Não rode `deploy` localmente.

- [ ] **Step 4: Commit**

```bash
git add pom.xml .github/workflows/release.yaml
git commit -m "build: publica no GitHub Packages
```

---

### Task 8: Governança mínima e migração para `bitubi-solutions`

**Files:**
- Create: `CONTRIBUTING.md`, `.github/ISSUE_TEMPLATE/bug_report.md`, `.github/ISSUE_TEMPLATE/feature_request.md`, `.github/pull_request_template.md`

**Interfaces:**
- Consumes: todas as tasks anteriores.

- [ ] **Step 1: `CONTRIBUTING.md` com DCO**

```markdown
# Contribuindo

1. Faça um fork e crie uma branch.
2. Rode `./mvnw verify` com JDK 25.
3. Assine seus commits com `git commit -s` (Developer Certificate of Origin,
   https://developercertificate.org/). PRs sem `Signed-off-by` não serão aceitos.
4. Abra o PR descrevendo o que mudou e como testou.

Ao contribuir você declara ter o direito de licenciar sua contribuição sob a
Apache License 2.0.
```

- [ ] **Step 2: Templates**

`.github/ISSUE_TEMPLATE/bug_report.md` com campos: versão do `aby`, JDK (`java -version`), SO e CPU, modelo, comando executado, saída. `feature_request.md` com: problema, proposta, alternativas. `.github/pull_request_template.md` com: resumo, como testar, checklist (`verify` verde, commits assinados).

- [ ] **Step 3: Verificar tudo antes da migração**

Run: `JAVA_HOME=/home/dumi/.sdkman/candidates/java/25.0.3-amzn ./mvnw -B clean verify 2>&1 | grep -E "BUILD"`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -s -m "docs: adiciona CONTRIBUTING com DCO e templates
```

- [ ] **Step 5: Migrar o repositório (ação do dono; confirme antes)**

Esta etapa é visível externamente; só execute com autorização explícita do autor. Duas opções:
(a) Transferir `DumiJDev/Jlama` para a organização na interface do GitHub (Settings, Transfer) e renomear para `aby-inference`;
(b) criar `bitubi-solutions/aby-inference` vazio e empurrar o histórico.

Para (b):

```bash
git remote rename origin old-origin
git remote add origin git@github.com:bitubi-solutions/aby-inference.git
git remote add upstream https://github.com/tjake/jlama.git
git push -u origin foundation/aby-rename
```

Depois abra o PR para `main` na organização e verifique que a CI fica verde nos três SOs.

- [ ] **Step 6: Primeira publicação no GitHub Packages (após o Step 5; confirme com o autor)**

Troque `<revision>` para `0.1.0`, faça commit, crie a tag `v0.1.0` e execute o workflow `Release`. Confira em `https://github.com/orgs/bitubi-solutions/packages` que os artefatos `aby-inference-*` aparecem. Volte `<revision>` para `0.1.1-SNAPSHOT`. O release no Maven Central fica fora deste plano.

---

## Self-Review

- **Spec coverage:** passo 1 do spec (fim de linha) = Task 1; passo 2 (migrar) = Task 8 Step 5; passo 3 (rename) = Task 4; passo 4 (atribuição) = Task 5; passo 5 (build e CI) = Tasks 2 e 6; passo 6 (publicação) = Tasks 7 e 8 Step 6; passo 7 (governança) = Task 8. Critério de pronto: `verify` verde em JDK 25 (Tasks 2, 4, 8), `grep tjake` (Task 5 Step 6), artefato no GitHub Packages (Task 8 Step 6), comparação de saída (Tasks 3 e 4), comparação de desempenho com o baseline (Tasks 3b e 4), CI verde em Linux, Windows e macOS (Task 6).
- **Divergência do spec:** o critério "`grep -ri tjake` não retorna nada" foi relaxado para permitir ids de modelo do Hugging Face e o clone `sdkman-for-toolchains` (nomes externos). O spec deve ser atualizado com esta ressalva.
- **Pontos que não pude verificar sem executar:** se o `jlama-native` compila em Windows e em macOS (ARM e Intel; o `Makefile` usa `gcc`, `make` e `ld`); se o rótulo de runner `macos-15-intel` existe; a existência do modelo do teste de fumaça no Hugging Face. Cada um tem um passo de verificação e fallback nas tasks acima.
