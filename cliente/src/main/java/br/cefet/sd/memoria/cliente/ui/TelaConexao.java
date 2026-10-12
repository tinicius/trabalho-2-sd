package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.cliente.modelo.ParametrosConexao;
import br.cefet.sd.memoria.cliente.modelo.ValidadorFormularioConexao;
import br.cefet.sd.memoria.cliente.modelo.ValidadorFormularioConexao.Campo;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Tela de conexão: nome do Jogador, host e porta do Registry. */
public class TelaConexao extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final String HOST_PADRAO = "localhost";
    public static final String PORTA_PADRAO = "1099";
    public static final String TIMEOUT_PADRAO_SEG = "10";

    private final OuvinteTelaConexao ouvinte;
    private final JTextField campoNome = new JTextField(20);
    private final JTextField campoHost = new JTextField(20);
    private final JTextField campoPorta = new JTextField(6);
    private final JTextField campoTimeout = new JTextField(6);
    private final JTextField campoRmiHostname = new JTextField(20);
    private final JButton botaoConectar = new JButton("Conectar");
    private final JLabel rotuloStatus = new JLabel(" ");
    private final boolean rmiHostnameTravado;
    private boolean bloqueada;

    /** Cria a tela com os valores padrão (host "localhost", porta "1099", timeout 10 s). */
    public TelaConexao(OuvinteTelaConexao ouvinte) {
        this(ouvinte, "", HOST_PADRAO, PORTA_PADRAO, TIMEOUT_PADRAO_SEG, "", false);
    }

    /** Cria a tela com valores iniciais (normalmente os últimos digitados pelo Jogador). */
    public TelaConexao(OuvinteTelaConexao ouvinte, String nome, String host, String porta,
            String timeoutSeg, String rmiHostname, boolean rmiHostnameTravado) {
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.rmiHostnameTravado = rmiHostnameTravado;
        campoNome.setText(nome == null ? "" : nome);
        campoHost.setText(host == null || host.isBlank() ? HOST_PADRAO : host);
        campoPorta.setText(porta == null || porta.isBlank() ? PORTA_PADRAO : porta);
        campoTimeout.setText(timeoutSeg == null || timeoutSeg.isBlank() ? TIMEOUT_PADRAO_SEG : timeoutSeg);
        campoRmiHostname.setText(rmiHostname == null ? "" : rmiHostname);
        montar();
    }

    private void montar() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        getAccessibleContext().setAccessibleName("Conexão com o jogo");

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        JLabel titulo = new JLabel("Jogo da Memória - Conectar");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, titulo.getFont().getSize2D() + 4f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        add(titulo, c);

        adicionarLinha(c, 1, "Nome:", 'N', campoNome,
                "Seu nome no jogo (1 a 20 caracteres)");
        adicionarLinha(c, 2, "Host:", 'H', campoHost,
                "Endereço do Registry RMI (1 a 255 caracteres)");
        adicionarLinha(c, 3, "Porta:", 'P', campoPorta,
                "Porta do Registry RMI (1 a 65535)");
        adicionarLinha(c, 4, "Timeout (s):", 'T', campoTimeout,
                "Tempo máximo para localizar os serviços no Registry, em segundos (1 a 600)");
        String dicaRmi = "IP ou nome deste computador, alcançável pelo Middleware (callback). "
                + "Obrigatório se o Middleware estiver em outra máquina; vazio usa o endereço da JVM";
        if (rmiHostnameTravado) {
            dicaRmi = "Já aplicado nesta execução; reabra o programa para alterar";
            campoRmiHostname.setEditable(false);
        }
        adicionarLinha(c, 5, "Meu IP:", 'M', campoRmiHostname, dicaRmi);

        botaoConectar.setMnemonic('C');
        botaoConectar.setToolTipText("Conectar ao Middleware (Alt+C)");
        botaoConectar.getAccessibleContext().setAccessibleDescription("Valida os campos e conecta");
        c.gridx = 1;
        c.gridy = 6;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        add(botaoConectar, c);

        rotuloStatus.getAccessibleContext().setAccessibleName("Mensagem de conexão");
        c.gridx = 0;
        c.gridy = 7;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(rotuloStatus, c);

        botaoConectar.addActionListener(e -> confirmar());
        campoNome.addActionListener(e -> confirmar());
        campoHost.addActionListener(e -> confirmar());
        campoPorta.addActionListener(e -> confirmar());
        campoTimeout.addActionListener(e -> confirmar());
        campoRmiHostname.addActionListener(e -> confirmar());

    }

    private void adicionarLinha(GridBagConstraints c, int linha, String texto, char atalho,
            JTextField campo, String dica) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setDisplayedMnemonic(atalho);
        rotulo.setLabelFor(campo);
        campo.setToolTipText(dica + " (Alt+" + atalho + ")");
        campo.getAccessibleContext().setAccessibleName(texto.replace(":", ""));
        campo.getAccessibleContext().setAccessibleDescription(dica);
        c.gridx = 0;
        c.gridy = linha;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        add(rotulo, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(campo, c);
    }

    /** Valida o formulário e, se válido, notifica o ouvinte. Nunca conecta com dados inválidos. */
    void confirmar() {
        if (bloqueada) {
            return;
        }
        ValidadorFormularioConexao.Resultado r = ValidadorFormularioConexao.validar(
                campoNome.getText(), campoHost.getText(), campoPorta.getText(),
                campoTimeout.getText(), campoRmiHostname.getText());
        if (!r.valido()) {
            mostrarErro(r.mensagem());
            componenteDe(r.campoInvalido()).requestFocusInWindow();
            return;
        }
        limparMensagem();
        ouvinte.aoConectar(new ParametrosConexao(campoNome.getText().trim(), campoHost.getText().trim(),
                Integer.parseInt(campoPorta.getText().trim()),
                Duration.ofSeconds(Long.parseLong(campoTimeout.getText().trim())),
                campoRmiHostname.getText().trim()));
    }

    private JComponent componenteDe(Campo campo) {
        return switch (campo) {
            case NOME -> campoNome;
            case HOST -> campoHost;
            case PORTA -> campoPorta;
            case TIMEOUT -> campoTimeout;
            case RMI_HOSTNAME -> campoRmiHostname;
        };
    }

    /**
     * Mostra a falha de conexão ou de entrada: a mensagem contém o motivo, o host e a porta, e o
     * formulário é reabilitado com os valores digitados preservados.
     */
    public void mostrarFalha(String motivo, String host, int porta) {
        bloquear(false);
        mostrarErro(mensagemFalha(motivo, host, porta));
        campoNome.requestFocusInWindow();
    }

    /**
     * Bloqueia ou libera o formulário durante a conexão. Bloqueado, desabilita campos e botão e
     * mostra "Conectando...".
     */
    public void bloquear(boolean bloquear) {
        bloqueada = bloquear;
        campoNome.setEnabled(!bloquear);
        campoHost.setEnabled(!bloquear);
        campoPorta.setEnabled(!bloquear);
        campoTimeout.setEnabled(!bloquear);
        campoRmiHostname.setEnabled(!bloquear);
        botaoConectar.setEnabled(!bloquear);
        if (bloquear) {
            rotuloStatus.setText("Conectando...");
            rotuloStatus.getAccessibleContext().setAccessibleDescription("Conectando ao servidor");
        }
    }

    public String nome() {
        return campoNome.getText();
    }

    public String host() {
        return campoHost.getText();
    }

    public String porta() {
        return campoPorta.getText();
    }

    public String timeoutSeg() {
        return campoTimeout.getText();
    }

    public String rmiHostname() {
        return campoRmiHostname.getText();
    }

    /** Texto atual da linha de mensagem (vazio quando não há mensagem). */
    public String mensagem() {
        return rotuloStatus.getText().trim();
    }

    public boolean estaBloqueada() {
        return bloqueada;
    }

    private void mostrarErro(String texto) {
        rotuloStatus.setText(texto);
    }

    private void limparMensagem() {
        rotuloStatus.setText(" ");
    }

    /** Monta a mensagem de falha com motivo, host e porta (texto puro, testável sem Swing). */
    static String mensagemFalha(String motivo, String host, int porta) {
        String m = motivo == null || motivo.isBlank() ? "motivo não informado" : motivo.trim();
        String h = host == null || host.isBlank() ? "(host não informado)" : host.trim();
        return "Erro: não foi possível conectar a " + h + ":" + porta + " - " + m;
    }
}
