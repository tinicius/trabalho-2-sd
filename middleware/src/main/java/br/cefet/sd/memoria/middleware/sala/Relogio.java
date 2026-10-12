package br.cefet.sd.memoria.middleware.sala;

/** Fonte de tempo injetável. */
public interface Relogio {

    /** Instante monotônico em nanossegundos, para medir intervalos (como {@link System#nanoTime()}). */
    long nowNanos();

    /** Instante em milissegundos desde a época Unix (como {@link System#currentTimeMillis()}). */
    long nowMillis();
}
