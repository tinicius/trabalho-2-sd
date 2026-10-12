package br.cefet.sd.memoria.middleware.sala;

/** Gancho de auditoria/log da {@link Sala}; deve ser rápido, sem I/O de rede e sem lançar exceção. */
@FunctionalInterface
public interface RegistroEventosSala {

    /** Tipos de linha de log. */
    enum Tipo {
        ENTRADA, SAIDA, PRONTO, INICIO_PARTIDA, JOGADA, TROCA_DE_TURNO, RESULTADO, DESCONEXAO,
        CANCELAMENTO, CONTINUAR, SUMARIO, CONFIG
    }

    /** Implementação nula: descarta todos os registros. */
    RegistroEventosSala NENHUM = (tipo, ator, resultado, detalhes) -> { };

    /** Registra um evento; {@code ator} e {@code detalhes} podem ser {@code null}. */
    void registrar(Tipo tipo, String ator, String resultado, String detalhes);

    /** Registra uma exceção inesperada com a pilha completa; por padrão descarta o registro. */
    default void registrarErro(String contexto, Throwable t) {
    }
}
