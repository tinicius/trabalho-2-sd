#!/usr/bin/env bash
# Inicia o Middleware (RMI Registry + servicos do Jogo da Memoria).
#
# Uso: scripts/rodar_middleware.sh
#
# O Middleware pergunta o IP deste host, a porta do Registry e o tamanho do
# tabuleiro (linhas e colunas) no terminal ao iniciar; nao usa arquivo de
# configuracao nem argumentos.
#
# Variaveis de ambiente (opcionais):
#   JAVA_OPTS      opcoes extras da JVM (ex.: "-Dmemoria.log=build/middleware.log").
set -euo pipefail

# shellcheck source=scripts/_comum.sh
source "$(dirname "${BASH_SOURCE[0]}")/_comum.sh"

garantir_jar "$JAR_MIDDLEWARE"

# shellcheck disable=SC2086  # JAVA_OPTS e separado em palavras de proposito
exec java ${JAVA_OPTS:-} -jar "$JAR_MIDDLEWARE"
