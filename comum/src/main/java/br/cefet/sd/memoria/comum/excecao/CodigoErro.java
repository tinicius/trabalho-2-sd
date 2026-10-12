package br.cefet.sd.memoria.comum.excecao;

/** Códigos de erro de domínio transportados por {@link ExcecaoDominio}. */
public enum CodigoErro {
    SALA_CHEIA,
    NOME_INVALIDO,
    NOME_EM_USO,
    PARTIDA_JA_COMECOU,
    SESSAO_EM_ANDAMENTO,
    CALLBACK_INVALIDO,

    PARTICIPANTES_INSUFICIENTES,
    PARTICIPANTE_NAO_REGISTRADO,
    PARTIDA_EM_ANDAMENTO,
    SAIDA_NAO_PERMITIDA_EM_PARTIDA,
    SEM_PERGUNTA_ABERTA,
    JA_RESPONDEU,
    SESSAO_ENCERRADA,

    JOGADA_REJEITADA
}
