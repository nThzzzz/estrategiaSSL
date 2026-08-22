#!/usr/bin/env bash
# Baixa os .proto oficiais da SSL e gera o Java correspondente.
#
# Roda so quando o protocolo muda; o Java gerado fica commitado para que o build
# normal continue sendo uma unica linha de javac, sem Gradle nem rede.
#
# Sao TRES familias, de tres repositorios diferentes, e elas nao podem ser
# compiladas juntas: ssl-vision e ssl-simulation-protocol declaram as mesmas
# mensagens (SSL_DetectionBall, SSL_GeometryData...), e ssl-simulation-protocol e
# ssl-game-controller declaram os mesmos Team e RobotId. Nenhuma delas declara
# "package". Compilar tudo de uma vez daria simbolo duplicado, e gerar no mesmo
# pacote Java daria colisao de classe. Por isso: um java_package por familia e uma
# invocacao separada do protoc para cada.
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

VISION=https://raw.githubusercontent.com/RoboCup-SSL/ssl-vision/master/src/shared/proto
SIM=https://raw.githubusercontent.com/RoboCup-SSL/ssl-simulation-protocol/master/proto
GC=https://raw.githubusercontent.com/RoboCup-SSL/ssl-game-controller/master/proto

baixar() { # url destino pacote_java
  mkdir -p "$(dirname "$2")"
  curl -sSfL --max-time 30 -o "$2" "$1"
  # Insere o java_package logo apos a linha de syntax. Sem isso o protoc gera
  # tudo no pacote default do Java, que nao pode ser importado.
  if ! grep -q "java_package" "$2"; then
    sed -i '' "s|^syntax = \"proto2\";|syntax = \"proto2\";\noption java_package = \"$3\";|" "$2"
  fi
}

echo "baixando protos da ssl-vision..."
for f in messages_robocup_ssl_wrapper messages_robocup_ssl_detection messages_robocup_ssl_geometry; do
  baixar "$VISION/$f.proto" "proto/vision/$f.proto" "proto.vision"
done

echo "baixando protos da ssl-simulation-protocol..."
for f in ssl_simulation_control ssl_simulation_config ssl_simulation_error \
         ssl_simulation_robot_control ssl_gc_common ssl_vision_geometry ssl_vision_detection; do
  baixar "$SIM/$f.proto" "proto/sim/$f.proto" "proto.sim"
done

# O arbitro mantem a arvore de diretorios do repositorio de origem porque os
# imports dentro dos .proto sao relativos a ela ("state/...", "geom/...").
echo "baixando protos do ssl-game-controller..."
for f in state/ssl_gc_referee_message state/ssl_gc_game_event state/ssl_gc_common \
         geom/ssl_gc_geometry; do
  baixar "$GC/$f.proto" "proto/gc/$f.proto" "proto.gc"
done

echo "gerando Java..."
rm -rf src/proto
mkdir -p src/proto
protoc -I proto/vision --java_out=src proto/vision/*.proto
protoc -I proto/sim    --java_out=src proto/sim/*.proto
protoc -I proto/gc     --java_out=src proto/gc/state/*.proto proto/gc/geom/*.proto

echo "gerado:"
find src/proto -name "*.java" | wc -l | xargs printf "  %s arquivos\n"
du -sh src/proto | awk '{printf "  %s\n", $1}'
