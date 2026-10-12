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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ao.bitubi.inference4j.model.functions.Generator;
import ao.bitubi.inference4j.safetensors.DType;
import ao.bitubi.inference4j.safetensors.SafeTensorSupport;
import ao.bitubi.inference4j.safetensors.prompt.PromptContext;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

/**
 * Generates a few tokens with a small model at temperature 0 and compares them with a reference
 * recorded on the author's machine (SMOKE_RECORD=1 records it). Opt-in with SMOKE_MODEL=1.
 * SMOKE_RELAXED=1 only requires non-empty output (for CI on other hardware).
 */
public class SmokeTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources");

    @Test
    public void generatesSameTokensAsReference() throws Exception {
        Assume.assumeTrue("SMOKE_MODEL=1 required", "1".equals(System.getenv("SMOKE_MODEL")));

        File dir = SafeTensorSupport.maybeDownloadModel("./models", "tjake/Qwen2.5-0.5B-Instruct-JQ4");
        AbstractModel m = ModelSupport.loadModel(dir, DType.F32, DType.I8);

        PromptContext ctx = m.promptSupport().get().builder().addUserMessage("What is the capital of France?").build();

        Generator.Response r = m.generate(UUID.randomUUID(), ctx, 0.0f, 96, (s, f) -> {});
        assertFalse("no tokens generated", r.responseText.isBlank());

        if ("1".equals(System.getenv("SMOKE_RELAXED"))) {
            return;
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
