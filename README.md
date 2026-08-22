# 🎯 Estratégia SSL

Software de time da **Small Size League** (RoboCup), escrito em Java (Swing/Graphics2D).
Escuta a visão pelo protocolo oficial da liga, reconstrói o que a câmera não manda e mostra
numa janela. É o outro lado da conversa do [simuladorSSL](../simuladorSSL): o simulador é a
fonte da visão, este projeto é quem consome e — quando houver estratégia escrita — quem
comanda os robôs.

## Como rodar

Com o simulador aberto em outra janela:

```bash
./tools/build.sh                    # javac puro, sem Gradle e sem rede

java -cp "out/production/estrategiaSSL:lib/*" Main             # joga de azul
java -cp "out/production/estrategiaSSL:lib/*" Main --amarelo   # joga de amarelo
java -cp "out/production/estrategiaSSL:lib/*" Main --host 192.168.0.10   # simulador em outra máquina
java -cp "out/production/estrategiaSSL:lib/*" Main --ajuda

java -cp "out/production/estrategiaSSL:lib/*" teste.Autoteste  # filtro, sensor e protocolo
```

A única dependência é `protobuf-java`, versionada em `lib/` junto com o Java gerado dos
`.proto` (em `src/proto/`), exatamente como no simulador. Um clone limpo compila offline.

**Não coloque este projeto e o simulador no mesmo classpath.** Os dois têm `model.Geometria`,
`model.Cor` e `rede.ConfigRede` com formatos diferentes, de propósito: um descreve o mundo que
simula, o outro descreve o mundo que observa. São dois processos que conversam por UDP, e é
só assim que devem se encontrar.

## O que a rede entrega, e o que não

Este é o fato que organiza o projeto inteiro. `SSL_DetectionRobot` carrega exatamente isto:

```
confidence, robot_id, x, y, orientation, pixel_x, pixel_y, height
```

Não há velocidade. Não há posse de bola. Não há placar, nome de time nem estado de jogo. A
câmera descreve o que enxerga num instante, e o resto é trabalho de quem recebe:

| o que a interface mostra | de onde vem |
|---|---|
| posição e orientação | medido, direto da rede |
| geometria do campo | `SSL_GeometryData`, reenviado a cada 60 quadros |
| **velocidade e ω** | **inferido** — Kalman sobre quadros consecutivos |
| **bola no sensor** | **inferido** — geometria da boca do dribbler |
| tempo de partida | `t_capture` da visão |
| nomes dos times | configuração local (viriam do Game Controller) |

## Arquitetura

Dependências em uma direção, sem ciclos, como no simulador:

```
core      →  (nada)              Vec2, Angulo
model     →  core, proto         Geometria, Cor, Robo, Comando
mundo     →  core, model         Quadro, EstadoRobo, EstadoBola
percepcao →  + proto             Rastreador, Trilha, FiltroKalman1D, SensorDeBola, Ajuste
rede      →  + percepcao         ConfigRede, ReceptorDeVisao, EmissorDeComandos
view      →  core, model, mundo  Campo, Paleta
app       →  tudo                Cliente, Janela, BarraSuperior, PainelRede, PainelRobos
```

Uma entrada e uma saída, ao contrário do simulador, que tem uma saída e três entradas:

| | endereço | mensagem |
|---|---|---|
| visão (entrada) | multicast `224.5.23.2:10006` | `SSL_WrapperPacket` |
| comandos (saída) | `127.0.0.1:10301` azul, `:10302` amarelo | `RobotControl` |

O ingresso no grupo multicast é tentado em **todas** as interfaces que suportam multicast, não
só na padrão. Numa máquina com Wi-Fi, Ethernet e as interfaces virtuais que Docker e VPN
criam, a rota padrão quase nunca é a que recebe o tráfego — entrar só nela é o motivo clássico
de "o simulador publica e o cliente não recebe nada".

## Filtro de Kalman

O que substituiu a primeira versão, que era um passa-baixa exponencial. O passa-baixa
funcionava, mas atrasava o sinal, não sabia extrapolar durante oclusão e não tinha como dizer
o quanto estava confiando em si mesmo.

### O modelo

Velocidade constante com aceleração desconhecida tratada como ruído branco. É proposital: um
robô da SSL acelera quando quer, e de fora só se veem posições — o comando que ele recebeu
não está em campo nenhum do protocolo. O parâmetro `sigmaAceleracao` é, literalmente, *quanta
aceleração eu admito que aconteceu sem eu ver*.

### Um eixo de cada vez

O filtro natural seria de quatro estados, `[x, y, vx, vy]`. Só que com esse modelo, ruído de
processo igual nos dois eixos e medidas independentes, a matriz 4×4 é exatamente
bloco-diagonal: x nunca influencia y. Rodar dois filtros de 2 estados **não é simplificação, é
a mesma conta** — com matrizes 2×2 escritas à mão, sem biblioteca de álgebra linear e sem
inverter matriz nenhuma.

O mesmo filtro serve para a orientação, com `angular = true`. A única diferença é que a
inovação passa por `Angulo.diferenca`: sem isso, um robô cruzando 180° produziria uma inovação
de quase 2π e o filtro entenderia que ele girou meia volta em um quadro.

### O portão

Nem toda detecção merece ser incorporada. Uma câmera inventa robô onde há reflexo, dois robôs
trocam de id ao se cruzarem, e a bola é confundida com o casaco laranja de alguém na
arquibancada — todos casos reais em competição. O filtro sabe dizer o quanto uma medida o
surpreende: `inovação² / variância da inovação`, somado nos dois eixos, é uma distância de
Mahalanobis que segue qui-quadrado com 2 graus de liberdade. Acima do limite, a medida é
recusada.

**E o portão precisa de válvula de escape.** Sozinho ele é uma armadilha: uma trilha convencida
da posição errada passa a recusar justamente as medidas certas, e nunca mais volta. Por isso
contam-se as recusas seguidas — passou do limite, quem está errado é o filtro, e ele se
reinicializa sobre as medidas novas.

Nessa reinicialização a velocidade **não** volta a zero: é semeada pela diferença entre a
medida atual e a que foi recusada antes. É o que faz um chute — que o modelo de velocidade
constante jamais preveria, porque a bola vai de 0 a 6,5 m/s dentro de um quadro — ser
absorvido em dois quadros já com a velocidade quase certa, em vez de precisar de meio segundo
reconvergindo do zero.

### Sintonia

| | robô | bola |
|---|---:|---:|
| σ aceleração | 3000 mm/s² | 8000 mm/s² |
| σ medida | 12 mm | 10 mm |
| portão (χ², 2 gl) | 16 | 30 |
| recusas até se render | 4 | 2 |

A bola tem portão mais largo e desiste mais rápido porque chute é impulso, não aceleração:
nenhuma sintonia de ruído de processo acompanha isso, então o caminho é reconhecer a mudança
depressa em vez de tentar prevê-la.

### O que o filtro passou a saber

Prever e corrigir são passos separados: a cada quadro **todas** as trilhas são previstas até o
instante do quadro, e só as que aparecem nele são corrigidas. Um robô oculto atrás de outro
continua andando na tela pela velocidade estimada, em vez de congelar no último ponto visto, e
a covariância cresce enquanto ele não volta. Esse crescimento vira o anel tracejado que a
interface desenha: enquanto a visão enxerga, o anel cabe dentro do próprio robô e some; quando
some, o anel cresce e diz que aquela posição virou extrapolação.

Isso também faz a união de várias câmeras funcionar sem nenhum código específico: cada trilha
é corrigida quando alguma câmera a vê e prevista quando nenhuma vê.

## Sensor de bola

O robô de verdade tem uma barreira infravermelha atravessando a boca do dribbler: quando a
bola entra, o feixe corta. Esse bit não existe no protocolo, então é reconstruído por
geometria, no referencial local do robô, onde o teste fica trivial — a boca é um segmento em
`x = 72,5 mm` e basta a bola estar logo à frente dele e dentro da largura útil do rolete.

Duas condições que parecem detalhe e não são: sem a **largura da boca**, uma bola encostada na
lateral da capa contaria como posse; sem a **altura**, uma bola passando por cima contaria
também — e como a visão reporta z do centro, "no chão" é `z <= raio · 2,5`, não `z == 0`.

A área desenhada no campo é **exatamente** a região que o teste usa. Desenhar a própria
condição, em vez de um ícone aceso ao lado do robô, é o que permite ver por que o sensor
disparou ou não: uma bola que passa raspando fica visivelmente fora da faixa.

Verificado contra o simulador, que tem a verdade interna: rodando o cenário
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

Os três painéis conversam só pelo `Cliente`: nenhum tem referência para o outro. A exceção é a
seleção de robô, que precisa mesmo ser compartilhada — clicar no campo acende o cartão, clicar
no cartão acende o robô.

**Topo.** Cada equipe num bloco pintado com a própria cor, nas mesmas cores dos robôs no campo:
a associação nome/cor tem de ser lida de relance, no meio de um jogo. O tempo é o `t_capture`
da visão, ou seja o relógio de quem gera os quadros. Não há placar, de propósito — placar e
estado de jogo vêm do Game Controller, que o simulador ainda não publica, e inventar um zero a
zero seria mostrar dado que ninguém apurou.

**Esquerda.** Portas de escuta e de envio, cor da equipe e nomes, tudo trocável a quente. Numa
bancada troca-se de porta o tempo todo, e fechar o programa para mudar um número seria
insuportável. A validação segue a mesma divisão do simulador: o que dá para saber sem tocar no
sistema (endereço fora da faixa multicast, porta repetida) é recusado na hora em que se digita;
"porta ocupada" só aparece no bind, e aí a configuração anterior é restaurada em vez de deixar
a estratégia surda.

**Centro.** Zoom no scroll, arrasto com o botão direito, clique seleciona robô. Vetores de
velocidade, área do sensor e anel de incerteza podem ser desligados.

**Direita.** Um cartão por robô com ID, velocidade, direção (seta desenhada no ângulo real
mais o valor em graus — a seta sozinha é ambígua em cima de 90°, o número sozinho não se lê de
relance) e o selo do sensor. Robô que sumiu da visão continua listado, apagado, com há quanto
tempo não é visto.

O painel é desenhado em um componente só, e não montado com um `JPanel` por robô: a lista muda
de tamanho a cada quadro e recriar componentes Swing sessenta vezes por segundo custaria mais
do que redesenhar tudo.

## Ainda não existe

**A estratégia.** A saída de comandos está pronta e testada até o fio, mas ninguém produz
comando: sem isso os robôs ficam parados, do mesmo jeito que ficam no simulador quando nenhum
time está conectado. Skills, táticas, planejamento de trajetória e desvio de obstáculo entram
aqui.

Também não existem: Game Controller (placar, faltas, `HALT`/`STOP`/`RUNNING`), associação de
detecção sem `robot_id`, reprocessamento de quadros fora de ordem entre câmeras, e os modos
`MoveGlobalVelocity` e `MoveWheelVelocity` do `RobotControl` — só `MoveLocalVelocity` é
enviado, que é o que bate com o firmware de um robô de verdade.
