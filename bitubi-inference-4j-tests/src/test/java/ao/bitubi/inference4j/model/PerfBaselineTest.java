/*
 * Copyright 2024 T Jake Luciani
 *
 * The Jlama Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package ao.bitubi.inference4j.model;

import ao.bitubi.inference4j.model.functions.Generator;
import ao.bitubi.inference4j.safetensors.DType;
import ao.bitubi.inference4j.safetensors.SafeTensorSupport;
import ao.bitubi.inference4j.safetensors.prompt.PromptContext;
import java.io.File;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

/** Prints tokens/s for a small model. Informational only; opt-in with PERF_BASELINE=1. */
public class PerfBaselineTest {

    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final int MAX_TOKENS = 256;

    @Test
    public void measureTokensPerSecond() throws Exception {
        Assume.assumeTrue("PERF_BASELINE=1 required", "1".equals(System.getenv("PERF_BASELINE")));

        File dir = SafeTensorSupport.maybeDownloadModel("./models", "tjake/Qwen2.5-0.5B-Instruct-JQ4");

        long t0 = System.nanoTime();
        AbstractModel m = ModelSupport.loadModel(dir, DType.F32, DType.I8);
        long loadMs = (System.nanoTime() - t0) / 1_000_000;

        PromptContext ctx = m.promptSupport()
            .get()
            .builder()
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
            Runtime.version().feature(),
            loadMs,
            prefillSum / MEASURED_RUNS,
            decodeSum / MEASURED_RUNS,
            usedMb
        );
    }
}
