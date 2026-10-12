package br.cefet.sd.memoria.middleware.servico;

/** Falha ao publicar o RMI Registry ou os serviços remotos. */
public final class PublicacaoRmiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int porta;
    private final String motivo;

    /** Cria a exceção com a porta do Registry, o motivo e a causa original (pode ser nula). */
    public PublicacaoRmiException(int porta, String motivo, Throwable causa) {
        super("porta " + porta + ": " + motivo, causa);
        this.porta = porta;
        this.motivo = motivo;
    }

    /** Motivo resumido da falha (sem o prefixo da porta). */
    public String motivo() {
        return motivo;
    }

    /** Porta do Registry envolvida na falha. */
    public int porta() {
        return porta;
    }
}
