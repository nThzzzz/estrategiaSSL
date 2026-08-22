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

java -cp "out/production/estrategiaSSL:lib/*" teste.Autoteste  # percepção e protocolo
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
core      →  (nada)              Vec2, Angulo
model     →  core, proto         Geometria, Cor, Robo, Comando
mundo     →  core, model         Quadro, EstadoRobo, EstadoBola
jogo      →  core, model, proto  Arbitro, EstadoDeJogo, Acao, ArbitroLocal
percepcao →  + proto             Rastreador, Trilha, FiltroKalman1D, SensorDeBola, Ajuste
rede      →  + percepcao, jogo   ConfigRede, ReceptorDeVisao, ReceptorDeArbitro, EmissorDeComandos
view      →  core, model, mundo  Campo, Paleta
app       →  tudo                Cliente, Janela, BarraSuperior, PainelRede, PainelRobos
```

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
