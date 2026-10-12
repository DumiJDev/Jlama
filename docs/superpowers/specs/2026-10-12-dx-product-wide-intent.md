# DX em todo o produto (registo de intenção)

Data: 2026-10-12. Estado: **intenção registada; nada desenhado nem implementado**. A revisão de cada
componente fica para quando chegarmos a ele, e é nessa altura que se "acertam as contas".

## Princípio

A experiência de quem usa o bitubi-inference-4j é um requisito do produto inteiro, não só do SDK.
Cada componente é revisto como produto: do primeiro contacto ao primeiro resultado, nos erros, nos
padrões e na documentação.

## Componentes a rever

| Componente | Quem o usa | Estado da revisão |
|---|---|---|
| SDK Java | quem integra o motor em apps | proposta inicial em `2026-10-12-sdk-dx-design.md` (aguarda aprovação) |
| API REST (OpenAI e Anthropic) | clientes HTTP e SDKs de terceiros | por rever (sub-projeto 2) |
| UI web | quem conversa com o modelo no navegador | por rever (sub-projeto 2, htmx + JTE) |
| CLI `bi4j` | operadores e desenvolvedores | por rever |
| Harness de agentes (`bi4j`) | quem usa o agente no terminal | por rever (último sub-projeto) |
| Deploy (Docker, Helm, cluster) | operadores | por rever (sub-projeto 3) |
| Documentação, exemplos e mensagens de erro | todos | por rever, transversal |
| Instalação e build | quem compila ou publica | por rever |

## Modelo de revisão (a aplicar a cada componente, quando chegar a vez)

1. **Primeiro sucesso:** tempo e passos do zero ao primeiro resultado útil, medidos num ambiente limpo.
2. **Padrões:** o que funciona sem configuração; o que é obrigatório configurar e se isso é claro.
3. **Erros:** cada falha comum diz a causa e a correção; testada.
4. **Consistência:** nomes, flags, formatos e mensagens seguem o mesmo vocabulário (`bi4j`, `bitubi-inference-4j`).
5. **Documentação e exemplos:** executáveis e verificados na CI.
6. **Acessibilidade e plataformas:** UI acessível; comportamento igual em Linux, Windows e macOS.

## Fricções já observadas (só as verificadas)

- Texto de ajuda da CLI e títulos ainda em inglês misturado com o idioma da documentação; decidir a política de idioma.
- `--add-modules jdk.incubator.vector` é obrigatório e hoje falha sem orientação.
- `generate(..., ntokens, ...)` é limite total de tokens e produz `Prompt exceeds max tokens` com valores baixos.
- O README e os exemplos antigos citavam APIs que já não existiam (`Downloader`); a documentação precisa de teste automático.

Tudo o mais está por observar; não presumir problemas sem medir.
