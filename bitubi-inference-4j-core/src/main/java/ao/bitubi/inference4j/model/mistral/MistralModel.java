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
package ao.bitubi.inference4j.model.mistral;

import ao.bitubi.inference4j.model.ModelSupport;
import ao.bitubi.inference4j.model.llama.LlamaModel;
import ao.bitubi.inference4j.safetensors.Config;
import ao.bitubi.inference4j.safetensors.DType;
import ao.bitubi.inference4j.safetensors.WeightLoader;
import ao.bitubi.inference4j.safetensors.tokenizer.Tokenizer;
import java.util.Optional;

public class MistralModel extends LlamaModel {

    public MistralModel(
        Config config,
        WeightLoader weights,
        Tokenizer tokenizer,
        DType workingDType,
        DType workingQType,
        Optional<DType> modelQType
    ) {
        super(config, weights, tokenizer, workingDType, workingQType, modelQType);
    }

    public MistralModel(
        InferenceType inferenceType,
        Config config,
        WeightLoader weights,
        Tokenizer tokenizer,
        DType workingDType,
        DType workingQType,
        Optional<DType> modelQType
    ) {
        super(inferenceType, config, weights, tokenizer, workingDType, workingQType, modelQType);
    }

    @Override
    public ModelSupport.ModelType getModelType() {
        return ModelSupport.ModelType.MISTRAL;
    }
}
