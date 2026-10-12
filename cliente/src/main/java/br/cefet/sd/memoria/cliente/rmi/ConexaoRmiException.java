package br.cefet.sd.memoria.cliente.rmi;

/** Falha ao localizar os serviços do Middleware no Registry. */
public class ConexaoRmiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String host;
    private final int porta;

    /** Cria a exceção. */
    public ConexaoRmiException(String host, int porta, String motivo, Throwable causa) {
        super("Não foi possível conectar ao Middleware em " + host + ":" + porta + ": " + motivo, causa);
        this.host = host;
        this.porta = porta;
    }

    /** Host do Registry. */
    public String getHost() {
        return host;
    }

    /** Porta do Registry. */
    public int getPorta() {
        return porta;
    }
}
