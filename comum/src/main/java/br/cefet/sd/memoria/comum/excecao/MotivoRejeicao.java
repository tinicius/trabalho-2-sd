package br.cefet.sd.memoria.comum.excecao;

/** Motivos pelos quais uma jogada é rejeitada, na ordem de avaliação do validador. */
public enum MotivoRejeicao {
    PARTICIPANTE_INVALIDO("participante inválido"),
    PARTIDA_ANTERIOR_OU_INEXISTENTE("partida anterior ou inexistente"),
    PARTIDA_ENCERRADA("partida encerrada"),
    SESSAO_ENCERRADA("sessão encerrada"),
    FORA_DA_VEZ("fora da vez"),
    INTERVALO_DE_ERRO("intervalo de erro em andamento"),
    POSICAO_INVALIDA("posição inválida"),
    SELECAO_REPETIDA("seleção repetida"),
    CARTA_INDISPONIVEL("carta indisponível"),
    SELECAO_CONFLITANTE("seleção conflitante");

    private final String mensagem;

    MotivoRejeicao(String mensagem) {
        this.mensagem = mensagem;
    }

    /** Mensagem em português apresentável ao usuário. */
    public String getMensagem() {
        return mensagem;
    }
}
