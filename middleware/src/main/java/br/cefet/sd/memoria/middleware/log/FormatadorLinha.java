package br.cefet.sd.memoria.middleware.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * {@link Formatter} que produz uma linha por evento no formato
 * {@code yyyy-MM-dd HH:mm:ss.SSS | TIPO | jogador | resultado | detalhes}.
 */
public final class FormatadorLinha extends Formatter {

    /** Padrão de data/hora de cada linha. */
    public static final String PADRAO_DATA_HORA = "yyyy-MM-dd HH:mm:ss.SSS";

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern(PADRAO_DATA_HORA).withZone(ZoneId.systemDefault());

    /** Separador entre colunas. */
    private static final String SEPARADOR = " | ";

    /** Marca de campo ausente. */
    static final String AUSENTE = "-";

    @Override
    public String format(LogRecord registro) {
        Object[] p = registro.getParameters();
        String tipo;
        String jogador;
        String resultado;
        String detalhes;
        if (p != null && p.length >= 4) {
            tipo = campo(p[0]);
            jogador = campo(p[1]);
            resultado = campo(p[2]);
            detalhes = campo(p[3]);
        } else {
            tipo = "LOG";
            jogador = AUSENTE;
            resultado = registro.getLevel() == null ? AUSENTE : registro.getLevel().getName();
            detalhes = campo(registro.getMessage());
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(DATA_HORA.format(registro.getInstant()))
                .append(SEPARADOR).append(tipo)
                .append(SEPARADOR).append(jogador)
                .append(SEPARADOR).append(resultado)
                .append(SEPARADOR).append(detalhes)
                .append(System.lineSeparator());
        Throwable t = registro.getThrown();
        if (t != null) {
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                t.printStackTrace(pw);
            }
            sb.append(sw);
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /** Converte um campo em texto de linha única: nulo ou vazio vira {@code "-"}. */
    static String campo(Object valor) {
        if (valor == null) {
            return AUSENTE;
        }
        String s = valor.toString();
        if (s.isEmpty()) {
            return AUSENTE;
        }
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r' || c == '\n' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                if (sb == null) {
                    sb = new StringBuilder(s);
                }
                sb.setCharAt(i, ' ');
            }
        }
        return sb == null ? s : sb.toString();
    }
}
