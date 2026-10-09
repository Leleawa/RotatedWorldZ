# RotatedWorldZ

Versão em 20 linguagens do RotatedWorld (um plugin Paper/Folia que gira todo o mundo em 90° ao redor do eixo X para os jogadores usando PacketEvents):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** compilados juntos,
mais 8 camadas de script carregadas em tempo de execução: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, tudo empacotado em **um único jar**.


## Quem é responsável pelo quê

| Camada | Linguagem | Responsabilidade | Por que esta linguagem? |
|---|---|---|---|
| 0 api | Java | Contratos a serem implementados pela camada de computação pura (apenas tipos do JDK) | O menor denominador comum compreendido por todos os compiladores |
| 1 computação pura | **Frege** | Álgebra de coordenadas: definição canônica da rotação, mais 10 propriedades QuickCheck | Funções puras + QuickCheck integrado; compila em métodos `static int/float/double`, sem boxing |
| | **Flix** | Regras de nomenclatura de blocos (chão/em pé ↔ parede, tipos de tile entities); reciclagem de cache | Tabelas de regras + sistema de efeitos garantem pureza; "quais chunks ainda são necessários" é uma regra em **Datalog** |
| | **Clojure** | Análise de configuração: opções de configuração escritas como dados de schema | Orientado a dados: adicionar uma opção de configuração = adicionar uma linha de dados |
| | **Haxe** | Rotação do array de nibbles de iluminação | Usa apenas `haxe.io.Bytes`, o mesmo código compila para JS/C++ (ex.: visualizador offline) |
| | **Fantom** | Estratégia de busca por pontos de apoio seguros | Algoritmo puro, enxerga o mundo via interface `BlockProbe`, sem tocar no Bukkit |
| 2 kernel | Java | Estado do jogador `PlayerState`, `SerialExecutor`, interfaces `Host` / `BlockRotator` | Estado compartilhado concorrente com `volatile` / variáveis atômicas, mais direto de escrever em Java |
| 3 motor | **Scala 3** | Cache de chunks `ChunkStore` + motor de visão `ViewManager` (origem flutuante, prefetch, envio de chunks) | Núcleo de desempenho, muitos loops de array |
| | **Gosu** | `WorldProbe`: verificação de blocos / caixas de colisão do Bukkit; enhancement de `Material` | Enhancements adicionam propriedades aos tipos do Bukkit (`type.Hazard`) |
| | **Groovy** | Orquestração do fluxo do comando `/rotate` | Caminho frio (cold path) |
| | **Kawa Scheme** | Limpeza de cache: coleta o raio de visão de cada mundo e entrega ao Datalog do Flix para decidir o que manter (originalmente no `ViewManager` do Scala) | Compilação AOT (ahead-of-time), processamento de listas |
| | **Xtend** | `BlockRotation`: regras de rotação de BlockData | Cast automático com `instanceof` + sintaxe de propriedades; as regras leem-se como a própria especificação |
| 4 plugin | **Kotlin** | Reescrita de pacotes (hot path do Netty), eventos Bukkit, fachada de vetores do PacketEvents | Código original; a sintaxe do Kotlin é a mais fluida com PacketEvents |
| | Java | Ponto de entrada do plugin (implementa `Host`), pontes de inicialização do Flix/Fantom/Clojure, carregador da camada de scripts | Ponto de entrada e montagem |
| 5 scripts | **Kotlin Script** | Decisão de recentralização da origem flutuante (originalmente no `ViewManager` do Scala) | Compilado para bytecode em tempo de execução, rápido o suficiente para invocar a cada tick |
| | **JRuby** | Ordem de envio das colunas de chunks (originalmente no `ViewManager` do Scala) | Uma única cadeia de `product.each_with_index.sort_by` |
| | **JavaScript** | Liberação de rubberband para "movimento rápido demais" (originalmente no listener Kotlin) | Interpretado via Rhino, eventos raros |
| | **Jython** | Repulsão (knockback) (originalmente no listener Kotlin) | Executado uma vez a cada dano recebido |
| | **Lua** | Dano de queda (originalmente no listener Kotlin) | Jurado principal |
| | **BeanShell** | A linha de texto do `/rotate status` (originalmente no Groovy) | Scripts com sintaxe Java |
| | **Common Lisp** | Quais verificações do servidor liberar para jogadores rotacionados (movimentos com falha, expulsão por voo) (originalmente no listener Kotlin) | Uma tabela de decisão `cond` |
| | **Prolog** | Gramática de argumentos do `/rotate`, escrita como DCG (originalmente no Groovy) | Gramáticas devem ser escritas em DCG |
| | **Todos os oito + Kawa** | **Júri do dano de queda**: nove linguagens calculam uma vez cada, a maioria vence, empate desempatado por Lua, aviso registrado em caso de divergência | Entretenimento |

**A Camada 1 não tem visibilidade em tempo de compilação do Bukkit nem do PacketEvents** (elas recebem apenas a `api`), e as cinco linguagens não enxergam umas às outras. Chamadas para baixo são invocações normais da JVM; chamadas para cima só ocorrem através de interfaces: Flix e Fantom delegam implementações para `api.Services`, e a classe principal do plugin fornece serviços para a Camada 3 através de `kernel.Host`.

## Camada de scripts

Os scripts não são compilados no jar, mas carregados na inicialização do plugin por interpretadores embutidos (`script.ScriptLayers`, 8 interpretadores inicializados em paralelo, cerca de 4–5 segundos):

* O código-fonte fica em `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, empacotado durante a compilação em `scripts/` dentro do jar.
* Se `plugins/RotatedWorldZ/scripts/<diretório>/rules.*` existir, ele sobrescreve a versão do jar: **não é necessário recompilar para alterar regras, basta reiniciar o servidor**.
* As interfaces implementadas pelos scripts estão em `api.ScriptedRules`. A última expressão do Kotlin Script deve ser um objeto que implemente `Recenter` e `FallJuror` simultaneamente, e não pode ser um objeto anônimo; os demais scripts definem apenas funções globais (o Prolog usa predicados, com o último argumento como resultado), encapsuladas como interfaces pelo Java.
* Nenhum interpretador embutido garante thread safety, portanto as chamadas de cada script são serializadas (`synchronized`). Por isso, apenas o Kotlin Script (executado compilado) está no hot path a cada tick, enquanto os demais estão em cold paths.
* As bibliotecas incluídas do Jython (como jnr/jffi) entram em conflito de versão com o JRuby, de modo que o build primeiro empacota o Jython isoladamente e realoca esses pacotes (tarefa `jythonIsolated`, seguindo a abordagem do template upstream).

Pontos onde a consistência multilíngue é garantida:

* O mapeamento de faces de blocos (UP→NORTH……) não é mais feito manualmente; Xtend (`BlockFace` do Bukkit) e Kotlin (`BlockFace` do PacketEvents) são derivados da mesma rotação do Frege.
* O mapeamento de coordenadas dentro da section `Geometry.localIndex` (Frege) e a rotação de iluminação do Haxe usam exatamente o mesmo mapeamento, validado bloco a bloco no `smokeTest`.

## Compilação

```bash
./gradlew build          # Artefato: build/libs/RotatedWorldZ-1.0.0.jar; inclui checkGeometry e smokeTest
./gradlew checkGeometry  # Executa apenas as propriedades QuickCheck do Frege
./gradlew smokeTest      # Não requer servidor: inicia a camada de computação pura do jar final e compara com o comportamento original em Kotlin; verifica links de todas as outras camadas
```

Em tempo de execução, o servidor precisa do PacketEvents instalado (`depend: [packetevents]`).

O primeiro build faz download de Flix, Haxe e Fantom em `.tools/` (aprox. 60 MB); o Haxe também requer um JDK 8 (usando apenas o seu `rt.jar`; se a toolchain do Gradle não encontrar, fará o download automático via foojay). Todo o restante usa JDK 21.

## Armadilhas de cada linguagem

* **Flix**: Só pode exportar uma única função de entrada, gerada na classe `Main` do pacote padrão (`FlixBridge` a invoca via reflexão). Ele também gera cerca de 500 classes e pacotes no diretório raiz (`Array`, `List`……), o que ocultaria o `Array`/`List` do Scala/Kotlin; portanto, a saída do Flix **não fica em nenhum classpath de compilação**, entrando apenas em tempo de execução e no jar. As guards do Datalog capturam no máximo 5 variáveis. Interfaces Java e módulos Flix não podem ter o mesmo nome.
* **Frege**: Os parâmetros são preguiçosos (lazy) por padrão (na assinatura Java aparecem como `Lazy<Float>`); para parâmetros estritos de tipos primitivos, adicione `!` no pattern.
* **Fantom**: Implementar interfaces Java com métodos `default` causa crash; portanto, as interfaces em `api` não têm métodos `default`. `long`/`double`/`boolean` do Java correspondem diretamente a `Int`/`Float`/`Bool` do Fantom; tipos de referência são todos anuláveis (nullable). Literais de ponto flutuante devem ser escritos como `0.5f` (`0.5` é Decimal).
* **Gosu**: `block` é uma palavra-chave (as lambdas do Gosu). O gosuc resolve imediatamente tipos de anotação, portanto o classpath de compilação precisa das dependências da Paper API `org.jetbrains:annotations` e `jspecify`.
* **Xtend**: `Keyed` do Bukkit possui tanto `key()` quanto `getKey()`; `b.key` é ambíguo, devendo ser escrito como `b.getKey`. Quando campos e métodos têm o mesmo nome, o identificador dentro do corpo do método refere-se ao método.
* **Haxe**: Alvo `--jvm`; o carregador de classes externas do Java só reconhece o `rt.jar` do JDK 8; o pacote oficial para Windows do `haxelib` depende do Neko, então um stub é colocado no PATH durante o build.
* **Clojure**: Depende do class loader de contexto da thread para encontrar namespaces; `ClojureBridge` aponta temporariamente para o class loader do plugin durante inicialização e chamadas.
* **Kotlin Script**: O resultado do script não pode ser um objeto anônimo (`object : X {}` resulta no erro "anonymous type"); é necessário declarar uma classe nomeada antes.
* **Kawa**: O `null` do Java é avaliado como verdadeiro em Scheme (apenas `#f` é falso); verificações devem usar `(eq? x #!null)`; `obj:field` lê campos e `(obj:method)` chama métodos.
* **Class loader de contexto de thread**: As threads do servidor (thread principal, threads de região, netty, agendador global) têm um class loader de contexto que não enxerga as classes do plugin, enquanto os runtimes do Kawa e Clojure usam justamente ele para buscar classes por nome. Portanto, todo acesso a esses runtimes vindo de threads do servidor é encapsulado com `kernel.PluginContext` (limpeza de cache do Kawa, júri, todas as invocações de script, busca de pontos de apoio em Gosu/Fantom, comandos Groovy). A thread principal do `smokeTest` define, por esse motivo, o class loader de contexto como o platform class loader, simulando uma thread de servidor.
* **Prolog**: tuProlog não carrega DCG por padrão; é necessário chamar `loadLibrary(new DCGLibrary())` para disponibilizar `-->` e `phrase/2`.
* **Jython**: `PyString` é uma cadeia de bytes do Python 2; quando há caracteres não ASCII no caminho, é necessário usar `Py.newStringOrUnicode`. O `smokeTest` é executado, por esse motivo, a partir de um caminho não ASCII como `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Durante a inicialização, tenta usar reflexão para abrir a factory de threads virtuais do JDK; sem `--add-opens java.base/java.lang`, emite uma linha no `System.err` (o Paper avisa "plugin usou System.err" por causa disso). Inofensivo; `StderrFilter` intercepta apenas essa linha durante a inicialização do ABCL.
* **Júri**: `ceil(2.5 - 3)` é `-0.0`; o `max` de Python / Kotlin / Scheme mantém isso inalterado. Deve ser normalizado para `0.0` antes da contagem de votos, caso contrário, quedas de 2 a 3 blocos acusariam falsamente "opiniões divergentes".
* **Gosu**: Assim que o runtime inicializa, ele abre `java.base` para reflexão, mascarando problemas de reflexão de outras bibliotecas depois disso. Por isso, o `smokeTest` segue a ordem do `onEnable` do plugin: primeiro carrega a camada de scripts, depois inicializa o Gosu. Pode-se rodar `./gradlew smokeTest -PsmokeJava=25` com o JDK usado no servidor.
* **JRuby**: `sort_by` é instável; para manter a ordem original, deve-se incluir o índice na chave de ordenação.
* **Scala 3**: Após deletar arquivos-fonte, a compilação incremental pode deixar arquivos `.tasty` antigos para trás, que acabam entrando no cache de compilação. Se encontrar classes indevidas no jar, execute `./gradlew clean build --rerun-tasks`.

## Verificado / Não verificado

Verificado (Windows 11, JDK 21, Gradle 9.7.1):

* Todas as 12 linguagens compiladas compilaram, todas as 8 camadas de script carregaram, `./gradlew clean build --rerun-tasks` passou.
* `checkGeometry`: 10 propriedades QuickCheck passaram, incluindo ida e volta (round-trip), consistência bloco/ponto, consistência de rotação de visão e rotação de vetor, `zBase` sendo divisão inteira arredondada para baixo.
* `smokeTest` (total de 123.134 verificações, executado em class loader isolado, a partir de caminho com caracteres chineses, simulando o carregamento do plugin; executado tanto no JDK 21 quanto no 25):
  * Regras em Flix, configurações em Clojure, iluminação em Haxe e busca em Fantom apresentaram resultados idênticos item por item à implementação original em Kotlin;
  * Os 8 scripts e o Kawa apresentaram resultados idênticos item por item ao código original que substituíram; o júri teve 9 votos unânimes sem registrar avisos; as camadas de script não imprimiram nada no `System.err` durante o carregamento;
  * As camadas Scala/Gosu/Groovy/Xtend/Kotlin conseguem ligar-se (link), e os runtimes de Gosu/Groovy/Scala inicializam com sucesso.

**Não verificado**:

* Não foi executado em um servidor Paper / Folia real, nem acessado com um cliente (isso exigiria aceitar o EULA do Minecraft e instalar PacketEvents). Caminhos que dependem do servidor, como reescrita de pacotes, envio de chunks e recentralização, contam apenas com verificações de compilação e a garantia de terem sido "portados linha por linha a partir do original". Teste em um servidor de teste antes de usar em produção.
* O jar tem cerca de 231 MB (o original tinha 4,9 MB): compilador Kotlin, JRuby, Jython, ABCL, mais os runtimes de Gosu, Groovy, Scala, Clojure, Frege, Kawa. A inicialização do plugin leva 4–5 segundos adicionais para carregar as camadas de script. Esses runtimes foram incluídos sem relocação (relocate): se outro plugin no mesmo servidor contiver versões diferentes de Kotlin/Scala/Groovy, podem ocorrer conflitos.

## Estrutura de diretórios

```
src/api/java        Camada 0: Contratos da computação pura (tipos do JDK)
src/kernel/java     Camada 2: Estado compartilhado e interfaces de serviço (Bukkit + PacketEvents)
src/main/<linguagem> Código-fonte de cada linguagem (camada de script em src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Tarefas Gradle de compilação para cada linguagem
build.gradle.kts    Camadas e dependências
```
