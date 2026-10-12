package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Painel do resultado de uma Partida: vencedor ou empate, placar final e os dois controles de
 * resposta ("Continuar jogando" e "Encerrar sessão").
 */
public final class PainelResultado extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUNAS = {"Jogador", "Pares", "Tentativas", "Aproveitamento"};

    private final transient OuvinteResultado ouvinte;
    private final JLabel rotuloVencedor = new JLabel("Aguardando resultado…");
    private final DefaultTableModel modeloPlacar = new DefaultTableModel(COLUNAS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };
    private final JTable tabelaPlacar = new JTable(modeloPlacar);
    private final JLabel rotuloPrazo = new JLabel(" ");
    private final JLabel rotuloResposta = new JLabel(" ");
    private final JButton botaoContinuar = new JButton("Continuar jogando");
    private final JButton botaoEncerrar = new JButton("Encerrar sessão");
    private final Timer temporizador;

    private boolean respondido;
    private int segundosRestantes;

    /** Cria o painel; {@code ouvinte} é chamado na EDT no primeiro clique de resposta. */
    public PainelResultado(OuvinteResultado ouvinte) {
        super(new BorderLayout(8, 8));
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.temporizador = new Timer(1000, e -> passarSegundo());
        this.temporizador.setRepeats(true);
        montar();
    }

    private void montar() {
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        getAccessibleContext().setAccessibleName("Resultado da partida");

        rotuloVencedor.setFont(rotuloVencedor.getFont().deriveFont(Font.BOLD, 18f));
        rotuloVencedor.getAccessibleContext().setAccessibleName("Resultado da partida");
        add(rotuloVencedor, BorderLayout.NORTH);

        tabelaPlacar.setFillsViewportHeight(true);
        tabelaPlacar.getAccessibleContext().setAccessibleName("Placar final da partida");
        JLabel legenda = new JLabel("Placar final:");
        legenda.setLabelFor(tabelaPlacar);
        legenda.setDisplayedMnemonic(KeyEvent.VK_P);
        JScrollPane rolagem = new JScrollPane(tabelaPlacar);
        rolagem.setPreferredSize(new Dimension(420, 140));
        JPanel centro = new JPanel(new BorderLayout(4, 4));
        centro.add(legenda, BorderLayout.NORTH);
        centro.add(rolagem, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        botaoContinuar.setMnemonic(KeyEvent.VK_C);
        botaoContinuar.getAccessibleContext().setAccessibleName("Continuar jogando");
        botaoContinuar.setToolTipText("Jogar outra partida com os mesmos jogadores");
        botaoEncerrar.setMnemonic(KeyEvent.VK_E);
        botaoEncerrar.getAccessibleContext().setAccessibleName("Encerrar sessão");
        botaoEncerrar.setToolTipText("Encerrar a sessão e ver o sumário");
        botaoContinuar.setEnabled(false);
        botaoEncerrar.setEnabled(false);
        botaoContinuar.addActionListener(e -> responder(true));
        botaoEncerrar.addActionListener(e -> responder(false));

        rotuloPrazo.getAccessibleContext().setAccessibleName("Prazo para responder");
        rotuloResposta.getAccessibleContext().setAccessibleName("Resposta enviada");

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        botoes.add(botaoContinuar);
        botoes.add(botaoEncerrar);

        JPanel sul = new JPanel();
        sul.setLayout(new BoxLayout(sul, BoxLayout.Y_AXIS));
        rotuloPrazo.setAlignmentX(CENTER_ALIGNMENT);
        rotuloResposta.setAlignmentX(CENTER_ALIGNMENT);
        botoes.setAlignmentX(CENTER_ALIGNMENT);
        sul.add(rotuloPrazo);
        sul.add(botoes);
        sul.add(rotuloResposta);
        add(sul, BorderLayout.SOUTH);
    }

    /** Exibe o vencedor ou o empate e o placar final. */
    public void mostrarResultado(ResultadoPartidaDTO resultado) {
        Objects.requireNonNull(resultado, "resultado");
        rotuloVencedor.setText(textoVencedor(resultado));
        modeloPlacar.setRowCount(0);
        for (PlacarJogadorDTO jogador : resultado.placarFinal()) {
            modeloPlacar.addRow(linhaPlacar(jogador));
        }
        if (!respondido && !temporizador.isRunning()) {
            rotuloPrazo.setText(textoPrazo(resultado.prazoContinuarSeg()));
        }
    }

    /**
     * Habilita os botões de resposta e inicia a contagem regressiva do prazo. Ignorado se o Jogador
     * já respondeu.
     */
    public void habilitarResposta(PerguntaContinuarDTO pergunta) {
        Objects.requireNonNull(pergunta, "pergunta");
        if (respondido) {
            return;
        }
        segundosRestantes = pergunta.prazoSeg();
        rotuloPrazo.setText(textoPrazo(segundosRestantes));
        rotuloResposta.setText(" ");
        botaoContinuar.setEnabled(true);
        botaoEncerrar.setEnabled(true);
        temporizador.restart();
    }

    /** Para o temporizador e fecha a janela que hospeda o painel, se houver. */
    public void fechar() {
        temporizador.stop();
        botaoContinuar.setEnabled(false);
        botaoEncerrar.setEnabled(false);
        Window janela = SwingUtilities.getWindowAncestor(this);
        if (janela != null) {
            janela.dispose();
        }
    }

    private void responder(boolean continuar) {
        if (respondido || !botaoContinuar.isEnabled()) {
            return;
        }
        respondido = true;
        temporizador.stop();
        botaoContinuar.setEnabled(false);
        botaoEncerrar.setEnabled(false);
        rotuloPrazo.setText(" ");
        rotuloResposta.setText(textoRespostaEnviada(continuar));
        ouvinte.aoResponder(continuar);
    }

    private void passarSegundo() {
        if (respondido) {
            temporizador.stop();
            return;
        }
        if (segundosRestantes > 0) {
            segundosRestantes--;
        }
        if (segundosRestantes > 0) {
            rotuloPrazo.setText(textoPrazo(segundosRestantes));
        } else {
            temporizador.stop();
            botaoContinuar.setEnabled(false);
            botaoEncerrar.setEnabled(false);
            rotuloPrazo.setText("Tempo esgotado: aguardando o encerramento da sessão.");
        }
    }

    /** "Vencedor: X" ou "Empate entre: A, B". */
    static String textoVencedor(ResultadoPartidaDTO resultado) {
        List<String> nomes = resultado.nomesVencedores();
        if (resultado.empate() || nomes.size() >= 2) {
            return "Empate entre: " + String.join(", ", nomes);
        }
        return "Vencedor: " + nomes.get(0);
    }

    /** "Responda em até N s". */
    static String textoPrazo(int segundos) {
        return "Responda em até " + Math.max(0, segundos) + " s";
    }

    /** Texto da resposta já enviada. */
    static String textoRespostaEnviada(boolean continuar) {
        return continuar
                ? "Resposta enviada: continuar jogando. Aguardando os demais jogadores."
                : "Resposta enviada: encerrar sessão.";
    }

    /** Linha da tabela: nome, pares, tentativas e aproveitamento (ou "sem tentativas"). */
    static String[] linhaPlacar(PlacarJogadorDTO jogador) {
        return new String[] {
            jogador.nome(),
            Integer.toString(jogador.pares()),
            Integer.toString(jogador.tentativas()),
            textoAproveitamento(jogador.aproveitamento(), jogador.semTentativas())
        };
    }

    /**
     * Formata o aproveitamento como recebido (ex.: "66,67%"); "sem tentativas" quando aplicável.
     */
    static String textoAproveitamento(BigDecimal aproveitamento, boolean semTentativas) {
        if (semTentativas) {
            return "0% (sem tentativas)";
        }
        BigDecimal valor = aproveitamento.scale() > 2
                ? aproveitamento.setScale(2, RoundingMode.HALF_UP)
                : aproveitamento;
        return String.format(Locale.forLanguageTag("pt-BR"), "%s%%", valor.toPlainString().replace('.', ','));
    }

    JButton botaoContinuar() {
        return botaoContinuar;
    }

    JButton botaoEncerrar() {
        return botaoEncerrar;
    }

    String textoResultado() {
        return rotuloVencedor.getText();
    }

    String textoResposta() {
        return rotuloResposta.getText();
    }

    String textoPrazoExibido() {
        return rotuloPrazo.getText();
    }

    int linhasPlacar() {
        return modeloPlacar.getRowCount();
    }
}
