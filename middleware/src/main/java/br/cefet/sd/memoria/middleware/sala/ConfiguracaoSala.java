package br.cefet.sd.memoria.middleware.sala;

import br.cefet.sd.memoria.comum.catalogo.CatalogoImagens;
import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoPartida;
import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoTabuleiro;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Parâmetros imutáveis de uma {@link Sala}.
 *
 * @param capacidade      número máximo de Jogadores (2 a 8)
 * @param timeoutCallback tempo máximo de cada entrega de Callback
 * @param timeoutPing     tempo máximo da verificação de conectividade na entrada
 * @param prazoContinuar  prazo para responder "continuar"
 * @param inatividade     tempo máximo de uma vez sem jogada
 * @param intervaloErro   fornecedor da duração do Intervalo_de_Erro, lido a cada agendamento
 * @param tabuleiro       dimensões do tabuleiro, compatíveis com o catálogo
 * @param catalogo        catálogo de imagens
 * @param random          fonte de aleatoriedade do sorteio da matriz; {@code null} = produção
 */
public record ConfiguracaoSala(
        int capacidade,
        Duration timeoutCallback,
        Duration timeoutPing,
        Duration prazoContinuar,
        Duration inatividade,
        Supplier<Duration> intervaloErro,
        ConfiguracaoTabuleiro tabuleiro,
        CatalogoImagens catalogo,
        RandomGenerator random) {

    /** Capacidade mínima da Sala. */
    public static final int CAPACIDADE_MIN = 2;
    /** Capacidade máxima da Sala. */
    public static final int CAPACIDADE_MAX = 8;
    /** Capacidade padrão da Sala. */
    public static final int CAPACIDADE_PADRAO = 4;

    /** Valida os parâmetros. */
    public ConfiguracaoSala {
        if (capacidade < CAPACIDADE_MIN || capacidade > CAPACIDADE_MAX) {
            throw new IllegalArgumentException(
                    "capacidade deve estar entre " + CAPACIDADE_MIN + " e " + CAPACIDADE_MAX + ": " + capacidade);
        }
        exigirPositiva(timeoutCallback, "timeoutCallback");
        exigirPositiva(timeoutPing, "timeoutPing");
        exigirPositiva(prazoContinuar, "prazoContinuar");
        exigirPositiva(inatividade, "inatividade");
        Objects.requireNonNull(intervaloErro, "intervaloErro");
        Objects.requireNonNull(tabuleiro, "tabuleiro");
        Objects.requireNonNull(catalogo, "catalogo");
        ConfiguracaoTabuleiro.Resultado r =
                ConfiguracaoTabuleiro.validar(tabuleiro.linhas(), tabuleiro.colunas(), catalogo.quantidade());
        if (!r.foiAceito()) {
            throw new IllegalArgumentException("tabuleiro incompatível com o catálogo: " + r.causa().get());
        }
    }

    /** Configuração padrão, com o catálogo de imagens do classpath. */
    public static ConfiguracaoSala padrao() {
        return new ConfiguracaoSala(CAPACIDADE_PADRAO, Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(60),
                () -> Duration.ofMillis(ConfiguracaoPartida.INTERVALO_ERRO_PADRAO_MS),
                ConfiguracaoTabuleiro.PADRAO, CatalogoImagens.carregar(), null);
    }

    /** Cópia com outra capacidade. */
    public ConfiguracaoSala comCapacidade(int nova) {
        return new ConfiguracaoSala(nova, timeoutCallback, timeoutPing, prazoContinuar, inatividade,
                intervaloErro, tabuleiro, catalogo, random);
    }

    /** Cópia com outro timeout de entrega de Callback. */
    public ConfiguracaoSala comTimeoutCallback(Duration novo) {
        return new ConfiguracaoSala(capacidade, novo, timeoutPing, prazoContinuar, inatividade,
                intervaloErro, tabuleiro, catalogo, random);
    }

    /** Cópia com outro timeout de verificação de conectividade (ping) na entrada. */
    public ConfiguracaoSala comTimeoutPing(Duration novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, novo, prazoContinuar, inatividade,
                intervaloErro, tabuleiro, catalogo, random);
    }

    /** Cópia com outro prazo de resposta de "continuar". */
    public ConfiguracaoSala comPrazoContinuar(Duration novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, novo, inatividade,
                intervaloErro, tabuleiro, catalogo, random);
    }

    /** Cópia com outro limite de inatividade da vez. */
    public ConfiguracaoSala comInatividade(Duration novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, prazoContinuar, novo,
                intervaloErro, tabuleiro, catalogo, random);
    }

    /** Cópia com outro fornecedor da duração do Intervalo_de_Erro. */
    public ConfiguracaoSala comIntervaloErro(Supplier<Duration> novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, prazoContinuar, inatividade,
                novo, tabuleiro, catalogo, random);
    }

    /** Cópia com outro tabuleiro. */
    public ConfiguracaoSala comTabuleiro(ConfiguracaoTabuleiro novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, prazoContinuar, inatividade,
                intervaloErro, novo, catalogo, random);
    }

    /** Cópia com outro catálogo de imagens. */
    public ConfiguracaoSala comCatalogo(CatalogoImagens novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, prazoContinuar, inatividade,
                intervaloErro, tabuleiro, novo, random);
    }

    /** Cópia com outra fonte de aleatoriedade ({@code null} = produção). */
    public ConfiguracaoSala comRandom(RandomGenerator novo) {
        return new ConfiguracaoSala(capacidade, timeoutCallback, timeoutPing, prazoContinuar, inatividade,
                intervaloErro, tabuleiro, catalogo, novo);
    }

    private static void exigirPositiva(Duration d, String nome) {
        Objects.requireNonNull(d, nome);
        if (d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException(nome + " deve ser positivo: " + d);
        }
    }
}
