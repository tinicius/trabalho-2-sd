package br.cefet.sd.memoria.comum.excecao;

import java.util.Objects;

/** Exceção checked de erros de domínio, transportada via RMI. */
public class ExcecaoDominio extends Exception {

    private static final long serialVersionUID = 1L;

    private final CodigoErro codigo;

    public ExcecaoDominio(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}
