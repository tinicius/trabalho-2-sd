package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.cliente.modelo.VisaoTabuleiro;
import br.cefet.sd.memoria.comum.dto.CatalogoImagensDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Tela da partida: indicador de vez (norte), {@link PainelTabuleiro} (centro), {@link PainelPlacar}
 * (leste) e {@link BarraStatus} (sul).
 */
public final class TelaJogo extends JPanel {

    private static final long serialVersionUID = 1L;

    static final String TEXTO_SUA_VEZ = "Sua vez";
    static final String TEXTO_AGUARDE = "Aguarde sua vez";
    static final String TEXTO_SELECAO_DESABILITADA = "Seleção desabilitada";
    static final String TEXTO_PARTIDA_ENCERRADA = "Partida encerrada";
    static final String TEXTO_CONEXAO_PERDIDA = "Conexão perdida: seleção desabilitada";

    private final PainelTabuleiro painelTabuleiro;
    private final PainelPlacar painelPlacar = new PainelPlacar();
    private final BarraStatus barraStatus = new BarraStatus();
    private final JLabel indicadorVez = new JLabel(TEXTO_AGUARDE, SwingConstants.CENTER);

    /** Cria a tela. */
    public TelaJogo(OuvinteTabuleiro ouvinte) {
        super(new BorderLayout(8, 8));
        painelTabuleiro = new PainelTabuleiro(Objects.requireNonNull(ouvinte, "ouvinte"));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        indicadorVez.setFont(indicadorVez.getFont().deriveFont(Font.BOLD, 18f));
        indicadorVez.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        indicadorVez.getAccessibleContext().setAccessibleName("Indicador de vez: " + TEXTO_AGUARDE);

        JPanel centro = new JPanel(new GridBagLayout());
        centro.add(painelTabuleiro);
        JScrollPane rolagemTabuleiro = new JScrollPane(centro);
        rolagemTabuleiro.setBorder(BorderFactory.createEmptyBorder());

        add(indicadorVez, BorderLayout.NORTH);
        add(rolagemTabuleiro, BorderLayout.CENTER);
        add(painelPlacar, BorderLayout.EAST);
        add(barraStatus, BorderLayout.SOUTH);
        getAccessibleContext().setAccessibleName("Tela do jogo");
    }

    /** Aplica um novo estado: redesenha o tabuleiro, o placar e o indicador de vez. */
    public void aplicar(VisaoTabuleiro visao, EstadoPublicoDTO estado, CatalogoImagensDTO catalogo) {
        Objects.requireNonNull(visao, "visao");
        Objects.requireNonNull(estado, "estado");
        CatalogoImagensDTO usado = catalogo != null ? catalogo : estado.catalogo();
        painelTabuleiro.atualizar(visao, usado);
        painelPlacar.atualizar(estado.placar(), estado.idJogadorDaVez());
        definirIndicador(textoIndicadorVez(visao, estado));
    }

    /**
     * Atualiza somente o placar (ex.: notificação de estatísticas), com os valores como recebidos.
     */
    public void atualizarPlacar(List<PlacarJogadorDTO> placar, String idJogadorDaVez) {
        painelPlacar.atualizar(placar, idJogadorDaVez);
    }

    /** Atualiza somente o placar, com o estado de prontidão dos Jogadores. */
    public void atualizarPlacar(List<PlacarJogadorDTO> placar, String idJogadorDaVez, Set<String> prontos) {
        painelPlacar.atualizar(placar, idJogadorDaVez, prontos);
    }

    /** Mostra uma mensagem de status (mantida por pelo menos 3 s ou até a próxima). */
    public void mostrarStatus(String mensagem) {
        barraStatus.mostrar(mensagem);
    }

    /** Mostra um erro na barra de status, com prefixo "Erro:". */
    public void mostrarErro(String mensagem) {
        barraStatus.mostrarErro(mensagem);
    }

    /**
     * Mostra uma mensagem que permanece até a próxima, sem voltar ao texto neutro (ex.: conexão
     * perdida).
     */
    public void mostrarStatusFixo(String mensagem) {
        barraStatus.mostrarFixa(mensagem);
    }

    /**
     * Desabilita todas as cartas e informa que a seleção está indisponível. A mensagem de status
     * existente não é alterada.
     */
    public void desabilitarTudo() {
        painelTabuleiro.desabilitarTodos();
        definirIndicador(TEXTO_CONEXAO_PERDIDA);
    }

    /** Libera recursos (temporizador da barra de status) ao descartar a tela. */
    public void encerrar() {
        barraStatus.parar();
    }

    /** Texto do indicador de vez: */
    static String textoIndicadorVez(VisaoTabuleiro visao, EstadoPublicoDTO estado) {
        if (estado.status() != StatusPartida.EM_ANDAMENTO) {
            return TEXTO_PARTIDA_ENCERRADA;
        }
        if (visao.minhaVez()) {
            return visao.podeSelecionar() ? TEXTO_SUA_VEZ : TEXTO_SELECAO_DESABILITADA;
        }
        String nome = nomeDoJogadorDaVez(estado);
        return nome == null ? TEXTO_AGUARDE : TEXTO_AGUARDE + " (vez de " + nome + ")";
    }

    /** Nome do Jogador da vez segundo o placar do estado; {@code null} se desconhecido. */
    static String nomeDoJogadorDaVez(EstadoPublicoDTO estado) {
        String id = estado.idJogadorDaVez();
        if (id == null) {
            return null;
        }
        for (PlacarJogadorDTO j : estado.placar()) {
            if (id.equals(j.idParticipante())) {
                return j.nome();
            }
        }
        return null;
    }

    private void definirIndicador(String texto) {
        indicadorVez.setText(texto);
        indicadorVez.getAccessibleContext().setAccessibleName("Indicador de vez: " + texto);
        // Itálico quando a seleção está desabilitada: sinal visual que não depende só de cor.
        boolean ativo = TEXTO_SUA_VEZ.equals(texto);
        indicadorVez.setFont(indicadorVez.getFont().deriveFont(ativo ? Font.BOLD : Font.BOLD | Font.ITALIC));
        indicadorVez.setForeground(ativo ? new Color(0, 90, 0) : new Color(0x505050));
    }

    PainelTabuleiro painelTabuleiro() {
        return painelTabuleiro;
    }

    PainelPlacar painelPlacar() {
        return painelPlacar;
    }

    BarraStatus barraStatus() {
        return barraStatus;
    }

    String textoIndicador() {
        return indicadorVez.getText();
    }
}
