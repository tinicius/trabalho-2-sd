package br.cefet.sd.memoria.middleware.nucleo;

/** Ação que a {@code Sala} deve tomar sobre os temporizadores após uma mutação aceita pela {@link Partida}. */
public enum AcaoTemporizador {
    /** Armar o fim do Intervalo_de_Erro (e cancelar a inatividade). */
    INICIAR_INTERVALO_ERRO,
    /** Armar (ou rearmar) o temporizador de inatividade da vez corrente. */
    REARMAR_INATIVIDADE,
    /** A Partida foi concluída: cancelar todos os temporizadores e abrir a coleta de continuar. */
    PARTIDA_CONCLUIDA,
    /** Nenhuma ação sobre temporizadores. */
    NENHUMA
}
