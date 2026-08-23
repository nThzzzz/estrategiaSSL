# 🎯 Estratégia SSL

Software de time da **Small Size League** (RoboCup), escrito em Java (Swing/Graphics2D).
Escuta a visão pelo protocolo oficial da liga, reconstrói o que a câmera não manda e mostra
numa janela. É o outro lado da conversa do [simuladorSSL](../simuladorSSL): lá é a fonte da
visão, aqui é quem consome e quem comanda os robôs.

## Como rodar

Com o simulador aberto em outra janela:

```bash
./tools/build.sh                    # javac puro, sem Gradle e sem rede

java -cp "out/production/estrategiaSSL:lib/*" Main             # joga de azul
java -cp "out/production/estrategiaSSL:lib/*" Main --amarelo   # joga de amarelo
java -cp "out/production/estrategiaSSL:lib/*" Main --host 192.168.0.10   # simulador em outra máquina
java -cp "out/production/estrategiaSSL:lib/*" Main --ajuda

java -cp "out/production/estrategiaSSL:lib/*" teste.Autoteste            # percepção, árbitro e protocolo
java -cp "out/production/estrategiaSSL:lib/*" teste.AutotesteEstrategia  # ciclo de vida da estratégia
```

As dependências são três, todas versionadas em `lib/` junto com o Java gerado dos `.proto`
(em `src/proto/`), exatamente como no simulador — um clone limpo compila offline:
`protobuf-java`, e o `flatlaf` mais sua fonte JetBrains Mono, que são o que faz a janela ter a
mesma cara em qualquer sistema (veja [Aparência](#aparência)). As três são jar único, sem
dependência transitiva.

Os dois projetos não podem dividir o mesmo classpath. Ambos têm `model.Geometria`, `model.Cor`
e `rede.ConfigRede` com formatos diferentes, porque um descreve o mundo que simula e o outro o
mundo que observa. São dois processos, e conversam por UDP.

## O que a rede entrega

Este é o fato que organiza o projeto inteiro. `SSL_DetectionRobot` carrega exatamente isto:

```
confidence, robot_id, x, y, orientation, pixel_x, pixel_y, height
```

Não há velocidade, posse de bola, placar, nome de time nem estado de jogo. A câmera descreve o
que enxerga num instante, e o resto é trabalho de quem recebe:

| o que a interface mostra | de onde vem |
|---|---|
| posição e orientação | medido, direto da visão |
| geometria do campo | `SSL_GeometryData`, reenviado a cada 60 quadros |
| **velocidade e ω** | **inferido** a partir de quadros consecutivos |
| **bola no sensor** | **inferido** pela geometria da boca do dribbler |
| nomes dos times e placar | `Referee`, do Game Controller |
| que lado defendemos | `Referee.blue_team_on_positive_half` |
| comando de jogo e tempo | `Referee`, traduzido para nós e eles |

Qualquer número novo na tela ou vem da rede, ou é inferência. E inferência precisa aparecer
como tal.

## Arquitetura

Dependências em uma direção, sem ciclos, como no simulador:

```
core       →  (nada)              Vec2, Angulo, Caixa, Geo
ajuste     →  (nada)              Parametro, Json
model      →  core, proto         Geometria, Cor, Robo, Comando
mundo      →  core, model         Quadro, EstadoRobo, EstadoBola
jogo       →  core, model, proto  Arbitro, EstadoDeJogo, Acao, ArbitroLocal
percepcao  →  + proto             Rastreador, Trilha, FiltroKalman1D, SensorDeBola, Ajuste
estrategia →  mundo, jogo, model  Ambiente, Jogador, Executor, Diario
  ids      →  os números estáveis       Jogada, Papel, Tatica, Habilidade
  esqueleto→  o que se estende          Coach, Play, Role, Tactic, Skill
  skills   →  as micro habilidades      SkillAndarPara, SkillOlharPara, SkillChutar
  tactics  →  conjuntos de skills       TacticBuscarBola, TacticConduzirAoGol, ...
  roles    →  papéis de um robô         RoleAtacante, RoleGoleiro
  plays    →  jogadas do time           PlayTesteAtacante
  coaches  →  quem escolhe a play       CoachBancada
rede       →  + percepcao, jogo   ConfigRede, ReceptorDeVisao, ReceptorDeArbitro, EmissorDeComandos
view       →  core, model, mundo  Campo, Paleta
app        →  tudo                Cliente, Janela, BarraSuperior, PainelRede, PainelRobos
```

Note que `estrategia` não depende de `rede` nem de `view`. Quem decide o que os robôs
fazem só conhece `Ambiente` e `Jogador`, e é isso que permite rodar mil partidas de treino
sem abrir janela nem tocar em socket.

As implementações levam o **tipo no nome**: `SkillAndarPara`, `TacticBuscarBola`,
`RoleAtacante`, `PlayTesteAtacante`, `CoachBancada`. É verboso de propósito — numa lista de
arquivos ou numa pilha de exceção, `AndarPara` sozinho não diz se é a skill que move o robô ou a
tática que a usa, e as duas existem. O prefixo responde antes de abrir o arquivo.

Dentro de `estrategia` as pastas separam **coisas de naturezas diferentes**, que antes moravam
juntas: `ids` são só números estáveis e não dependem de nada; `esqueleto` são as cinco classes
abstratas que se estende para escrever uma jogada; as cinco pastas seguintes são as
implementações concretas; e na raiz ficam o vocabulário que todas as camadas leem (`Ambiente`,
`Jogador`) e o que roda tudo (`Executor`, `Diario`). Achar onde uma classe mora deixou de
depender de lembrar o nome dela.

Duas entradas e uma saída:

| | endereço | mensagem |
|---|---|---|
| visão (entrada) | multicast `224.5.23.2:10006` | `SSL_WrapperPacket` |
| árbitro (entrada) | multicast `224.5.23.1:10003` | `Referee` |
| comandos (saída) | `127.0.0.1:10301` azul, `:10302` amarelo | `RobotControl` |

As duas entradas vêm de programas diferentes, a visão da `ssl-vision` (ou do simulador no
lugar dela) e o árbitro do `ssl-game-controller`. Nada obriga os dois a estarem no ar juntos, e
a janela continua útil com só um deles.

O ingresso no grupo multicast é tentado em **todas** as interfaces que suportam multicast, e
não só na padrão. Numa máquina com Wi-Fi, Ethernet e as interfaces virtuais que Docker e VPN
criam, a rota padrão quase nunca é a que recebe o tráfego. Entrar só nela é o motivo clássico
de "o simulador publica e o cliente não recebe nada".

A conversão de unidade acontece só na borda, em `EmissorDeComandos`. Dentro do programa tudo é
mm e radianos, como a visão entrega; o `ssl_simulation_protocol` fala metros e graus. Errar
essa conversão dá mil vezes de diferença sem estourar exceção em lugar nenhum.

## Árbitro

O árbitro é um **terceiro programa**, o `ssl-game-controller` oficial. Ele não vive nem no
simulador nem aqui:

```
        ssl-game-controller
               │  Referee, multicast 224.5.23.1:10003
               ▼
        estrategiaSSL  ◄── visão ──  simuladorSSL
               │                          ▲
               └────── RobotControl ──────┘
```

O simulador não entra nesse fluxo porque não tem lógica de jogo nenhuma: os doze robôs ficam
parados até alguém mandar comando. Não existe "o time do simulador" para o árbitro comandar.

### Uma mensagem para todos, lida do nosso ponto de vista

O `Referee` é uma transmissão única, e não uma mensagem endereçada a cada time. O que ela traz
são comandos que nomeiam uma cor: `PREPARE_KICKOFF_BLUE`, `DIRECT_FREE_YELLOW`,
`BALL_PLACEMENT_BLUE`. Quem recebe é que traduz.

É isso que o pacote `jogo` faz. A cor desaparece na entrada e o que sai é `Acao` mais um
booleano `nosso`. Sem essa tradução, cada pedaço da estratégia teria de lembrar de que cor
jogamos hoje, e trocar de lado viraria uma caçada a comparações espalhadas pelo código.

A tradução acontece na **leitura**, e não na chegada: guardamos a mensagem crua e a
interpretamos quando alguém pergunta. Trocar de azul para amarelo na janela vira "falta deles"
na hora, sem esperar o próximo pacote, que em `HALT` pode demorar bastante.

### O que o árbitro decide

| | |
|---|---|
| `PARAR` | ninguém se move |
| `AFASTAR` | 1,5 m/s de teto e 0,5 m de distância da bola |
| `JOGAR` | bola em jogo |
| `KICKOFF`, `PENALTI`, `FALTA`, `POSICIONAR_BOLA`, `TEMPO` | têm dono: a favor ou contra |

Na reposição que é nossa, quem tem de dar espaço é o adversário, então o afastamento é zero.
Esses limites são regra, não preferência da estratégia, e por isso moram em `EstadoDeJogo` e
não no planejador.

Sem nenhuma mensagem, o estado é `PARAR`. Não saber o estado do jogo não autoriza a jogar, e um
padrão otimista faria a equipe entrar em campo andando durante um `HALT` que ela ainda não
ouviu.

### Que lado é o nosso

`blue_team_on_positive_half` é o campo mais importante da mensagem inteira para quem está
começando a escrever estratégia: a visão não diz para que lado atacamos, e sem isso não dá para
decidir nada. Ele fala do azul, então vira `defendemosLadoPositivo` conforme a nossa cor.

### Árbitro de bancada

O painel da esquerda troca a fonte entre o Game Controller e um árbitro local de teste, e traz
um botão para cada comando. Ele monta uma mensagem `Referee` de verdade e a entrega pelo mesmo
caminho de tradução que a rede usa, em vez de injetar o estado já pronto: se um comando estiver
mapeado errado, erra ali igual erraria em jogo.

Trocar de fonte descarta o que a fonte anterior tinha dito, e isso é o certo — o último comando
do Game Controller não vale mais depois de passar para o árbitro local. Mas só numa troca de
verdade: reafirmar o modo que já está valendo apagava o comando corrente, e quem reencostava
nisso era a própria interface, ao alinhar o seletor com o estado real. O painel exibia "nenhum
comando ainda" logo depois de um STOP que ele mesmo tinha mandado.

As duas fontes se excluem. Em modo local, pacote da rede é ignorado, senão um Game Controller
rodando na mesma bancada sobrescreveria o comando de teste no quadro seguinte e o painel
pareceria quebrado. O painel também não publica nada na rede: um segundo publicador no
multicast oficial brigaria com o árbitro de verdade.

## Estratégia

Quatro camadas, no mesmo desenho do [SSL-Strategy](https://gitlab.com/robofei/ssl/SSL-Strategy)
da RoboFEI, inclusive na nomenclatura:

```
Coach   ->  escolhe a Play          (o time todo)
Play    ->  quem faz o quê          (o time todo)
Role    ->  o papel de um robô      (um robô)
Tactic  ->  conjunto de Skills      (um robô)
Skill   ->  uma micro habilidade    (um robô)
```

Toda Role tem Tactic, e toda Tactic é um conjunto de Skills. Se descrever a coisa exige um "e
depois", ela não é uma Skill: é uma Tactic.

### O que se implementa, e o que já vem pronto

Quem escreve uma jogada implementa `vRun()` e as condições. O ciclo em volta é `final` nas
classes base, justamente para que nenhuma implementação possa esquecer um pedaço dele:

| implementa | herda pronto |
|---|---|
| `vRun()`, o que fazer no tique | `vRunPlay`, `vRunRole`, `vRunTactic`, `vRunSkill` |
| `bCheckPreConditions()` | inicialização automática na primeira vez |
| `bCheckEndConditions()` | tempo limite e aborto |
| `dGetScore()`, `vUpdateScore()`, `vSaveScore()` | atribuição de robôs aos papéis |
| `dCusto()` de cada Role | limites do árbitro aplicados na saída |

`vRun()` roda uma vez por tique e **não bloqueia**. Uma skill de "andar até o ponto" não anda:
ela calcula a velocidade daquele tique e escreve no `Jogador`. Bloquear até chegar congelaria o
time inteiro e impediria reagir a uma bola que mudou de rumo no meio do caminho.

### O ciclo da play

Uma play sai de execução por quatro caminhos, e em qualquer um deles o coach escolhe outra:

- `bCheckEndConditions()` virou verdadeira, e ela é abortada
- o tempo limite estourou, e ela é abortada
- as roles terminaram, e ela é concluída
- alguém forçou outra play

Esse ciclo fechado é o **episódio** do aprendizado: começa em uma escolha e termina em um
resultado que dá para creditar àquela escolha. Quando a play sai de execução, o coach chama
`vUpdateScore()` uma vez, com o mundo como ele ficou, e depois `vSaveScore()`.

O tempo limite é contado no relógio da **visão**, não no de parede. Com o simulador acelerado
os dois divergem muito, e uma play de 10 segundos que virasse 10 segundos de tempo real duraria
dez vezes menos jogo: o coach treinado assim aprenderia sobre um jogo que não existe.

### Como o coach escolhe

Entre as plays cujas pré-condições valem agora, a escolha é por **roleta**: a chance de cada uma
é proporcional à sua nota. Não é pegar a melhor, de propósito. Sempre pegar a melhor nunca
experimenta as outras, e uma play com nota baixa por azar nas primeiras tentativas jamais teria
a chance de se provar.

Há um modo manual, em que a play é escolhida no `vRun()` do coach, para teste e para jogadas
ensaiadas.

### Papéis e robôs

A Role não escolhe qual robô a executa. Ela declara o que o papel faz e o `dCusto()` de cada
candidato, e a Play atribui. É o que faz a mesma play continuar valendo quando um robô leva
cartão ou quebra: uma play que fixasse "o 3 é o goleiro" pararia de funcionar no instante em que
o 3 saísse de campo.

A atribuição tem **histerese**: quem já está no papel leva 30% de vantagem. Sem isso, dois robôs
a distâncias parecidas ficam trocando de função a cada quadro e nenhum dos dois chega a lugar
nenhum.

### A prioridade de cada papel, declarada pela play

Uma play declara os papéis da jogada **ideal**, e quase nunca há robô para todos: cartão, quebra,
robô fora do alcance da visão. Sem uma classificação, quem ficasse de fora seria decidido pela
ordem em que alguém escreveu `bAddRole` — o mesmo que decidir por acaso. Toda Role entra numa de
quatro categorias:

| categoria | pode faltar? | exemplo |
|---|---|---|
| `MAXIMA` | **não** — sem ele a jogada não existe | goleiro numa defesa, batedor num pênalti |
| `MEDIA` | sim, com perda de qualidade | o corpo da jogada |
| `BAIXA` | sim | apoio |
| `OPCIONAL` | sim, sem a jogada sentir | cobertura, apoio distante |

A categoria responde "este papel pode faltar?". É isso que permite escrever uma play pensando em
seis robôs e ela rodar com **cinco**: com um robô a menos, quem fica sem função é o último
`OPCIONAL`, e não o goleiro.

Dentro da mesma categoria vale a **ordem de declaração**, porque a ordenação é estável — papéis
igualmente dispensáveis saem na ordem em que a play os escreveu, que é onde essa decisão fica
visível para quem lê a jogada.

A prioridade mora na **play**, e não na Role: quem a declara é o `bAddRole(role, prioridade)`.
O mesmo papel vale coisas diferentes em jogadas diferentes — um marcador é `MAXIMA` numa defesa e
`OPCIONAL` num ataque com o campo livre. Guardá-la dentro da Role obrigaria a escrever duas
classes idênticas só para dizer isso, ou a mexer no papel de uma jogada e quebrar outra sem
perceber. A Role declara o que faz e o `dCusto()` de cada candidato; o quanto ela importa é
decisão de quem monta a jogada.

E a prioridade é **obrigatória** no `bAddRole`, sem padrão silencioso: é uma decisão de projeto da
play, não um detalhe. Um padrão faria toda play nova cair na mesma categoria e o mecanismo inteiro
virar enfeite.

Três consequências, e as três são o que faz o mecanismo funcionar de verdade:

* **Papel sem robô é situação normal, não erro.** Fica com `player()` nulo, não roda, e **não
  conta para o fim da play** — exigi-lo faria uma play de seis papéis rodando com cinco robôs
  ficar pendurada até o tempo limite, que é exatamente o caso que as categorias existem para
  permitir.
* **Papel que perde o robô fica explicitamente vazio.** Guardar o robô do quadro anterior faria o
  mesmo robô aparecer em dois papéis ao mesmo tempo, cada um escrevendo um comando por cima do
  outro.
* **`MAXIMA` é levado a sério nas duas pontas.** Uma play cujos `MAXIMA` não cabem nos robôs
  disponíveis não entra no sorteio do coach — começar e abortar no primeiro tique gastaria um
  episódio de aprendizado com um resultado que não diz nada sobre a jogada — e perder um `MAXIMA`
  no meio da jogada aborta a play, porque continuar seria rodar uma defesa sem goleiro.

### Os ids são globais

Cada camada guarda as de baixo num mapa indexado por `int`: a Role tem um mapa de táticas, a
Tactic um de skills, o Coach um de plays. Esses números eram **locais** — cada arquivo declarava
o seu `private static final int TATICA_BUSCAR = 1`. Funcionava com um papel só e quebrava do jeito
mais chato assim que aparecia o segundo: o mesmo `TacticBuscarBola` era 1 num papel e 2 em outro, e nada
impedia dois papéis de discordarem. O número acabava sendo propriedade de **quem registrou**, e
não da coisa registrada.

Agora há um enum público por família — `Jogada`, `Papel`, `Tatica`, `Habilidade` — e o número é
propriedade da coisa. `TacticBuscarBola` é `Tatica.BUSCAR_BOLA` em qualquer papel, para sempre, e um
papel novo não inventa numeração: escolhe da lista.

```java
private final TacticBuscarBola buscar = new TacticBuscarBola();          // sabe o próprio id
bAddTactic(Tatica.BUSCAR_BOLA, buscar);
bSetTactic(Tatica.CHUTAR_AO_GOL);
```

**Nunca renumere.** O id vai para o log e, no caso das plays, para a nota persistida: trocar um
número faz o histórico passar a falar de outra coisa sem que nada acuse, e faz uma play herdar o
aprendizado de outra. Item que sai de uso deixa o número dele queimado.

Uma ressalva sobre `Papel`: `bAddRole` indexa pelo id do papel, então dois papéis com o mesmo
número na mesma play fariam o segundo ser recusado em silêncio. Uma jogada com **dois zagueiros**
precisa de duas entradas no enum (`ZAGUEIRO_ESQUERDO`, `ZAGUEIRO_DIREITO`), e não da mesma usada
duas vezes. Não confunda o id com a `Prioridade`: o id diz *qual* papel é, a prioridade diz o
quanto ele importa naquela jogada — e é por isso que um mora no enum global e a outra na play.

### Os números que se ajusta

Tolerâncias, distâncias e tetos de velocidade estavam espalhados como `static final` dentro da
skill, da tática ou da play que os usava. Cada um no seu lugar parecia arrumado, e escondia dois
problemas. Ajustar exigia **recompilar** — e numa bancada decidir se a tolerância de chegada deve
ser 40 ou 25 mm é tentar seis valores em dois minutos, não editar, compilar e reabrir a janela
seis vezes. E ninguém sabia quantos eram nem onde estavam: o que não dá para listar não dá para
ajustar com método.

Agora estão todos em `ajuste.Parametro`, e há três formas de mexer, da mais rápida para a mais
duradoura:

* **A aba *Ajustes*** da Configuração avançada, que é onde os valores se mexem — o código guarda
  só o padrão de fábrica. Vale no tique seguinte, sem recompilar e sem reiniciar. Cada linha mostra o valor de fábrica ao lado — depois de meia hora mexendo, ninguém
  lembra de onde partiu, e sem essa referência "restaurar" vira a única saída em vez de um passo
  atrás.
* **`ajustes.json`**, carregado do diretório atual na abertura, ou por `--ajustes <arquivo>`. É o
  que faz o ajuste sobreviver ao fechar do programa. `--gerar-ajustes` imprime o modelo com os
  valores atuais.
* **O padrão no código**, que é o valor de fábrica.

O arquivo diz o que **muda**; o que ele não cita fica no padrão. Nome desconhecido é **erro**, e
não aviso: um parâmetro digitado errado que passa em silêncio faz procurar o problema no
comportamento do robô, que é o lugar errado. A carga nunca é silenciosa — o programa imprime o
que mudou, porque um valor diferente do de fábrica sem nada dizendo vira caça ao fantasma.

O leitor de JSON é escrito à mão, pelo mesmo motivo que o resto compila offline: nada justifica
uma segunda dependência para ler dezessete números. Aceita só `{"CHAVE": número}` — sem
aninhamento, sem texto, sem lista. A única concessão é o comentário `//`, que JSON não tem: este
arquivo é editado por gente, e um número sem a unidade ao lado é como se acaba escrevendo grau
onde se esperava radiano.

O que **não** entra no enum, e por quê:

| fora | motivo |
|---|---|
| regra da liga (teto de 1,5 m/s no `STOP`, 500 mm da bola) | mudar não é ajustar, é jogar errado |
| dimensão física (raio do robô, face do dribbler) | é medida, não escolha |
| geometria do campo | vem da rede, e chumbar quebra em Divisão A |

**Ângulo vai em graus**, e é a única unidade que foge do mm/rad/s do resto. Não é
inconsistência: é a mesma regra que já vale para a rede — a conversão acontece na borda, e um
arquivo de ajuste é uma borda. Quem digita "2" para tolerância de mira está pensando em dois
graus, e `0.03490658503988659` num campo de texto não é um número que alguém revise.

### Geometria: reta, segmento e semirreta não são a mesma pergunta

`core.Geo` responde as perguntas que envolvem **dois** objetos — onde duas retas se cruzam, se um
segmento passa por dentro de um círculo. Ficam fora de `Vec2` de propósito: `Vec2` é um valor, e
enfiar isso nele faria o tipo mais usado do programa crescer sem parar.

A distinção que mais causa erro silencioso está no nome de cada função:

| | responde | usa quando |
|---|---|---|
| **reta** | sempre, se não forem paralelas | a linha não tem começo nem fim |
| **semirreta** | só à frente da origem | trajetória: a bola vai **para lá**, não para trás |
| **segmento** | só dentro do trecho | um lugar onde dá para estar: a linha do gol, entre os postes |

Perguntar "onde essas duas **retas** se cruzam" sempre devolve um ponto — inclusive quando a bola
está se afastando ou passa longe do poste. Um goleiro que acredite nessa resposta corre para fora
do gol. Por isso `getVec2MiraNoNossoGol()` é **semirreta contra segmento**: só existe resposta
quando a bola realmente vem, e quando ela entra entre os postes.

Toda função que pode não ter resposta devolve `null`, e nunca um ponto inventado. "São paralelas"
e "cruza fora do trecho" são respostas legítimas, e quem chama precisa distinguir isso de um ponto
de verdade.

**O tipo de retorno vai no nome**: `getVec2…` para ponto, `b…` para booleano, `d…` para double,
como o resto do projeto já faz com `dGetScore` e `bCheckPreConditions`. Numa cadeia de contas
geométricas o tipo do resultado é o que mais se erra, e ele aparecer no nome resolve antes de
compilar.

Em cima disso, o `Ambiente` oferece o que a estratégia realmente pergunta:
`getVec2MiraNoNossoGol()`, `bBolaVemParaONossoGol()`, `getVec2PostoDoGoleiro(recuo)`,
`bLinhaLivre(de, para, folga, ignorar…)` e `dFolgaContraEles(de, para)`. A folga soma o raio do
robô ao da bola: a linha não precisa passar pelo centro do adversário para o passe morrer.

As duas aparecem no campo, e podem ser desligadas em *Exibição*:

* **Linha de mira**, vermelha e contínua, da bola até onde ela cruza a linha do nosso gol. Some
  sozinha quando a bola muda de rumo. Contínua porque é o que está acontecendo agora — as zonas,
  que são regra, vão tracejadas.
* **Linha de chute**, tracejada, de quem está com a bola até o gol que ele ataca: **verde** quando
  ninguém corta, **laranja** quando alguém corta. É a mesma conta que uma play usaria para decidir
  chutar, e vê-la na tela é o que permite discordar dela olhando.

### Projeção e o fantasma

Onde o robô **vai** estar, a partir de onde está e para onde vai. É navegação estimada, a mesma
conta que um navio faz na carta: posição atual mais rumo e velocidade, integrados no tempo. O
Kalman já entrega os dois, então projetar sai de graça.

O campo desenha, para todo robô em movimento, um **fantasma** de onde ele estaria daqui a
1 segundo, mais a reta até lá. É o vetor de rumo de um radar, com a mesma simplificação: linha
reta na velocidade atual, ignorando que ele pode estar virando ou freando.

Essa simplificação não é descuido, é o que dá utilidade ao desenho. Quando o fantasma não bate
com o que o robô faz, o que se está vendo é ele mudando de ideia. E quando o fantasma aparece
atravessando a parede, o recado é que naquele rumo e naquela velocidade ele não tem mais um
segundo de campo pela frente.

O fantasma some abaixo de 150 mm/s. Sem esse corte, robô parado ganharia um fantasma em cima de
si mesmo e o campo viraria doze círculos tracejados sobrepostos, dizendo nada.

`Projecao` também traz o **CPA** da navegação: a menor distância entre dois corpos se ambos
mantiverem o rumo, e quando isso acontece. Não está em uso, e é a base do desvio de obstáculo
quando ele entrar.

**Não há projeção de bola, de propósito.** O robô mantém velocidade enquanto o comando mandar,
então a reta é uma aproximação honesta por um segundo. A bola desacelera contra o carpete,
quica e muda de dono, e prever isso direito é um problema por si.

### A área de defesa é do goleiro, e só dele

Entrar na própria área de defesa sendo robô de linha é pênalti, e é a regra que mais custa caro
por descuido. Por isso ela mora no mesmo lugar que os limites do árbitro — o `Executor`, no fim
do tique — e não em cada play.

São **duas zonas**, e elas dizem coisas opostas:

* A **zona proibida**, em vermelho tracejado: robô de linha não entra. Ela vai da frente da área
  até a **parede**, e não até a linha de fundo — o corredor atrás do gol não tem jogada nenhuma,
  e um robô que entra lá errou o caminho e volta atravessando a área.
* A **zona do goleiro**, em verde por dentro dela: onde ele pode ficar. É a área de defesa mais
  **um raio de robô**, e essa sobra é o que faz diferença — com a zona colada na linha, o goleiro
  não consegue ficar *em cima* dela, que é justamente onde um goleiro fica. Com um raio de folga
  ele encosta a casca na linha por fora e continua valendo.

A zona proibida sai da área que a **visão informou** (`penalty_area_depth`/`width`) mais uma
**folga de segurança**, e por vir da geometria da rede continua certa em Divisão A. O padrão da
folga é 45 mm, meio raio de robô. Quem quiser mandar na medida direto tem `ZONA_PROFUNDIDADE` e
`ZONA_LARGURA` na aba *Ajustes*: em zero — o padrão — a conta volta a ser da visão; com um número,
ele manda, e aí é responsabilidade de quem digitou conferir a divisão.

Quem pode entrar é o **goleiro declarado**, e só ele. O número vem do `Referee.TeamInfo`, do
Game Controller ou do árbitro de teste — é declaração de equipe, não escolha da estratégia.

**Cortar seco o comando não bastaria.** O robô desacelera a `ACEL_MAX`, então a 3 m/s ele ainda
percorreria 1,5 m depois do comando ir a zero, e entraria na área assim mesmo. O que se limita é
a velocidade de **aproximação**, ao maior valor que ainda deixa parar antes da linha —
`v = √(2·a·d)`, o mesmo perfil de frenagem que `SkillAndarPara` usa para chegar num ponto. De longe
não corta nada; chegando perto, freia na taxa certa; já dentro, sobra só a componente de saída.

Só a componente **normal** é tocada; a tangencial passa inteira. Uma defesa que trava ao encostar
na área é pior que uma que a contorna.

As duas são tracejadas, e não contínuas: as linhas brancas são o que a visão mediu, e estes
retângulos são decisão nossa — desenhá-los com o mesmo traço faria a folga parecer pintada no
chão. Vermelho para a proibição e verde para a permissão, o mesmo verde do sensor de bola: duas
proibições onde há uma proibição e uma permissão seria pior que legenda nenhuma.

A conta do desenho e a do corte saem da mesma `Geometria.zonaProibida` — ver na tela uma borda
diferente da que está sendo obedecida seria pior que não ver borda nenhuma. Aparecem só do
**nosso** lado: a área deles também é proibida pela regra, mas a estratégia ainda não a trata, e
desenhar uma restrição que não está em vigor prometeria o que o código não cumpre.

### O lado nunca se deduz de um campo ausente

`blue_team_on_positive_half` é **`optional`** no protocolo, e a leitura era
`has(...) && get(...)`. Um pacote sem o campo virava "azul no lado negativo" — que não é
"não sei", é uma afirmação trocada. Com o Game Controller alternando pacotes com e sem ele, o
lado invertia, a zona proibida pulava para a outra metade e a **nossa** área ficava
desguarnecida: robô de linha entrava com o comando intacto, porque a zona que o `Executor`
recortava era a do adversário. É pênalti, e do pior tipo — a tela desenhava a zona na metade
errada com o mesmo ar de certeza.

O `Arbitro` guarda agora o último valor que alguém **declarou**, fora do retrato cru: um pacote
omisso não apaga o que já se sabia. Guardar o valor do *azul*, e não o nosso lado, mantém a
tradução na leitura — trocar a nossa cor com a janela aberta continua valendo na hora.

Enquanto ninguém declarou, o lado em uso é um chute, e o painel do árbitro diz isso em amarelo
em vez de mostrar a contagem de pacotes. Vale a regra do projeto: o que está na tela ou veio da
rede, ou é inferência, e inferência precisa aparecer como tal. O teste antigo só exercitava o
árbitro de bancada, que preenche o campo sempre — foi por isso que o buraco sobreviveu.

### Os limites do árbitro ficam fora das plays

`HALT` não deixa sair comando, e fora de jogo corrido a velocidade satura enquanto chute e
dribbler são cortados. Isso é aplicado no `Executor`, no fim do tique, depois de todo mundo ter
opinado. Poderia ficar em cada play, e é assim que se costuma errar: basta uma esquecer de
checar `HALT` para o time andar com o jogo parado, e o custo disso é falta.

### O que está acontecendo, e o que aconteceu

São duas perguntas diferentes, e ficam em lugares diferentes.

**O que está acontecendo agora** fica no painel da direita, embaixo de cada robô: o papel, a
tática e a skill que ele está executando, em verde quando existem e vermelho quando faltam. É a
pergunta que se faz olhando um robô, junto da velocidade e do sensor dele. Uma tática concluída
aparece em verde com o sufixo "fim", porque concluir é ter dado certo — mirar e chegar
terminam. Vermelho fica para robô sem papel, papel sem tática, tática sem skill, que são as três
formas de um robô estar parado sem ninguém perceber.

Abaixo dos robôs vem o **repertório de jogadas**, cada uma com a nota, e a que está rodando em
destaque. Jogada sem pré-condição continua na lista, apagada, em vez de sumir: quem olha quer
entender por que o coach escolheu aquela, e para isso precisa ver as que ele não podia escolher.

**O que aconteceu** fica na janela do botão *Ver log...*, em texto corrido como sairia no
terminal, com o carimbo do relógio da visão. Play que entra, papel que muda de robô, tática que
troca, bola que entra no sensor, chute que sai. Misturar as duas coisas na mesma tela faria o
estado rolar para fora junto com o histórico.

O `Diario` **observa** em vez de ser chamado: olha a árvore do coach a cada tique e anota o que
mudou. O contrário, com cada skill chamando um logger, obrigaria a passar o diário por todas as
camadas e faria quem escreve uma skill nova ter de lembrar de registrar. Diferença não esquece.
Em troca, o motivo de uma escolha fica de fora — só o resultado dela entra.

Ele também anota o que o árbitro **corta**: a play pediu chute, o `Executor` podou por causa do
`STOP`, e sem essa linha o comando sumiria sem deixar rastro.

### A jogada de bancada

`PlayTesteAtacante` existe para provar que o encanamento inteiro funciona, do `Coach` até o
`Comando` no fio: um papel só, sem passe, sem marcação, sem desvio de obstáculo e **sem prever
a bola** — o atacante vai onde ela está, não onde ela estará. O `RoleAtacante`
escolhe entre três táticas **pelo estado do mundo**, e não por lembrar em que fase está: sem a
bola, buscar; com a bola longe do gol, conduzir; com a bola perto, chutar. Uma máquina de
estados com memória ficaria presa em "conduzindo" no instante em que a bola escapasse, e o robô
continuaria empurrando o ar.

Medido contra o simulador, com o campo livre e a bola no centro: **gol em 6,2 s**.

Três detalhes que custaram depuração e viraram teste:

**O destino da condução não pode ser igual ao limiar de chute.** Com os dois em 2500 mm, o robô
parava exatamente em cima da fronteira, a tolerância de chegada o deixava ora dentro ora fora, e
quando ficava fora a jogada morria ali com a bola presa. A condução vai até 2000 mm e a mira
começa em 2500 mm.

**A aproximação mira o centro da bola, não o ponto de contato.** Mirar o contato exato parece
certo e não é: o robô declara chegada dentro da própria tolerância, e nessa folga a bola fica
além do alcance do sensor. O resultado era um robô parado a um palmo da bola, para sempre.

**O chute espera a posse assentar.** Sem isso o robô chuta no instante em que a bola encosta, e
uma bola que só passa raspando pela boca aciona o sensor por um quadro e leva um chute para
lugar nenhum. São 0,15 s de bola no sensor antes de o chutador valer.

## Sensor de bola

O robô de verdade tem uma barreira infravermelha atravessando a boca do dribbler: quando a
bola entra, o feixe corta. Esse bit não existe no protocolo, então é reconstruído por
geometria, no referencial local do robô, onde o teste fica trivial. A boca é um segmento em
`x = 72,5 mm`, e basta a bola estar logo à frente dele e dentro da largura útil do rolete.

Duas condições que parecem detalhe e não são. Sem a **largura da boca**, uma bola encostada na
lateral da capa contaria como posse. Sem a **altura**, uma bola passando por cima contaria
também, e como a visão reporta z do centro, "no chão" é `z <= raio · 2,5`, e não `z == 0`.

A área desenhada no campo é exatamente a região que o teste usa. Desenhar a própria condição,
em vez de um ícone aceso ao lado do robô, é o que permite ver por que o sensor disparou ou não:
uma bola que passa raspando fica visivelmente fora da faixa.

Verificado contra o simulador, que tem a verdade interna. Rodando o cenário
`conducao-com-roller`, a inferência acusa `blue_0` com a bola em 1118 de 1125 quadros, e o
simulador diz o mesmo `blue_0`.

## Interface

```
+--------------------------------------------------+
|  BarraSuperior: equipes, cores, tempo de partida  |
+--------+--------------------------------+--------+
| Painel |                                | Painel |
|  Rede  |             Campo              | Robos  |
| árbitro|                                | jogadas|
+--------+--------------------------------+--------+
         ^                                ^
      divisores arrastáveis; o campo absorve a sobra
```

Os três painéis conversam só pelo `Cliente`, e nenhum tem referência para o outro. A exceção é
a seleção de robô, que precisa mesmo ser compartilhada: clicar no campo acende o cartão,
clicar no cartão acende o robô.

**Topo.** Cada equipe num bloco pintado com a própria cor, nas mesmas cores dos robôs no campo,
porque a associação nome/cor tem de ser lida de relance no meio de um jogo. Nome e placar vêm
do Game Controller quando ele está no ar, que é quem sabe: `TeamInfo` carrega o nome inscrito e
o placar oficial. Sem árbitro, os nomes caem para os configurados no painel da esquerda e o
placar simplesmente não aparece, porque exibir zero a zero sem fonte seria inventar um dado.

O comando do árbitro fica no meio, grande e colorido, porque é a informação que muda o que a
equipe pode fazer no instante seguinte. O relógio mostra o tempo restante do estágio quando há
árbitro, e o `t_capture` da visão quando não há. São coisas diferentes: um é tempo de jogo, o
outro é tempo de simulação.

**Esquerda.** A coluna é dividida pelo critério do que se **olha** e do que se **aperta**:
estado da rede (para onde escutamos, a que taxa, para onde mandamos), estado do árbitro, estado
da estratégia — e os **comandos de árbitro**, que são os botões que mais se usam numa bancada.

Antes era o contrário. Os comandos moravam num diálogo atrás de um botão *Simular...*, a duas
aberturas de distância de um STOP, enquanto nomes de equipe e caixas de exibição — coisas que se
mexem uma vez por bancada — ocupavam a maior parte da coluna.

Tudo que se **configura** foi para *Configuração avançada...*, em três abas: **Equipe** (cor,
nomes, lado de ataque, goleiro e a folga da área), **Rede** (as sete portas e endereços) e
**Exibição**. Em abas porque as três seções numa coluna só passariam de 700 px de altura, e
ninguém rola um diálogo de configuração para achar um campo de porta. Tudo continua trocável com
a janela aberta, porque numa bancada troca-se de porta o tempo todo.

Dentro da aba **Equipe** há uma divisão que importa: cor e nomes esperam o *Aplicar*, porque
trocar de cor **reabre os sockets** — a porta de destino dos comandos depende dela. Lado, goleiro
e folga valem no clique, porque não custam nada. Um campo que parece ter sido aplicado e não foi
é o pior dos dois mundos.

Lado de ataque e goleiro são **declarações de árbitro**, não comandos: viajam dentro de toda
mensagem `Referee`, e por isso trocá-los reemite o comando corrente em vez de inventar um STOP e
interromper o jogo por causa de uma configuração. Com o Game Controller no ar quem declara os
dois é ele, e os dois controles ficam desabilitados.

Em modo REDE os botões de árbitro ficam **desabilitados**, e não apenas inertes: num diálogo que
alguém acabou de escolher abrir bastava uma linha de aviso, mas numa coluna sempre visível
dezessete botões que parecem funcionar e não fazem nada são pior que aviso nenhum.

A validação segue a mesma divisão do simulador. O que dá para saber sem tocar no sistema
(endereço fora da faixa multicast, porta repetida) é recusado na hora em que se digita. Já
"porta ocupada" só aparece no bind, e nesse caso a configuração anterior é restaurada em vez
de deixar a estratégia surda.

**Centro.** Zoom no scroll, arrasto move o campo, clique seleciona robô. Vetores de
velocidade, área do sensor, anel de incerteza e a previsão do desenho podem ser desligados em
*Configuração avançada...*.

Arrastar com o **botão esquerdo** move o campo. Antes só o direito deslocava, e num trackpad de
MacBook não há botão direito para segurar — clique de dois dedos não se sustenta enquanto um
terceiro arrasta —, então o campo era, na prática, fixo para quem não usa mouse. O direito
continua funcionando.

O zoom ancora no **cursor**: o ponto do campo sob o ponteiro fica parado enquanto a escala muda.
Antes ancorava no centro do painel, e quem olhava um canto via o canto fugir da tela a cada
passo, tendo de arrastar de volta toda vez.

Cada evento de scroll vale no máximo **um entalhe de roda**. O trackpad do mac não manda um
evento por gesto como uma roda com entalhe: manda uma rajada — medidos 327 eventos em 4 s — em
que o peso varia de 0,006 a 4,7. Sem teto, aquele 4,7 sozinho mudava a escala em 58% num quadro,
no meio de centenas de eventos que não faziam nada visível. Era esse contraste, e não a
sensibilidade média, que fazia o zoom parecer instável: quase parado e de repente um pulo.
Cortar em um entalhe limita pela coisa certa e por isso não estraga a roda de mouse, onde o
valor já é 1 e o corte nunca age. O `teste.Autoteste` prende as três coisas — âncora, batente e
teto do pico.

O gramado é desenhado com as **faixas do corte**, como um campo de futebol de verdade. Não é
enfeite: esta é uma tela de leitura — é nela que se percebe o filtro mentindo — e um retângulo
verde uniforme não dá referência nenhuma de posição. Com o corte marcado, um robô que andou meia
faixa e um que andou três se distinguem de relance, sem ler número.

A largura da faixa é a **profundidade da área de defesa**, e as faixas são contadas a partir de
cada linha de fundo para dentro. Duas consequências, e as duas são o motivo da escolha: a
primeira faixa de cada lado coincide exatamente com a área, que é a régua que mais interessa em
campo; e o desenho fica **espelhado**, igual visto de qualquer um dos dois gols. A faixa do meio
é o que sobra depois das inteiras — 1000 mm em Divisão B, 1200 em Divisão A — e é simétrica em
torno da linha central nos dois casos, que é o que sustenta o espelho. A média exata dos dois
tons é o verde chapado do simulador, então as duas janelas lado a lado continuam lendo como o
mesmo campo.

As duas colunas têm **largura arrastável**, e o campo absorve a diferença — inclusive
recolhendo uma coluna inteira pelas setinhas do divisor, quando a bancada aperta. Redimensionar
a janela alarga o campo e não as colunas: são elas que guardam a largura escolhida.

O gol é desenhado como estrutura **aberta** — dois postes e o fundo — e não mais como o bloco
chapado atrás da linha de fundo. O bloco dava a entender que o gol é maciço, e desde que o
simulador passou a tratar as paredes do gol como física de verdade isso ficou errado de dois
jeitos: a bola entra e fica lá dentro, e parada dentro do gol ela sumia atrás do bloco.

O contorno é uma **linha**, e não uma parede com espessura. `SSL_GeometryFieldSize` manda
largura e profundidade do gol, e nada mais: espessura de parede não existe no protocolo.
Desenhá-la com os 20 mm do regulamento seria chumbar no cliente um número que não veio da rede,
o mesmo erro de chumbar 9000 × 6000 e quebrar em Divisão A. A espessura usada é a das outras
linhas do campo, e essa vem no pacote.

A previsão existe porque aqui a cena não é integrada no mesmo instante em que se pinta, como
acontece na janela do simulador: ela chega em amostras discretas pela rede. A tela repinta a 60
Hz e a visão chega a 60 Hz, mas as duas cadências não estão em fase, então alguns repaints
mostram o mesmo quadro duas vezes e outros pulam um, e o movimento treme mesmo com a
estimativa perfeita. Como cada objeto tem velocidade estimada, desenhá-lo em `p + v·dt` põe a
cena no instante do desenho em vez do instante do último pacote. Medido com a bola em
movimento, os repaints sem avanço caem de 9 em 120 para 1 em 163. O avanço é limitado a 80 ms:
se a visão parar, a cena congela onde estava, porque é melhor vê-la parar do que vê-la
inventar.

**Direita.** Um cartão por robô **nosso**, com ID, velocidade, direção e o selo do sensor. O
adversário fica de fora porque estes cartões existem para operar os nossos robôs: é sobre eles
que se decide, e serão eles que ganharão comando, papel e estado de skill conforme a estratégia
crescer. De um robô do outro time só se sabe onde está. Ele continua no campo, e clicável lá.

A direção aparece como seta desenhada no ângulo real mais o valor em graus, porque a seta
sozinha é ambígua em cima de 90° e o número sozinho não se lê de relance. Robô que sumiu da
visão continua listado, apagado, com há quanto tempo não é visto.

O painel é desenhado em um componente só, e não montado com um `JPanel` por robô. A lista muda
de tamanho a cada quadro, e recriar componentes Swing sessenta vezes por segundo custaria mais
do que redesenhar tudo.

### Aparência

Antes, a janela pedia `UIManager.getSystemLookAndFeelClassName()` — o visual do sistema
operacional. Aqua no macOS, WindowsLookAndFeel no Windows: o botão saía diferente em cada
máquina porque era exatamente isso que a chamada mandava fazer. E o Aqua **ignora**
`setBackground` em botão, então o painel escuro da `Paleta` ficava com botão claro do sistema
no mac e obedecia no Windows. Estilizar componente a componente não resolveria, porque cada
look-and-feel decide sozinho o que respeitar.

Hoje `view.Estilo` instala o [FlatLaf](https://www.formdev.com/flatlaf/), que desenha tudo em
Java sem delegar nada ao sistema — mesma janela nos três — e alimenta o L&F com as cores da
`Paleta` que já existiam. O simulador faz o mesmo, com os mesmos valores: as duas janelas ficam
lado a lado e um cinza diferente em cada uma faria parecer dois programas.

A fonte é um segundo eixo, independente do L&F, e é a parte que passa despercebida:
`new Font("SansSerif", ...)` não nomeia uma fonte, é um pedido que o JDK resolve para uma fonte
**física** diferente em cada sistema. Larguras diferentes movem o texto que `Campo` e
`BarraSuperior` desenham direto no `Graphics`, e nenhum look-and-feel conserta isso. A
JetBrains Mono vai embarcada no jar, e todo `new Font` do código virou `Estilo.fonte(...)` —
um só que sobrasse traria a divergência de volta.

Ela é monoespaçada de propósito, num programa que é quase todo número medido: coordenada,
velocidade e tempo não dançam de largura quando só o algarismo muda. O custo é que coluna
passou a ser caractere na régua, sem a folga que uma proporcional dava de graça — foi o que
alargou o campo de nome do simulador de 9 para 12 colunas, e a coluna de perguntas do diálogo
de física de 290 para 335 px. Ao mexer em largura fixa perto de texto, meça com
`getFontMetrics` em vez de estimar no olho.
