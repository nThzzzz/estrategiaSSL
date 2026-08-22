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

A única dependência é `protobuf-java`, versionada em `lib/` junto com o Java gerado dos
`.proto` (em `src/proto/`), exatamente como no simulador. Um clone limpo compila offline.

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
core       →  (nada)              Vec2, Angulo
model      →  core, proto         Geometria, Cor, Robo, Comando
mundo      →  core, model         Quadro, EstadoRobo, EstadoBola
jogo       →  core, model, proto  Arbitro, EstadoDeJogo, Acao, ArbitroLocal
percepcao  →  + proto             Rastreador, Trilha, FiltroKalman1D, SensorDeBola, Ajuste
estrategia →  mundo, jogo, model  Coach, Play, Role, Tactic, Skill, Ambiente, Jogador, Executor
  skills   →  as micro habilidades      AndarPara, OlharPara, Chutar
  tactics  →  conjuntos de skills       BuscarBola, ConduzirAoGol, ChutarAoGol
  roles    →  papéis de um robô         Atacante
  plays    →  jogadas do time           TesteAtacante
  coaches  →  quem escolhe a play       Bancada
rede       →  + percepcao, jogo   ConfigRede, ReceptorDeVisao, ReceptorDeArbitro, EmissorDeComandos
view       →  core, model, mundo  Campo, Paleta
app        →  tudo                Cliente, Janela, BarraSuperior, PainelRede, PainelRobos
```

Note que `estrategia` não depende de `rede` nem de `view`. Quem decide o que os robôs
fazem só conhece `Ambiente` e `Jogador`, e é isso que permite rodar mil partidas de treino
sem abrir janela nem tocar em socket.

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

O painel da esquerda troca a fonte entre o Game Controller e um árbitro local de teste, com
botões para cada comando. Ele monta uma mensagem `Referee` de verdade e a entrega pelo mesmo
caminho de tradução que a rede usa, em vez de injetar o estado já pronto: se um comando estiver
mapeado errado, erra ali igual erraria em jogo.

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

A Role não escolhe qual robô a executa. Ela declara o papel, a prioridade e o `dCusto()` de cada
candidato, e a Play atribui. É o que faz a mesma play continuar valendo quando um robô leva
cartão ou quebra: uma play que fixasse "o 3 é o goleiro" pararia de funcionar no instante em que
o 3 saísse de campo.

A atribuição tem **histerese**: quem já está no papel leva 30% de vantagem. Sem isso, dois robôs
a distâncias parecidas ficam trocando de função a cada quadro e nenhum dos dois chega a lugar
nenhum.

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

### Os limites do árbitro ficam fora das plays

`HALT` não deixa sair comando, e fora de jogo corrido a velocidade satura enquanto chute e
dribbler são cortados. Isso é aplicado no `Executor`, no fim do tique, depois de todo mundo ter
opinado. Poderia ficar em cada play, e é assim que se costuma errar: basta uma esquecer de
checar `HALT` para o time andar com o jogo parado, e o custo disso é falta.

### A jogada de bancada

`TesteAtacante` existe para provar que o encanamento inteiro funciona, do `Coach` até o
`Comando` no fio: um papel só, sem passe, sem marcação, sem desvio de obstáculo e **sem prever
a bola** — o atacante vai onde ela está, não onde ela estará. O `Atacante`
escolhe entre três táticas **pelo estado do mundo**, e não por lembrar em que fase está: sem a
bola, buscar; com a bola longe do gol, conduzir; com a bola perto, chutar. Uma máquina de
estados com memória ficaria presa em "conduzindo" no instante em que a bola escapasse, e o robô
continuaria empurrando o ar.

Medido contra o simulador, com o campo livre e a bola no centro: **gol em 5,2 s**.

Um detalhe que custou uma depuração e virou teste: o destino da condução não pode ser igual ao
limiar que decide entre conduzir e chutar. Com os dois em 2500 mm, o robô parava exatamente em
cima da fronteira, a tolerância de chegada fazia ele ficar ora dentro ora fora, e quando ficava
fora a jogada morria ali com a bola presa. A condução vai até 2000 mm e a mira começa em
2500 mm, então a troca acontece durante a aproximação.

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
+--------+--------------------------------+--------+
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

**Esquerda.** Estado da rede (para onde escutamos, a que taxa, para onde mandamos), a fonte do
árbitro com o botão *Simular...*, a cor da equipe e os nomes. As portas ficam atrás do botão *Configurar...*, no mesmo desenho do
simulador: cinco campos ocupavam metade do painel para algo que se mexe uma vez por bancada,
enquanto o que se olha o tempo todo ficava espremido embaixo. Tudo é trocável com a janela
aberta, porque numa bancada troca-se de porta o tempo todo.

A validação segue a mesma divisão do simulador. O que dá para saber sem tocar no sistema
(endereço fora da faixa multicast, porta repetida) é recusado na hora em que se digita. Já
"porta ocupada" só aparece no bind, e nesse caso a configuração anterior é restaurada em vez
de deixar a estratégia surda.

**Centro.** Zoom no scroll, arrasto com o botão direito, clique seleciona robô. Vetores de
velocidade, área do sensor, anel de incerteza e a previsão do desenho podem ser desligados.

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
