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
| posição e orientação | medido, direto da rede |
| geometria do campo | `SSL_GeometryData`, reenviado a cada 60 quadros |
| **velocidade e ω** | **inferido** a partir de quadros consecutivos |
| **bola no sensor** | **inferido** pela geometria da boca do dribbler |
| tempo de partida | `t_capture` da visão |
| nomes dos times | configuração local (viriam do Game Controller) |

Qualquer número novo na tela ou vem da rede, ou é inferência. E inferência precisa aparecer
como tal.

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

O ingresso no grupo multicast é tentado em **todas** as interfaces que suportam multicast, e
não só na padrão. Numa máquina com Wi-Fi, Ethernet e as interfaces virtuais que Docker e VPN
criam, a rota padrão quase nunca é a que recebe o tráfego. Entrar só nela é o motivo clássico
de "o simulador publica e o cliente não recebe nada".

A conversão de unidade acontece só na borda, em `EmissorDeComandos`. Dentro do programa tudo é
mm e radianos, como a visão entrega; o `ssl_simulation_protocol` fala metros e graus. Errar
essa conversão dá mil vezes de diferença sem estourar exceção em lugar nenhum.

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
porque a associação nome/cor tem de ser lida de relance no meio de um jogo. O tempo é o
`t_capture` da visão, ou seja o relógio de quem gera os quadros. Não há placar: ele viria do
Game Controller, que o simulador ainda não publica, e inventar um zero a zero seria mostrar
dado que ninguém apurou.

**Esquerda.** Estado da rede (para onde escutamos, a que taxa, para onde mandamos), a cor da
equipe e os nomes. As portas ficam atrás do botão *Configurar...*, no mesmo desenho do
simulador: cinco campos ocupavam metade do painel para algo que se mexe uma vez por bancada,
enquanto o que se olha o tempo todo ficava espremido embaixo. Tudo é trocável com a janela
aberta, porque numa bancada troca-se de porta o tempo todo.

A validação segue a mesma divisão do simulador. O que dá para saber sem tocar no sistema
(endereço fora da faixa multicast, porta repetida) é recusado na hora em que se digita. Já
"porta ocupada" só aparece no bind, e nesse caso a configuração anterior é restaurada em vez
de deixar a estratégia surda.

**Centro.** Zoom no scroll, arrasto com o botão direito, clique seleciona robô. Vetores de
velocidade, área do sensor e anel de incerteza podem ser desligados.

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
