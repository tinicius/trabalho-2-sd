package br.cefet.sd.memoria.comum.excecao;

/** Entrada na Sala rejeitada. */
public class EntradaRejeitadaException extends ExcecaoDominio {

    private static final long serialVersionUID = 1L;

    public EntradaRejeitadaException(CodigoErro codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
