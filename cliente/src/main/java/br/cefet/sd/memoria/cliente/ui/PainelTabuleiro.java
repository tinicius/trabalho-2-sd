package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.cliente.modelo.VisaoTabuleiro;
import br.cefet.sd.memoria.comum.catalogo.CatalogoImagens;
import br.cefet.sd.memoria.comum.dto.CatalogoImagensDTO;
import br.cefet.sd.memoria.comum.dto.EstadoCartaDTO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

/** Grade {@code linhas × colunas} de botões de carta. */
public final class PainelTabuleiro extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Lado, em pixels, da imagem exibida em cada carta. */
    static final int TAMANHO_PADRAO = 96;

    private static final int ESPACO = 4;
    private static final int ESPESSURA_BORDA_CONQUISTADA = 4;
    private static final Color COR_BORDA_CONQUISTADA = new Color(0, 110, 0);
    private static final String TEXTO_CONQUISTADA = "conquistada";

    private final transient OuvinteTabuleiro ouvinte;
    private final int tamanhoIcone;
    private final List<JButton> botoes = new ArrayList<>();
    private final Map<String, Optional<ImageIcon>> cacheIcones = new HashMap<>();
    private Border bordaPadrao;
    private int linhas;
    private int colunas;

    /** Cria o painel com o tamanho padrão de imagem. */
    public PainelTabuleiro(OuvinteTabuleiro ouvinte) {
        this(ouvinte, TAMANHO_PADRAO);
    }

    /** Cria o painel. */
    public PainelTabuleiro(OuvinteTabuleiro ouvinte, int tamanhoIcone) {
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        if (tamanhoIcone < 16) {
            throw new IllegalArgumentException("tamanhoIcone deve ser >= 16");
        }
        this.tamanhoIcone = tamanhoIcone;
        setLayout(new GridLayout(1, 1, ESPACO, ESPACO));
        getAccessibleContext().setAccessibleName("Tabuleiro");
        getAccessibleContext().setAccessibleDescription("Grade de cartas do jogo da memória");
    }

    /**
     * Redesenha o tabuleiro conforme a visão recebida. Reconstrói a grade se as dimensões mudaram.
     */
    public void atualizar(VisaoTabuleiro visao, CatalogoImagensDTO catalogo) {
        Objects.requireNonNull(visao, "visao");
        if (botoes.isEmpty() || visao.linhas() != linhas || visao.colunas() != colunas) {
            reconstruir(visao.linhas(), visao.colunas());
        }
        for (int pos = 0; pos < botoes.size(); pos++) {
            configurar(botoes.get(pos), pos, visao.celulas().get(pos), catalogo);
        }
        repaint();
    }

    /** Desabilita todos os botões sem alterar suas imagens. */
    public void desabilitarTodos() {
        for (JButton botao : botoes) {
            botao.setEnabled(false);
            botao.getAccessibleContext().setAccessibleDescription(DESCRICAO_DESABILITADA);
        }
    }

    private void reconstruir(int novasLinhas, int novasColunas) {
        removeAll();
        botoes.clear();
        linhas = novasLinhas;
        colunas = novasColunas;
        setLayout(new GridLayout(linhas, colunas, ESPACO, ESPACO));
        for (int pos = 0; pos < linhas * colunas; pos++) {
            botoes.add(criarBotao(pos));
        }
        for (JButton botao : botoes) {
            add(botao);
        }
        revalidate();
    }

    private JButton criarBotao(int posicao) {
        JButton botao = new JButton();
        if (bordaPadrao == null) {
            bordaPadrao = botao.getBorder();
        }
        botao.setHorizontalTextPosition(SwingConstants.CENTER);
        botao.setVerticalTextPosition(SwingConstants.BOTTOM);
        botao.setFont(botao.getFont().deriveFont(Font.BOLD, 11f));
        botao.setMargin(new java.awt.Insets(2, 2, 2, 2));
        botao.setPreferredSize(new Dimension(tamanhoIcone + 16, tamanhoIcone + 32));
        botao.setEnabled(false);
        // O clique só notifica; nenhuma mudança visual local.
        botao.addActionListener(e -> ouvinte.aoSelecionar(posicao));
        return botao;
    }

    private void configurar(JButton botao, int posicao, VisaoTabuleiro.Celula celula,
                            CatalogoImagensDTO catalogo) {
        String recurso = recursoDaCelula(celula, catalogo);
        Optional<ImageIcon> icone = recurso == null ? Optional.empty() : icone(recurso);
        boolean conquistada = celula.estado() == EstadoCartaDTO.CONQUISTADA;

        botao.setIcon(icone.orElse(null));
        // Mantém a imagem nítida mesmo desabilitado ("Aguarde sua vez").
        botao.setDisabledIcon(icone.orElse(null));
        botao.setText(textoDoBotao(celula, icone.isPresent()));
        botao.setBorder(conquistada ? bordaConquistada() : bordaPadrao);
        botao.setEnabled(celula.habilitada());

        String nome = nomeAcessivel(posicao, celula.estado());
        botao.getAccessibleContext().setAccessibleName(nome);
        botao.getAccessibleContext().setAccessibleDescription(
                celula.habilitada() ? DESCRICAO_HABILITADA : DESCRICAO_DESABILITADA);
        botao.setToolTipText(nome);
    }

    private static Border bordaConquistada() {
        return BorderFactory.createLineBorder(COR_BORDA_CONQUISTADA, ESPESSURA_BORDA_CONQUISTADA);
    }

    private Optional<ImageIcon> icone(String recurso) {
        return cacheIcones.computeIfAbsent(recurso + "@" + tamanhoIcone, k -> carregarIcone(recurso));
    }

    private Optional<ImageIcon> carregarIcone(String recurso) {
        try {
            byte[] bytes = CatalogoImagens.lerBytes(recurso);
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                return Optional.empty();
            }
            return Optional.of(new ImageIcon(redimensionar(original, tamanhoIcone)));
        } catch (IOException | RuntimeException e) {
            // Recurso ausente ou ilegível: o botão usa o texto alternativo.
            return Optional.empty();
        }
    }

    /** Redimensiona preservando a proporção, centralizando em um quadrado {@code lado × lado}. */
    static BufferedImage redimensionar(BufferedImage original, int lado) {
        double escala = Math.min((double) lado / original.getWidth(), (double) lado / original.getHeight());
        int largura = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage destino = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, (lado - largura) / 2, (lado - altura) / 2, largura, altura, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static final String DESCRICAO_HABILITADA = "Clique para selecionar esta carta";
    private static final String DESCRICAO_DESABILITADA = "Seleção desabilitada";

    /**
     * Nome do recurso de imagem de uma célula: o verso para fechadas; para reveladas e
     * conquistadas, o recurso do catálogo para o {@code idPar}.
     */
    static String recursoDaCelula(VisaoTabuleiro.Celula celula, CatalogoImagensDTO catalogo) {
        if (celula.estado() == EstadoCartaDTO.FECHADA) {
            return CatalogoImagens.ARQUIVO_VERSO;
        }
        if (celula.idPar() == null || catalogo == null) {
            return null;
        }
        return catalogo.recursoPorPar().get(celula.idPar());
    }

    /**
     * Texto exibido no botão. Com imagem, só as conquistadas têm texto (indicação não dependente de
     * cor); sem imagem, mostra um texto alternativo ("?" ou o id do par).
     */
    static String textoDoBotao(VisaoTabuleiro.Celula celula, boolean temImagem) {
        boolean conquistada = celula.estado() == EstadoCartaDTO.CONQUISTADA;
        if (temImagem) {
            return conquistada ? TEXTO_CONQUISTADA : "";
        }
        String base = celula.estado() == EstadoCartaDTO.FECHADA || celula.idPar() == null
                ? "?" : "Par " + celula.idPar();
        return conquistada ? base + " (" + TEXTO_CONQUISTADA + ")" : base;
    }

    /**
     * Nome acessível da carta: "Carta N, estado", com N a partir de 1 (a posição interna é base 0).
     */
    static String nomeAcessivel(int posicao, EstadoCartaDTO estado) {
        String descricaoEstado = switch (estado) {
            case FECHADA -> "fechada";
            case REVELADA -> "revelada";
            case CONQUISTADA -> TEXTO_CONQUISTADA;
        };
        return "Carta " + (posicao + 1) + ", " + descricaoEstado;
    }

    /** Quantidade de botões atualmente na grade. */
    int quantidadeBotoes() {
        return botoes.size();
    }

    /** Botão da posição informada (base 0). */
    JButton botao(int posicao) {
        return botoes.get(posicao);
    }
}
