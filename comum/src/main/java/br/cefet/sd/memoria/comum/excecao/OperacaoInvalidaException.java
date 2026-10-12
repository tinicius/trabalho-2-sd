package br.cefet.sd.memoria.comum.excecao;

/** Operação inválida no estado atual. */
public class OperacaoInvalidaException extends ExcecaoDominio {

    private static final long serialVersionUID = 1L;

    public OperacaoInvalidaException(CodigoErro codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
