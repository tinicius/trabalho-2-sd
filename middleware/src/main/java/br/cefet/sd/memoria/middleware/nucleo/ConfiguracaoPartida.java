package br.cefet.sd.memoria.middleware.nucleo;

/** Parâmetros vigentes de uma Partida: Intervalo_de_Erro e configuração do tabuleiro (thread-safe). */
public final class ConfiguracaoPartida {

    /** Duração mínima do Intervalo_de_Erro, em ms. */
    public static final int INTERVALO_ERRO_MIN_MS = 500;

    /** Duração máxima do Intervalo_de_Erro, em ms. */
    public static final int INTERVALO_ERRO_MAX_MS = 10_000;

    /** Duração padrão do Intervalo_de_Erro, em ms. */
    public static final int INTERVALO_ERRO_PADRAO_MS = 2000;

    /**
     * Resultado de uma tentativa de alterar o Intervalo_de_Erro.
     *
     * @param aceito        {@code true} se o valor proposto foi adotado
     * @param valorProposto valor proposto, em ms
     * @param valorVigente  duração em vigor após a tentativa, em ms
     */
    public record ResultadoIntervalo(boolean aceito, int valorProposto, int valorVigente) {
    }

    private volatile int intervaloErroMs = INTERVALO_ERRO_PADRAO_MS;
    private volatile ConfiguracaoTabuleiro tabuleiro = ConfiguracaoTabuleiro.PADRAO;

    /** Informa se o valor (ms) é aceitável como Intervalo_de_Erro (500 a 10000). */
    public static boolean intervaloErroValido(int valorMs) {
        return valorMs >= INTERVALO_ERRO_MIN_MS && valorMs <= INTERVALO_ERRO_MAX_MS;
    }

    /** Duração vigente do Intervalo_de_Erro, em ms. */
    public int intervaloErroMs() {
        return intervaloErroMs;
    }

    /** Tenta alterar o Intervalo_de_Erro; fora da faixa válida, mantém o valor anterior. */
    public synchronized ResultadoIntervalo tentarAlterarIntervaloErro(int valorMs) {
        if (!intervaloErroValido(valorMs)) {
            return new ResultadoIntervalo(false, valorMs, intervaloErroMs);
        }
        intervaloErroMs = valorMs;
        return new ResultadoIntervalo(true, valorMs, valorMs);
    }

    /** Configuração vigente do tabuleiro. */
    public ConfiguracaoTabuleiro tabuleiro() {
        return tabuleiro;
    }

    /** Tenta substituir o tabuleiro; só adota se {@link ConfiguracaoTabuleiro#validar(int, int, int)} aceitar. */
    public synchronized ConfiguracaoTabuleiro.Resultado tentarAlterarTabuleiro(
            int linhas, int colunas, int numeroDeImagens) {
        ConfiguracaoTabuleiro.Resultado resultado =
                ConfiguracaoTabuleiro.validar(linhas, colunas, numeroDeImagens);
        if (resultado.foiAceito()) {
            tabuleiro = new ConfiguracaoTabuleiro(linhas, colunas);
        }
        return resultado;
    }
}
