package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.comum.dto.SumarioJogadorDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

/**
 * Tela do Sumário da Sessão: total de Partidas_Concluidas, Partidas_Canceladas (separadamente),
 * valores por Jogador, partidas empatadas e o nome de todos os Campeões.
 */
public final class TelaSumario extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUNAS =
            {"Jogador", "Vitórias", "Pares", "Tentativas", "Aproveitamento"};

    private final transient OuvinteSumario ouvinte;
    private final JLabel rotuloConcluidas = new JLabel(textoConcluidas(0));
    private final JLabel rotuloCanceladas = new JLabel(textoCanceladas(0));
    private final JLabel rotuloCampeoes = new JLabel(textoCampeoes(List.of()));
    private final JTextArea areaEmpates = new JTextArea(2, 30);
    private final DefaultTableModel modeloJogadores = new DefaultTableModel(COLUNAS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };
    private final JTable tabelaJogadores = new JTable(modeloJogadores);
    private final JButton botaoVoltar = new JButton("Voltar à conexão");

    /** Cria a tela vazia; {@code ouvinte} é chamado na EDT ao acionar "Voltar à conexão". */
    public TelaSumario(OuvinteSumario ouvinte) {
        super(new BorderLayout(8, 8));
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        montar();
    }

    private void montar() {
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        getAccessibleContext().setAccessibleName("Sumário da sessão");

        JLabel titulo = new JLabel("Sessão encerrada");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));

        rotuloConcluidas.getAccessibleContext().setAccessibleName("Partidas concluídas");
        rotuloCanceladas.getAccessibleContext().setAccessibleName("Partidas canceladas");
        rotuloCampeoes.setFont(rotuloCampeoes.getFont().deriveFont(Font.BOLD, 15f));
        rotuloCampeoes.getAccessibleContext().setAccessibleName("Campeões da sessão");

        JPanel norte = new JPanel();
        norte.setLayout(new BoxLayout(norte, BoxLayout.Y_AXIS));
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        rotuloConcluidas.setAlignmentX(LEFT_ALIGNMENT);
        rotuloCanceladas.setAlignmentX(LEFT_ALIGNMENT);
        rotuloCampeoes.setAlignmentX(LEFT_ALIGNMENT);
        norte.add(titulo);
        norte.add(rotuloConcluidas);
        norte.add(rotuloCanceladas);
        norte.add(rotuloCampeoes);
        add(norte, BorderLayout.NORTH);

        tabelaJogadores.setFillsViewportHeight(true);
        tabelaJogadores.getAccessibleContext().setAccessibleName("Valores acumulados por jogador");
        JLabel legendaTabela = new JLabel("Valores por jogador:");
        legendaTabela.setLabelFor(tabelaJogadores);
        legendaTabela.setDisplayedMnemonic(KeyEvent.VK_J);
        JScrollPane rolagemTabela = new JScrollPane(tabelaJogadores);
        rolagemTabela.setPreferredSize(new Dimension(520, 140));

        areaEmpates.setEditable(false);
        areaEmpates.setLineWrap(true);
        areaEmpates.setWrapStyleWord(true);
        areaEmpates.setOpaque(false);
        areaEmpates.getAccessibleContext().setAccessibleName("Partidas empatadas");
        areaEmpates.setText(textoEmpates(List.of()));

        JPanel centro = new JPanel(new BorderLayout(4, 4));
        centro.add(legendaTabela, BorderLayout.NORTH);
        centro.add(rolagemTabela, BorderLayout.CENTER);
        centro.add(areaEmpates, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        botaoVoltar.setMnemonic(KeyEvent.VK_V);
        botaoVoltar.getAccessibleContext().setAccessibleName("Voltar à conexão");
        botaoVoltar.addActionListener(e -> ouvinte.aoFechar());
        JPanel sul = new JPanel(new FlowLayout(FlowLayout.CENTER));
        sul.add(botaoVoltar);
        add(sul, BorderLayout.SOUTH);
    }

    /** Exibe o sumário recebido. */
    public void mostrarSumario(SumarioSessaoDTO sumario) {
        Objects.requireNonNull(sumario, "sumario");
        rotuloConcluidas.setText(textoConcluidas(sumario.partidasConcluidas()));
        rotuloCanceladas.setText(textoCanceladas(sumario.partidasCanceladas()));
        rotuloCampeoes.setText(textoCampeoes(sumario.campeoes()));
        modeloJogadores.setRowCount(0);
        for (SumarioJogadorDTO jogador : sumario.jogadores()) {
            modeloJogadores.addRow(linhaJogador(jogador));
        }
        areaEmpates.setText(textoEmpates(sumario.empates()));
    }

    /**
     * Cria (sem exibir) uma janela que hospeda a tela; fechar a janela equivale a voltar à conexão.
     */
    public JFrame emJanela() {
        JFrame janela = new JFrame("Sumário da sessão");
        janela.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        janela.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                ouvinte.aoFechar();
            }
        });
        janela.setContentPane(this);
        janela.pack();
        janela.setLocationRelativeTo(null);
        return janela;
    }

    /** "Partidas concluídas: N". */
    static String textoConcluidas(int n) {
        return "Partidas concluídas: " + n;
    }

    /** "Partidas canceladas: M". */
    static String textoCanceladas(int m) {
        return "Partidas canceladas: " + m;
    }

    /** "Campeão: X" ou "Campeões (empate): A, B", nomeando todos os campeões. */
    static String textoCampeoes(List<String> campeoes) {
        if (campeoes == null || campeoes.isEmpty()) {
            return "Campeão: nenhum";
        }
        if (campeoes.size() == 1) {
            return "Campeão: " + campeoes.get(0);
        }
        return "Campeões (empate): " + String.join(", ", campeoes);
    }

    /** Resumo das partidas empatadas; uma linha por partida. */
    static String textoEmpates(List<List<String>> empates) {
        if (empates == null || empates.isEmpty()) {
            return "Partidas empatadas: 0";
        }
        StringBuilder sb = new StringBuilder("Partidas empatadas: ").append(empates.size());
        for (List<String> empate : empates) {
            sb.append('\n').append("Empate entre: ").append(String.join(", ", empate));
        }
        return sb.toString();
    }

    /**
     * Linha da tabela: nome (com marca "desconectado"), vitórias, pares, tentativas,
     * aproveitamento.
     */
    static String[] linhaJogador(SumarioJogadorDTO jogador) {
        String nome = jogador.desconectado() ? jogador.nome() + " (desconectado)" : jogador.nome();
        return new String[] {
            nome,
            Integer.toString(jogador.vitorias()),
            Integer.toString(jogador.pares()),
            Integer.toString(jogador.tentativas()),
            PainelResultado.textoAproveitamento(jogador.aproveitamento(), jogador.semTentativas())
        };
    }

    JButton botaoVoltar() {
        return botaoVoltar;
    }

    String textoConcluidasExibido() {
        return rotuloConcluidas.getText();
    }

    String textoCanceladasExibido() {
        return rotuloCanceladas.getText();
    }

    String textoCampeoesExibido() {
        return rotuloCampeoes.getText();
    }

    int linhasJogadores() {
        return modeloJogadores.getRowCount();
    }
}
