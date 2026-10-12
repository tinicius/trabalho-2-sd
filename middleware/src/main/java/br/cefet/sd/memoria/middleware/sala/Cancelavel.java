package br.cefet.sd.memoria.middleware.sala;

/** Alça de uma tarefa agendada por um {@link Agendador}, que permite cancelá-la. */
@FunctionalInterface
public interface Cancelavel {

    /** Cancela a tarefa se ainda não começou; retorna {@code true} se impediu a execução. */
    boolean cancel();
}
