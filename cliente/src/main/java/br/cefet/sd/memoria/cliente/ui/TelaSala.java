package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.comum.dto.FaseSala;
import br.cefet.sd.memoria.comum.dto.ParticipanteDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Tela da Sala: lista de Jogadores com o estado de prontidão de cada um e o botão "Pronto". A
 * prontidão é sempre mostrada em texto ("pronto" / "não pronto"), nunca só por cor.
 */
public class TelaSala extends JPanel {

    private static final long serialVersionUID = 1L;

    static final String TEXTO_PRONTO = "pronto";
    static final String TEXTO_NAO_PRONTO = "não pronto";

    private final OuvinteTelaSala ouvinte;
    private final DefaultListModel<String> modeloLista = new DefaultListModel<>();
    private final JList<String> lista = new JList<>(modeloLista);
    private final JLabel rotuloCapacidade = new JLabel("Aguardando dados da sala...");
    private final JLabel rotuloInstrucao = new JLabel(" ");
    private final JButton botaoPronto = new JButton("Pronto");

    private String meuId;
    private SalaDTO ultimaSala;
    private boolean aguardandoResposta;

    public TelaSala(OuvinteTelaSala ouvinte) {
        this(ouvinte, null);
    }

    public TelaSala(OuvinteTelaSala ouvinte, String meuId) {
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.meuId = meuId;
        montar();
        atualizarBotao();
    }

    private void montar() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        getAccessibleContext().setAccessibleName("Sala de espera");

        JLabel titulo = new JLabel("Sala de espera");
        titulo.setFont(titulo.getFont().deriveFont(java.awt.Font.BOLD, titulo.getFont().getSize2D() + 4f));
        JPanel topo = new JPanel(new BorderLayout(4, 4));
        topo.add(titulo, BorderLayout.NORTH);
        topo.add(rotuloCapacidade, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setToolTipText("Jogadores na sala e se cada um está pronto");
        lista.getAccessibleContext().setAccessibleName("Jogadores da sala");
        lista.getAccessibleContext().setAccessibleDescription(
                "Lista de jogadores com o estado pronto ou não pronto");
        JLabel rotuloLista = new JLabel("Jogadores:");
        rotuloLista.setDisplayedMnemonic('J');
        rotuloLista.setLabelFor(lista);
        JPanel centro = new JPanel(new BorderLayout(4, 4));
        centro.add(rotuloLista, BorderLayout.NORTH);
        centro.add(new JScrollPane(lista), BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        botaoPronto.setMnemonic('P');
        botaoPronto.setToolTipText("Indicar que você está pronto para começar (Alt+P)");
        botaoPronto.getAccessibleContext().setAccessibleDescription(
                "Marca você como pronto; a partida começa quando todos estiverem prontos");
        botaoPronto.addActionListener(e -> aoClicarPronto());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        rodape.add(botaoPronto);
        rodape.add(rotuloInstrucao);
        add(rodape, BorderLayout.SOUTH);
    }

    private void aoClicarPronto() {
        if (!botaoPronto.isEnabled()) {
            return;
        }
        aguardandoResposta = true;
        atualizarBotao();
        ouvinte.aoMarcarPronto();
    }

    /** Define o identificador do Jogador local e reavalia lista e botão. */
    public void definirMeuId(String meuId) {
        this.meuId = meuId;
        if (ultimaSala != null) {
            atualizarSala(ultimaSala);
        } else {
            atualizarBotao();
        }
    }

    /** Atualiza lista, capacidade e botão a partir do estado da Sala (na EDT). */
    public void atualizarSala(SalaDTO sala) {
        Objects.requireNonNull(sala, "sala");
        ultimaSala = sala;
        modeloLista.clear();
        for (String linha : linhas(sala.participantes(), meuId)) {
            modeloLista.addElement(linha);
        }
        rotuloCapacidade.setText(textoCapacidade(sala));
        if (jaPronto(sala, meuId)) {
            aguardandoResposta = false;
        }
        atualizarBotao();
    }

    /** Reabilita "Pronto" após uma falha no envio, permitindo nova tentativa. */
    public void reabilitarPronto() {
        aguardandoResposta = false;
        atualizarBotao();
    }

    private void atualizarBotao() {
        boolean lobby = ultimaSala == null || ultimaSala.fase() == FaseSala.LOBBY;
        boolean pronto = ultimaSala != null && jaPronto(ultimaSala, meuId);
        botaoPronto.setEnabled(lobby && !pronto && !aguardandoResposta);
        rotuloInstrucao.setText(textoInstrucao(lobby, pronto, aguardandoResposta));
    }

    /** Número de linhas exibidas na lista (para testes). */
    int quantidadeLinhas() {
        return modeloLista.getSize();
    }

    String linha(int indice) {
        return modeloLista.get(indice);
    }

    boolean botaoProntoHabilitado() {
        return botaoPronto.isEnabled();
    }

    void clicarPronto() {
        botaoPronto.doClick();
    }

    String textoCapacidadeExibido() {
        return rotuloCapacidade.getText();
    }

    /** Texto de uma linha: "1. Ana - pronto" (com " (você)" para o Jogador local). */
    static String formatarLinha(ParticipanteDTO p, int posicao, String meuId) {
        String voce = p.id().equals(meuId) ? " (você)" : "";
        return posicao + ". " + p.nome() + voce + " - " + (p.pronto() ? TEXTO_PRONTO : TEXTO_NAO_PRONTO);
    }

    /** Linhas ordenadas por ordem de entrada, numeradas a partir de 1. */
    static List<String> linhas(List<ParticipanteDTO> participantes, String meuId) {
        List<ParticipanteDTO> ordenados = new ArrayList<>(participantes);
        ordenados.sort(Comparator.comparingInt(ParticipanteDTO::ordemEntrada));
        List<String> saida = new ArrayList<>(ordenados.size());
        for (int i = 0; i < ordenados.size(); i++) {
            saida.add(formatarLinha(ordenados.get(i), i + 1, meuId));
        }
        return saida;
    }

    /** Ex.: "Jogadores na sala: 2 de 4 (1 pronto)". */
    static String textoCapacidade(SalaDTO sala) {
        long prontos = sala.participantes().stream().filter(ParticipanteDTO::pronto).count();
        return "Jogadores na sala: " + sala.participantes().size() + " de " + sala.capacidade()
                + " (" + prontos + (prontos == 1 ? " pronto)" : " prontos)");
    }

    static boolean jaPronto(SalaDTO sala, String meuId) {
        if (meuId == null) {
            return false;
        }
        return sala.participantes().stream().anyMatch(p -> p.id().equals(meuId) && p.pronto());
    }

    static String textoInstrucao(boolean lobby, boolean pronto, boolean aguardando) {
        if (!lobby) {
            return "A sala não está em fase de espera; \"Pronto\" indisponível.";
        }
        if (pronto) {
            return "Você está pronto. Aguardando os demais jogadores.";
        }
        if (aguardando) {
            return "Enviando...";
        }
        return "Clique em \"Pronto\" quando quiser começar.";
    }
}
