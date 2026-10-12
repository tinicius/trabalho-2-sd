#!/usr/bin/env bash
# Abre um Cliente (GUI Swing) do Jogo da Memoria. Um processo por Jogador.
#
# Uso: scripts/rodar_cliente.sh
#
# Nao ha arquivo de configuracao nem argumentos: nome, host, porta, timeout e o
# IP deste computador (callback, "Meu IP") sao informados na tela inicial.
#
# Variavel de ambiente (opcional):
#   JAVA_OPTS        opcoes extras da JVM.
set -euo pipefail

# shellcheck source=scripts/_comum.sh
source "$(dirname "${BASH_SOURCE[0]}")/_comum.sh"

garantir_jar "$JAR_CLIENTE"

# shellcheck disable=SC2086  # JAVA_OPTS e separado em palavras de proposito
exec java ${JAVA_OPTS:-} -jar "$JAR_CLIENTE"
