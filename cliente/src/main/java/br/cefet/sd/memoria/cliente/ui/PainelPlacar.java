package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Placar dos Jogadores: nome, pares, tentativas e aproveitamento. */
public final class PainelPlacar extends JPanel {

    private static final long serialVersionUID = 1L;

    static final String MARCADOR_VEZ = "▶";
    static final String TEXTO_SEM_TENTATIVAS = "sem tentativas";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String[] COLUNAS = {"Vez", "Jogador", "Pares", "Tentativas", "Aproveitamento"};
    private static final String[] COLUNAS_COM_PRONTO =
            {"Vez", "Jogador", "Pares", "Tentativas", "Aproveitamento", "Pronto"};

    private final DefaultTableModel modelo = new DefaultTableModel(COLUNAS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };
    private final JTable tabela = new JTable(modelo);

    /** Cria o painel vazio. */
    public PainelPlacar() {
        super(new BorderLayout());
        tabela.setFocusable(false);
        tabela.setRowSelectionAllowed(false);
        tabela.setFillsViewportHeight(true);
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.getAccessibleContext().setAccessibleName("Placar dos jogadores");
        getAccessibleContext().setAccessibleName("Placar");
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(420, 140));
        add(rolagem, BorderLayout.CENTER);
    }

    /** Atualiza o placar sem informação de prontidão. */
    public void atualizar(List<PlacarJogadorDTO> placar, String idJogadorDaVez) {
        atualizar(placar, idJogadorDaVez, null);
    }

    /** Atualiza o placar. */
    public void atualizar(List<PlacarJogadorDTO> placar, String idJogadorDaVez, Set<String> prontos) {
        Objects.requireNonNull(placar, "placar");
        String[][] linhas = linhas(placar, idJogadorDaVez, prontos);
        modelo.setDataVector(linhas, prontos == null ? COLUNAS : COLUNAS_COM_PRONTO);
    }

    /**
     * Formata o aproveitamento recebido (sem recalcular): "sem tentativas" quando aplicável; senão,
     * duas casas decimais e "%", em formato brasileiro (ex.: {@code 66,67%}).
     */
    static String textoAproveitamento(PlacarJogadorDTO jogador) {
        if (jogador.semTentativas()) {
            return TEXTO_SEM_TENTATIVAS;
        }
        return String.format(PT_BR, "%s%%", formatar(jogador.aproveitamento()));
    }

    private static String formatar(BigDecimal valor) {
        // Apenas apresentação: ajusta a escala para 2 casas sem alterar o valor recebido.
        return String.format(PT_BR, "%.2f", valor.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Texto da coluna "Vez": marcador mais "da vez" para o Jogador da vez; vazio para os demais.
     */
    static String textoVez(PlacarJogadorDTO jogador, String idJogadorDaVez) {
        return idJogadorDaVez != null && idJogadorDaVez.equals(jogador.idParticipante())
                ? MARCADOR_VEZ + " da vez" : "";
    }

    /** Linhas da tabela, na ordem recebida. */
    static String[][] linhas(List<PlacarJogadorDTO> placar, String idJogadorDaVez, Set<String> prontos) {
        String[][] resultado = new String[placar.size()][];
        for (int i = 0; i < placar.size(); i++) {
            PlacarJogadorDTO j = placar.get(i);
            String[] base = {
                textoVez(j, idJogadorDaVez),
                j.nome(),
                Integer.toString(j.pares()),
                Integer.toString(j.tentativas()),
                textoAproveitamento(j)
            };
            if (prontos == null) {
                resultado[i] = base;
            } else {
                String[] comPronto = java.util.Arrays.copyOf(base, base.length + 1);
                comPronto[base.length] = prontos.contains(j.idParticipante()) ? "Sim" : "Não";
                resultado[i] = comPronto;
            }
        }
        return resultado;
    }

    /** Tabela interna (para testes). */
    JTable tabela() {
        return tabela;
    }
}
