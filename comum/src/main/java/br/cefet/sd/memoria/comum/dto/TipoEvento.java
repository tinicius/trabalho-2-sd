package br.cefet.sd.memoria.comum.dto;

/** Tipo do evento que originou um estado publico da Partida. */
public enum TipoEvento {
    INICIO_PARTIDA,
    CARTA_REVELADA,
    PAR_ENCONTRADO,
    ERRO,
    VEZ_PASSADA,
    INATIVIDADE,
    PARTIDA_CONCLUIDA,
    CONSULTA
}
