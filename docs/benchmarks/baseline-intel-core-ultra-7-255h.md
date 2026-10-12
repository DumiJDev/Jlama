# Baseline de desempenho: Intel Core Ultra 7 255H (WSL2)

Medido antes do rename, no commit `2284237`, com `docs/benchmarks/run-perf.sh` (3 execuções).

- Hardware: Intel(R) Core(TM) Ultra 7 255H, 16 CPUs, flags `avx avx2 fma` (sem AVX-512), WSL2 sobre Windows
- JDK: 25.0.3 (Corretto), tensor operations: Panama (Vector API), padrão
- Modelo: `tjake/Qwen2.5-0.5B-Instruct-JQ4` (F32 de trabalho, I8), 5 execuções medidas após 2 de aquecimento por rodada

| Execução | load_ms | prefill tok/s | decode tok/s | heap MB |
|---|---|---|---|---|
| 1 | 4602 | 17.35 | 6.27 | 283 |
| 2 | 6666 | 7.84 | 4.19 | 190 |
| 3 | 4684 | 15.67 | 5.51 | 417 |

**Faixa (tolerância para "sem regressão"):** prefill 7.84 a 17.35 tok/s; decode 4.19 a 6.27 tok/s; carga 4.6 a 6.7 s.

A variação entre execuções é grande (até 2x no prefill), provavelmente por núcleos híbridos P/E e WSL2.
Uma mudança só conta como regressão se ficar abaixo do mínimo da faixa em repetições seguidas; para
comparar ganhos de otimização, rode mais de 3 vezes e fixe a afinidade de CPU.

Saída de referência (temperatura 0): `bitubi-inference-4j-tests/src/test/resources/smoke-golden-25.txt`.
