package br.cefet.sd.memoria.cliente.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.Timer;

/** Barra de status com a mensagem do último evento. */
public final class BarraStatus extends JLabel {

    private static final long serialVersionUID = 1L;

    /** Duração mínima, em milissegundos, de uma mensagem na barra. */
    public static final int DURACAO_MINIMA_MS = 3000;

    /** Texto neutro exibido quando não há mensagem recente. */
    public static final String TEXTO_OCIOSO = "Aguardando eventos da partida.";

    static final String PREFIXO_ERRO = "Erro: ";

    private static final Color COR_NORMAL = new Color(0x202020);
    private static final Color COR_ERRO = new Color(0x9C0000);

    private final Timer temporizador;

    /** Cria a barra com a duração mínima de 3 s. */
    public BarraStatus() {
        this(DURACAO_MINIMA_MS);
    }

    /** Cria a barra. */
    public BarraStatus(int duracaoMs) {
        super(TEXTO_OCIOSO);
        temporizador = new Timer(Math.max(DURACAO_MINIMA_MS, duracaoMs), e -> voltarAoOcioso());
        temporizador.setRepeats(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        setOpaque(true);
        aplicar(TEXTO_OCIOSO, COR_NORMAL);
    }

    /**
     * Mostra uma mensagem informativa, substituindo a atual e reiniciando o prazo de 3 s. Mensagens
     * nulas ou em branco são ignoradas.
     */
    public void mostrar(String mensagem) {
        if (mensagemVazia(mensagem)) {
            return;
        }
        aplicar(mensagem.strip(), COR_NORMAL);
        temporizador.restart();
    }

    /** Mostra um erro com o prefixo "Erro:" e cor de destaque; mesmo prazo de {@link #mostrar}. */
    public void mostrarErro(String mensagem) {
        if (mensagemVazia(mensagem)) {
            return;
        }
        aplicar(textoDeErro(mensagem), COR_ERRO);
        temporizador.restart();
    }

    /** Mostra uma mensagem que permanece até a próxima (sem voltar ao neutro). */
    public void mostrarFixa(String mensagem) {
        if (mensagemVazia(mensagem)) {
            return;
        }
        temporizador.stop();
        aplicar(mensagem.strip(), COR_ERRO);
    }

    /** Texto atualmente exibido. */
    public String textoAtual() {
        return getText();
    }

    /** Cancela o temporizador pendente (use ao descartar a tela). */
    public void parar() {
        temporizador.stop();
    }

    private void voltarAoOcioso() {
        aplicar(TEXTO_OCIOSO, COR_NORMAL);
    }

    private void aplicar(String texto, Color cor) {
        setText(texto);
        setForeground(cor);
        getAccessibleContext().setAccessibleName("Status: " + texto);
        getAccessibleContext().setAccessibleDescription("Mensagem de status do jogo");
    }

    private static boolean mensagemVazia(String mensagem) {
        return mensagem == null || mensagem.isBlank();
    }

    /** Aplica o prefixo "Erro:" se a mensagem ainda não o possui. */
    static String textoDeErro(String mensagem) {
        String texto = mensagem.strip();
        if (texto.regionMatches(true, 0, "erro:", 0, 5)) {
            return texto;
        }
        return PREFIXO_ERRO + texto;
    }

    /** Indica se o temporizador de retorno ao neutro está ativo. */
    boolean temporizadorAtivo() {
        return temporizador.isRunning();
    }

    /** Atraso configurado, em milissegundos. */
    int atrasoMs() {
        return temporizador.getInitialDelay();
    }
}
