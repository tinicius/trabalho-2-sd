package br.cefet.sd.memoria.middleware.sala;

/** Implementação real de {@link Relogio}, apoiada no relógio do sistema. */
public final class RelogioSistema implements Relogio {

    /** Instância compartilhada. */
    public static final RelogioSistema INSTANCIA = new RelogioSistema();

    /** Cria um relógio do sistema. */
    public RelogioSistema() {
    }

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
