#!/usr/bin/env bash
# Funcoes compartilhadas pelos scripts de execucao (use com "source").
# Define RAIZ (raiz do projeto, resolvida a partir da localizacao do script)
# e muda o diretorio de trabalho para ela, de modo que config/*.properties
# e os caminhos relativos funcionem a partir de qualquer cwd.

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

JAR_MIDDLEWARE="$RAIZ/middleware/target/middleware-all.jar"
JAR_CLIENTE="$RAIZ/cliente/target/cliente-all.jar"

erro() {
    echo "erro: $*" >&2
}

# garantir_jar CAMINHO: compila o projeto se o jar nao existir.
garantir_jar() {
    local jar="$1"
    if [ -f "$jar" ]; then
        return 0
    fi
    if ! command -v mvn >/dev/null 2>&1; then
        erro "$jar nao encontrado e o Maven (mvn) nao esta no PATH. Rode 'mvn -q clean package' na raiz do projeto."
        exit 1
    fi
    echo "Jar ausente ($jar); compilando com 'mvn -q package'..." >&2
    mvn -q package
    if [ ! -f "$jar" ]; then
        erro "o build terminou, mas $jar nao foi gerado."
        exit 1
    fi
}

# porta_aberta HOST PORTA: sucesso se ha um servico escutando.
porta_aberta() {
    (exec 3<>"/dev/tcp/$1/$2") 2>/dev/null
}
