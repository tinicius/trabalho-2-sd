package br.cefet.sd.memoria.comum.remoto;
/** Nomes dos serviços remotos registrados no RMI Registry. */
public final class NomesServico {
    /** Nome do {@link ServicoSala} no Registry. */
    public static final String SALA = "memoria/Sala";
    /** Nome do {@link ServicoPartida} no Registry. */
    public static final String PARTIDA = "memoria/Partida";
    /** Nome do {@link ServicoSessao} no Registry. */
    public static final String SESSAO = "memoria/Sessao";
    private NomesServico() {
    }
}
