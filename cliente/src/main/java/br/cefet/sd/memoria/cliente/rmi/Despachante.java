package br.cefet.sd.memoria.cliente.rmi;

import javax.swing.SwingUtilities;

/** Agenda uma tarefa para execução fora da thread RMI, sem bloquear o chamador. */
@FunctionalInterface
public interface Despachante {

    /** Despachante padrão: enfileira a tarefa na EDT com {@link SwingUtilities#invokeLater}. */
    Despachante EDT = SwingUtilities::invokeLater;

    /** Agenda a tarefa e retorna imediatamente. */
    void despachar(Runnable tarefa);
}
