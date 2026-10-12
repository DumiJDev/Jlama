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
package ao.bitubi.inference4j.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class Bi4jPropertiesTest {

    @After
    public void clean() {
        System.clearProperty("bi4j.test_flag");
        System.clearProperty("jlama.test_flag");
        System.clearProperty("bi4j.test_int");
        System.clearProperty("jlama.test_int");
    }

    @Test
    public void booleanDefaultsToFalse() {
        assertFalse(Bi4jProperties.getBoolean("test_flag"));
    }

    @Test
    public void booleanReadsNewPrefix() {
        System.setProperty("bi4j.test_flag", "true");
        assertTrue(Bi4jProperties.getBoolean("test_flag"));
    }

    @Test
    public void booleanStillAcceptsLegacyPrefix() {
        System.setProperty("jlama.test_flag", "true");
        assertTrue(Bi4jProperties.getBoolean("test_flag"));
    }

    @Test
    public void integerDefaultsThenLegacyThenNewPrefixWins() {
        assertEquals(Integer.valueOf(7), Bi4jProperties.getInteger("test_int", 7));
        System.setProperty("jlama.test_int", "11");
        assertEquals(Integer.valueOf(11), Bi4jProperties.getInteger("test_int", 7));
        System.setProperty("bi4j.test_int", "13");
        assertEquals(Integer.valueOf(13), Bi4jProperties.getInteger("test_int", 7));
    }
}
