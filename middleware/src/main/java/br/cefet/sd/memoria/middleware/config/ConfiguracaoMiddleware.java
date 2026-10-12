package br.cefet.sd.memoria.middleware.config;

import br.cefet.sd.memoria.comum.catalogo.CatalogoImagens;
import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoTabuleiro;
import br.cefet.sd.memoria.middleware.sala.ConfiguracaoSala;
import java.time.Duration;

/**
 * Configuração imutável do processo Middleware, normalmente produzida por
 * {@link CarregadorConfiguracao}.
 *
 * @param registryPorta      porta do RMI Registry (1 a 65535)
 * @param rmiHostname        host/IP anunciado nos stubs; vazio = não definido
 * @param rmiPortaObjetos    porta de exportação dos objetos remotos; 0 = anônima
 * @param salaCapacidade     capacidade da Sala (2 a 8)
 * @param linhas             linhas do tabuleiro
 * @param colunas            colunas do tabuleiro
 * @param intervaloErroMs    Intervalo_de_Erro em ms (500 a 10000)
 * @param inatividadeSeg     limite de inatividade da vez, em segundos
 * @param prazoContinuarSeg  prazo para responder "continuar", em segundos
 * @param callbackTimeoutSeg timeout de entrega de Callback, em segundos (1 a 60)
 */
public record ConfiguracaoMiddleware(
        int registryPorta,
        String rmiHostname,
        int rmiPortaObjetos,
        int salaCapacidade,
        int linhas,
        int colunas,
        int intervaloErroMs,
        int inatividadeSeg,
        int prazoContinuarSeg,
        int callbackTimeoutSeg) {

    /** Porta padrão do RMI Registry. */
    public static final int REGISTRY_PORTA_PADRAO = 1099;
    /** Timeout padrão de Callback, em segundos. */
    public static final int CALLBACK_TIMEOUT_PADRAO_SEG = 5;
    /** Inatividade padrão da vez, em segundos. */
    public static final int INATIVIDADE_PADRAO_SEG = 60;
    /** Prazo padrão de "continuar", em segundos. */
    public static final int PRAZO_CONTINUAR_PADRAO_SEG = 30;
    /** Timeout de verificação de conectividade na entrada (não configurável), em segundos. */
    private static final int TIMEOUT_PING_SEG = 5;

    /** Normaliza {@code rmiHostname} nulo para vazio. */
    public ConfiguracaoMiddleware {
        rmiHostname = rmiHostname == null ? "" : rmiHostname;
    }

    /** Configuração com todos os padrões embutidos. */
    public static ConfiguracaoMiddleware padrao() {
        return new ConfiguracaoMiddleware(REGISTRY_PORTA_PADRAO, "", 0, ConfiguracaoSala.CAPACIDADE_PADRAO,
                ConfiguracaoTabuleiro.PADRAO.linhas(), ConfiguracaoTabuleiro.PADRAO.colunas(),
                2000, INATIVIDADE_PADRAO_SEG, PRAZO_CONTINUAR_PADRAO_SEG, CALLBACK_TIMEOUT_PADRAO_SEG);
    }

    /** Cria a configuração da Sala usando o catálogo de imagens do classpath. */
    public ConfiguracaoSala paraSala() {
        return paraSala(CatalogoImagens.carregar());
    }

    /** Cria a configuração da Sala com o catálogo informado. */
    public ConfiguracaoSala paraSala(CatalogoImagens catalogo) {
        long intervalo = intervaloErroMs;
        return new ConfiguracaoSala(
                salaCapacidade,
                Duration.ofSeconds(callbackTimeoutSeg),
                Duration.ofSeconds(TIMEOUT_PING_SEG),
                Duration.ofSeconds(prazoContinuarSeg),
                Duration.ofSeconds(inatividadeSeg),
                () -> Duration.ofMillis(intervalo),
                new ConfiguracaoTabuleiro(linhas, colunas),
                catalogo,
                null);
    }

    /** Produz o texto desta configuração no formato {@code .properties}. */
    public String formatar() {
        return "# Configuracao do Middleware\n"
                + CarregadorConfiguracao.REGISTRY_PORTA + "=" + registryPorta + "\n"
                + CarregadorConfiguracao.RMI_HOSTNAME + "=" + rmiHostname + "\n"
                + CarregadorConfiguracao.RMI_PORTA_OBJETOS + "=" + rmiPortaObjetos + "\n"
                + CarregadorConfiguracao.SALA_CAPACIDADE + "=" + salaCapacidade + "\n"
                + CarregadorConfiguracao.TABULEIRO_LINHAS + "=" + linhas + "\n"
                + CarregadorConfiguracao.TABULEIRO_COLUNAS + "=" + colunas + "\n"
                + CarregadorConfiguracao.INTERVALO_ERRO_MS + "=" + intervaloErroMs + "\n"
                + CarregadorConfiguracao.INATIVIDADE_SEG + "=" + inatividadeSeg + "\n"
                + CarregadorConfiguracao.PRAZO_CONTINUAR_SEG + "=" + prazoContinuarSeg + "\n"
                + CarregadorConfiguracao.CALLBACK_TIMEOUT_SEG + "=" + callbackTimeoutSeg + "\n";
    }
}
