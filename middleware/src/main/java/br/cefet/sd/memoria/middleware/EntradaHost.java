package br.cefet.sd.memoria.middleware;

import br.cefet.sd.memoria.middleware.config.CarregadorConfiguracao;
import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoTabuleiro;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Optional;

/** Pergunta ao operador, no terminal, o host RMI, a porta do Registry e as dimensões do tabuleiro. */
final class EntradaHost {

    private EntradaHost() {
    }

    /** Solicita o host até receber um valor válido; vazio se a entrada terminar (EOF). */
    static Optional<String> solicitar(BufferedReader entrada, PrintStream saida, String sugestao)
            throws IOException {
        boolean temSugestao = CarregadorConfiguracao.hostValido(sugestao);
        while (true) {
            saida.print(temSugestao
                    ? "Informe o IP deste host (Enter para " + sugestao + "): "
                    : "Informe o IP deste host: ");
            saida.flush();
            String linha = entrada.readLine();
            if (linha == null) {
                saida.println();
                return Optional.empty();
            }
            String host = linha.trim();
            if (host.isEmpty() && temSugestao) {
                return Optional.of(sugestao);
            }
            if (CarregadorConfiguracao.hostValido(host)) {
                return Optional.of(host);
            }
            saida.println("Host/IP inválido: '" + host + "'. Use apenas letras, dígitos e . : - _");
        }
    }

    /** Solicita a porta do RMI Registry (1 a 65535); vazio se a entrada terminar (EOF). */
    static Optional<Integer> solicitarPorta(BufferedReader entrada, PrintStream saida, int padrao)
            throws IOException {
        while (true) {
            saida.print("Informe a porta do Registry (Enter para " + padrao + "): ");
            saida.flush();
            String linha = entrada.readLine();
            if (linha == null) {
                saida.println();
                return Optional.empty();
            }
            String texto = linha.trim();
            if (texto.isEmpty()) {
                return Optional.of(padrao);
            }
            try {
                int porta = Integer.parseInt(texto);
                if (porta >= 1 && porta <= 65535) {
                    return Optional.of(porta);
                }
            } catch (NumberFormatException e) {
                // cai na mensagem abaixo
            }
            saida.println("Porta inválida: '" + texto + "'. Use um inteiro de 1 a 65535.");
        }
    }

    /**
     * Solicita linhas e colunas até receber uma combinação aceita por
     * {@link ConfiguracaoTabuleiro#validar(int, int, int)}; vazio se a entrada terminar (EOF).
     */
    static Optional<ConfiguracaoTabuleiro> solicitarTabuleiro(BufferedReader entrada, PrintStream saida,
            ConfiguracaoTabuleiro padrao, int numeroDeImagens) throws IOException {
        while (true) {
            Optional<Integer> linhas = solicitarInteiroPositivo(entrada, saida,
                    "Informe o número de linhas do tabuleiro (Enter para " + padrao.linhas() + "): ",
                    padrao.linhas());
            if (linhas.isEmpty()) {
                return Optional.empty();
            }
            Optional<Integer> colunas = solicitarInteiroPositivo(entrada, saida,
                    "Informe o número de colunas do tabuleiro (Enter para " + padrao.colunas() + "): ",
                    padrao.colunas());
            if (colunas.isEmpty()) {
                return Optional.empty();
            }
            ConfiguracaoTabuleiro.Resultado r =
                    ConfiguracaoTabuleiro.validar(linhas.get(), colunas.get(), numeroDeImagens);
            if (r.foiAceito()) {
                return Optional.of(new ConfiguracaoTabuleiro(linhas.get(), colunas.get()));
            }
            long total = (long) linhas.get() * colunas.get();
            if (r.causa().get() == ConfiguracaoTabuleiro.Causa.NUMERO_DE_POSICOES_INVALIDO) {
                saida.println("Tabuleiro inválido: " + linhas.get() + " x " + colunas.get() + " = " + total
                        + " posições; o total deve ser par e no mínimo "
                        + ConfiguracaoTabuleiro.MINIMO_DE_POSICOES + ".");
            } else {
                saida.println("Tabuleiro inválido: " + linhas.get() + " x " + colunas.get() + " = " + total
                        + " posições exige " + total / 2 + " imagens, mas o catálogo tem "
                        + numeroDeImagens + " (máximo de " + 2L * numeroDeImagens + " posições).");
            }
        }
    }

    private static Optional<Integer> solicitarInteiroPositivo(BufferedReader entrada, PrintStream saida,
            String prompt, int padrao) throws IOException {
        while (true) {
            saida.print(prompt);
            saida.flush();
            String linha = entrada.readLine();
            if (linha == null) {
                saida.println();
                return Optional.empty();
            }
            String texto = linha.trim();
            if (texto.isEmpty()) {
                return Optional.of(padrao);
            }
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= 1) {
                    return Optional.of(valor);
                }
            } catch (NumberFormatException e) {
                // cai na mensagem abaixo
            }
            saida.println("Valor inválido: '" + texto + "'. Use um inteiro maior ou igual a 1.");
        }
    }

    /** Detecta o endereço local de saída da máquina sem enviar pacotes; {@code null} se falhar. */
    static String detectarEnderecoLocal() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("203.0.113.1"), 9);
            InetAddress local = socket.getLocalAddress();
            if (local != null && !local.isAnyLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (IOException | RuntimeException e) {
            // cai no próximo método
        }
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return host == null || host.isEmpty() ? null : host;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
