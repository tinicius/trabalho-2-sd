package br.cefet.sd.memoria.middleware.sala;

import java.time.Duration;

/** Agenda a execução única de uma tarefa após um atraso. */
public interface Agendador {

    /** Agenda {@code tarefa} para rodar uma vez após {@code atraso}, retornando a alça de cancelamento. */
    Cancelavel agendar(Duration atraso, Runnable tarefa);
}
