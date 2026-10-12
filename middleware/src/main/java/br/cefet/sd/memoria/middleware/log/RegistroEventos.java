package br.cefet.sd.memoria.middleware.log;

import br.cefet.sd.memoria.middleware.sala.RegistroEventosSala;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * Registro de eventos do Middleware baseado em {@code java.util.logging}: uma linha por evento
 * (ver {@link FormatadorLinha}), no console e, opcionalmente, em arquivo. Nunca lança exceção.
 */
public final class RegistroEventos implements RegistroEventosSala, AutoCloseable {

    private static final AtomicInteger SEQUENCIA_INSTANCIAS = new AtomicInteger();

    private final Logger logger;
    private final Handler console;
    private final Handler arquivo;
    private volatile boolean fechado;

    private RegistroEventos(Handler console, Handler arquivo) {
        this.logger = Logger.getLogger(RegistroEventos.class.getName() + "#"
                + SEQUENCIA_INSTANCIAS.incrementAndGet());
        this.logger.setUseParentHandlers(false);
        this.logger.setLevel(Level.ALL);
        this.console = console;
        this.arquivo = arquivo;
        this.logger.addHandler(console);
        if (arquivo != null) {
            this.logger.addHandler(arquivo);
        }
    }

    /** Cria um registro que escreve no console e, se informado, também em arquivo (UTF-8, append). */
    public static RegistroEventos criar(Path arquivoOpcional) {
        FormatadorLinha formatador = new FormatadorLinha();
        Handler console = new ConsoleSaidaPadrao(formatador);
        Handler arquivo = null;
        if (arquivoOpcional != null) {
            try {
                arquivo = abrirArquivo(arquivoOpcional, formatador);
            } catch (IOException | RuntimeException e) {
                System.err.println("AVISO: não foi possível abrir o arquivo de log '" + arquivoOpcional
                        + "'; o log continuará apenas no console (" + e + ").");
            }
        }
        return new RegistroEventos(console, arquivo);
    }

    private static Handler abrirArquivo(Path caminho, FormatadorLinha formatador) throws IOException {
        Path pai = caminho.toAbsolutePath().getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        OutputStream saida = new BufferedOutputStream(Files.newOutputStream(caminho,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND));
        try {
            return new HandlerFluxo(saida, formatador, true);
        } catch (RuntimeException e) {
            saida.close();
            throw e;
        }
    }

    /** Registra um evento em uma linha. */
    @Override
    public void registrar(Tipo tipo, String ator, String resultado, String detalhes) {
        if (fechado) {
            return;
        }
        try {
            LogRecord r = new LogRecord(Level.INFO, tipo == null ? "-" : tipo.name());
            r.setParameters(new Object[] {tipo == null ? null : tipo.name(), ator, resultado, detalhes});
            logger.log(r);
        } catch (RuntimeException e) {
        }
    }

    /** Registra uma exceção inesperada (tipo {@code ERRO}) com a pilha completa. */
    @Override
    public void registrarErro(String contexto, Throwable t) {
        if (fechado) {
            return;
        }
        try {
            String detalhes = contexto + (t == null ? "" : ": " + t);
            LogRecord r = new LogRecord(Level.SEVERE, "ERRO");
            r.setParameters(new Object[] {"ERRO", null, "EXCECAO", detalhes});
            r.setThrown(t);
            logger.log(r);
        } catch (RuntimeException e) {
        }
    }

    /** Descarrega e fecha os handlers (exceto {@code System.out}). Idempotente. */
    public void fechar() {
        if (fechado) {
            return;
        }
        fechado = true;
        fecharHandler(console);
        fecharHandler(arquivo);
    }

    @Override
    public void close() {
        fechar();
    }

    private void fecharHandler(Handler h) {
        if (h == null) {
            return;
        }
        try {
            h.flush();
            logger.removeHandler(h);
            h.close();
        } catch (RuntimeException e) {
        }
    }

    /** Handler de fluxo que descarrega a cada registro. */
    private static class HandlerFluxo extends StreamHandler {
        HandlerFluxo(OutputStream saida, FormatadorLinha formatador, boolean utf8) {
            super(saida, formatador);
            setLevel(Level.ALL);
            if (utf8) {
                try {
                    setEncoding(StandardCharsets.UTF_8.name());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            flush();
        }
    }

    /** Escreve em {@code System.out} sem jamais fechá-lo. */
    private static final class ConsoleSaidaPadrao extends HandlerFluxo {
        ConsoleSaidaPadrao(FormatadorLinha formatador) {
            super(System.out, formatador, false);
        }

        @Override
        public synchronized void close() {
            flush();
        }
    }
}
