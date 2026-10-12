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
 *
 * Modifications Copyright 2026 Bitubi Solutions
 */
package ao.bitubi.inference4j.tensor.operations;

import ao.bitubi.inference4j.tensor.FloatBufferTensor;
import ao.bitubi.inference4j.tensor.Q4ByteBufferTensor;
import ao.bitubi.inference4j.util.MachineSpec;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

/**
 * F32 x Q4 must work with 128-bit vectors (Apple Silicon / ARM NEON). 128-bit vectors also run on x86,
 * so the 128-bit kernel is checked here against the 256-bit one on any machine.
 */
public class TestF32Q4Arm128 {

    private static final int BATCH = 4;
    private static final int ROWS = 16;
    private static final int SIZE = 512; // multiple of the Q4 block size (32)

    @Test
    public void f32xq4Arm128MatchesAvx256() {
        Random r = new Random(42);
        FloatBufferTensor a = new FloatBufferTensor(BATCH, SIZE);
        FloatBufferTensor w = new FloatBufferTensor(ROWS, SIZE);
        for (int i = 0; i < BATCH; i++)
            for (int k = 0; k < SIZE; k++)
                a.set(r.nextFloat() * 2 - 1, i, k);
        for (int j = 0; j < ROWS; j++)
            for (int k = 0; k < SIZE; k++)
                w.set(r.nextFloat() * 2 - 1, j, k);
        Q4ByteBufferTensor q4 = new Q4ByteBufferTensor(w);

        FloatBufferTensor expected = new FloatBufferTensor(BATCH, ROWS);
        FloatBufferTensor actual = new FloatBufferTensor(BATCH, ROWS);

        new PanamaTensorOperations(MachineSpec.Type.AVX_256).batchDotProduct(expected, a, q4, 0, 0, SIZE);
        new PanamaTensorOperations(MachineSpec.Type.ARM_128).batchDotProduct(actual, a, q4, 0, 0, SIZE);

        for (int i = 0; i < BATCH; i++)
            for (int j = 0; j < ROWS; j++)
                Assert.assertEquals("[" + i + "," + j + "]", expected.get(i, j), actual.get(i, j), 1e-3f);
    }
}
