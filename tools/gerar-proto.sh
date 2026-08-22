#!/usr/bin/env bash
# Baixa os .proto oficiais da SSL e gera o Java correspondente.
#
# Roda so quando o protocolo muda; o Java gerado fica commitado para que o build
# normal continue sendo uma unica linha de javac, sem Gradle nem rede.
#
# As duas familias de .proto (ssl-vision e ssl-simulation-protocol) declaram as
# MESMAS mensagens (SSL_DetectionBall, SSL_GeometryData...) e nenhuma delas
# declara "package". Compilar as duas juntas da erro de simbolo duplicado, e
# gerar as duas no mesmo pacote Java daria colisao de classe. Por isso: dois
# java_package distintos e duas invocacoes separadas do protoc.
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

VISION=https://raw.githubusercontent.com/RoboCup-SSL/ssl-vision/master/src/shared/proto
SIM=https://raw.githubusercontent.com/RoboCup-SSL/ssl-simulation-protocol/master/proto

baixar() { # url destino pacote_java
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

echo "gerando Java..."
rm -rf src/proto
mkdir -p src/proto
protoc -I proto/vision --java_out=src proto/vision/*.proto
protoc -I proto/sim    --java_out=src proto/sim/*.proto

echo "gerado:"
find src/proto -name "*.java" | wc -l | xargs printf "  %s arquivos\n"
du -sh src/proto | awk '{printf "  %s\n", $1}'
