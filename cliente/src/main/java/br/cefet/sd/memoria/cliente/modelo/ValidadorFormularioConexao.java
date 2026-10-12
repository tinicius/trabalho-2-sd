package br.cefet.sd.memoria.cliente.modelo;

import java.util.Objects;

/** Valida os campos da tela de conexão. */
public final class ValidadorFormularioConexao {

    public static final int NOME_MIN = 1;
    public static final int NOME_MAX = 20;
    public static final int HOST_MIN = 1;
    public static final int HOST_MAX = 255;
    public static final int PORTA_MIN = 1;
    public static final int PORTA_MAX = 65535;
    public static final int TIMEOUT_MIN_SEG = 1;
    public static final int TIMEOUT_MAX_SEG = 600;
    public static final int RMI_HOSTNAME_MAX = 255;

    /** Campo do formulário de conexão. */
    public enum Campo {
        NOME("nome"),
        HOST("host"),
        PORTA("porta"),
        TIMEOUT("timeout"),
        RMI_HOSTNAME("endereço deste computador");

        private final String rotulo;

        Campo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    /** Resultado imutável da validação. */
    public static final class Resultado {
        private static final Resultado VALIDO = new Resultado(null, "");

        private final Campo campoInvalido;
        private final String mensagem;

        private Resultado(Campo campoInvalido, String mensagem) {
            this.campoInvalido = campoInvalido;
            this.mensagem = mensagem;
        }

        static Resultado ok() {
            return VALIDO;
        }

        static Resultado falha(Campo campo, String mensagem) {
            return new Resultado(Objects.requireNonNull(campo), Objects.requireNonNull(mensagem));
        }

        public boolean valido() {
            return campoInvalido == null;
        }

        /** Primeiro campo inválido, ou {@code null} quando o formulário é válido. */
        public Campo campoInvalido() {
            return campoInvalido;
        }

        /** Mensagem em português indicando o campo inválido; vazia quando válido. */
        public String mensagem() {
            return mensagem;
        }

        @Override
        public String toString() {
            return valido() ? "Resultado[valido]" : "Resultado[" + campoInvalido + ": " + mensagem + "]";
        }
    }

    private ValidadorFormularioConexao() {
    }

    /** Valida os campos, na ordem nome, host, porta, timeout, endereço deste computador. */
    public static Resultado validar(String nome, String host, String porta, String timeoutSeg,
                                    String rmiHostname) {
        if (!tamanhoValido(nome, NOME_MIN, NOME_MAX)) {
            return Resultado.falha(Campo.NOME,
                    "Nome inválido: informe de " + NOME_MIN + " a " + NOME_MAX + " caracteres.");
        }
        if (!tamanhoValido(host, HOST_MIN, HOST_MAX)) {
            return Resultado.falha(Campo.HOST,
                    "Host inválido: informe de " + HOST_MIN + " a " + HOST_MAX + " caracteres.");
        }
        if (!inteiroNaFaixa(porta, PORTA_MIN, PORTA_MAX)) {
            return Resultado.falha(Campo.PORTA,
                    "Porta inválida: informe um número inteiro de " + PORTA_MIN + " a " + PORTA_MAX + ".");
        }
        if (!inteiroNaFaixa(timeoutSeg, TIMEOUT_MIN_SEG, TIMEOUT_MAX_SEG)) {
            return Resultado.falha(Campo.TIMEOUT,
                    "Timeout inválido: informe um número inteiro de " + TIMEOUT_MIN_SEG + " a "
                            + TIMEOUT_MAX_SEG + " segundos.");
        }
        if (!rmiHostnameValido(rmiHostname)) {
            return Resultado.falha(Campo.RMI_HOSTNAME,
                    "Endereço deste computador inválido: deixe em branco ou informe até "
                            + RMI_HOSTNAME_MAX + " caracteres, sem espaços.");
        }
        return Resultado.ok();
    }

    private static boolean rmiHostnameValido(String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        String aparado = texto.trim();
        if (aparado.length() > RMI_HOSTNAME_MAX) {
            return false;
        }
        for (int i = 0; i < aparado.length(); i++) {
            if (Character.isWhitespace(aparado.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean tamanhoValido(String texto, int min, int max) {
        if (texto == null) {
            return false;
        }
        int tamanho = texto.trim().length();
        return tamanho >= min && tamanho <= max;
    }

    private static boolean inteiroNaFaixa(String numero, int min, int max) {
        if (numero == null) {
            return false;
        }
        String texto = numero.trim();
        // No máximo 5 dígitos evita estouro de int; só dígitos ASCII são aceitos.
        if (texto.isEmpty() || texto.length() > 5) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        int valor = Integer.parseInt(texto);
        return valor >= min && valor <= max;
    }
}
