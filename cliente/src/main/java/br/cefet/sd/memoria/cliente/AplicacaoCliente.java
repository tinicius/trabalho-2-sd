package br.cefet.sd.memoria.cliente;

import br.cefet.sd.memoria.cliente.modelo.ControladorCliente;
import br.cefet.sd.memoria.cliente.modelo.OuvinteControlador;
import br.cefet.sd.memoria.cliente.modelo.ParametrosConexao;
import br.cefet.sd.memoria.cliente.modelo.VisaoTabuleiro;
import br.cefet.sd.memoria.cliente.rmi.ConexaoRmi;
import br.cefet.sd.memoria.cliente.rmi.ConexaoRmiException;
import br.cefet.sd.memoria.cliente.rmi.Despachante;
import br.cefet.sd.memoria.cliente.rmi.ExecutorRmi;
import br.cefet.sd.memoria.cliente.rmi.ServicosRemotos;
import br.cefet.sd.memoria.cliente.ui.BarraStatus;
import br.cefet.sd.memoria.cliente.ui.DialogoResultado;
import br.cefet.sd.memoria.cliente.ui.PainelResultado;
import br.cefet.sd.memoria.cliente.ui.TelaConexao;
import br.cefet.sd.memoria.cliente.ui.TelaJogo;
import br.cefet.sd.memoria.cliente.ui.TelaSala;
import br.cefet.sd.memoria.cliente.ui.TelaSumario;
import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Janela única do Cliente: liga as telas ao {@link ControladorCliente} e cuida do ciclo de vida.
 */
public final class AplicacaoCliente {

    static final String CARTA_CONEXAO = "conexao";
    static final String CARTA_SALA = "sala";
    static final String CARTA_JOGO = "jogo";
    static final String CARTA_SUMARIO = "sumario";

    private static final System.Logger LOG = System.getLogger(AplicacaoCliente.class.getName());
    private static final long ESPERA_ENCERRAMENTO_SEG = 2;

    private static final String PROPRIEDADE_RMI_HOSTNAME = "java.rmi.server.hostname";

    private final JFrame janela = new JFrame("Jogo da Memória");
    private final CardLayout cartas = new CardLayout();
    private final JPanel raiz = new JPanel(cartas);

    private TelaConexao telaConexao;
    private TelaSala telaSala;
    private BarraStatus statusSala;
    private TelaJogo telaJogo;
    private TelaSumario telaSumario;

    private PainelResultado painelResultado;
    private JDialog dialogoPerda;

    private Sessao sessao;
    private String ultimoNome = "";
    private String ultimoHost = TelaConexao.HOST_PADRAO;
    private String ultimaPorta = TelaConexao.PORTA_PADRAO;
    private String ultimoTimeoutSeg = TelaConexao.TIMEOUT_PADRAO_SEG;
    private String ultimoRmiHostname = System.getProperty(PROPRIEDADE_RMI_HOSTNAME, "");
    /**
     * O RMI lê {@code java.rmi.server.hostname} uma única vez por processo (ao carregar suas
     * classes de transporte).
     */
    private boolean rmiHostnameFixado = System.getProperty(PROPRIEDADE_RMI_HOSTNAME) != null;
    private String cartaAtual = CARTA_CONEXAO;
    private boolean encerrando;

    /** Cria a aplicação (sem exibir a janela). Deve ser chamado na EDT. */
    public AplicacaoCliente() {
    }

    /** Monta e exibe a janela com a tela de conexão. Deve ser chamado na EDT. */
    public void iniciar() {
        janela.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        janela.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                encerrarAplicacao();
            }
        });
        janela.setContentPane(raiz);
        montarTelas();
        janela.pack();
        janela.setLocationRelativeTo(null);
        janela.setVisible(true);
    }

    /** (Re)cria todas as telas, preservando os últimos valores do formulário de conexão. */
    private void montarTelas() {
        if (telaJogo != null) {
            telaJogo.encerrar();
        }
        if (statusSala != null) {
            statusSala.parar();
        }
        raiz.removeAll();

        telaConexao = new TelaConexao(this::aoConectar, ultimoNome, ultimoHost, ultimaPorta,
                ultimoTimeoutSeg, ultimoRmiHostname, rmiHostnameFixado);
        telaSala = new TelaSala(this::aoMarcarPronto);
        statusSala = new BarraStatus();
        JPanel painelSala = new JPanel(new BorderLayout());
        painelSala.add(telaSala, BorderLayout.CENTER);
        painelSala.add(statusSala, BorderLayout.SOUTH);
        telaJogo = new TelaJogo(this::aoSelecionar);
        telaSumario = new TelaSumario(this::reiniciar);

        raiz.add(telaConexao, CARTA_CONEXAO);
        raiz.add(painelSala, CARTA_SALA);
        raiz.add(telaJogo, CARTA_JOGO);
        raiz.add(telaSumario, CARTA_SUMARIO);
        trocar(CARTA_CONEXAO);
        raiz.revalidate();
        raiz.repaint();
    }

    private void trocar(String carta) {
        cartaAtual = carta;
        cartas.show(raiz, carta);
    }

    private void aoConectar(ParametrosConexao parametros) {
        if (sessao != null) {
            return;
        }
        String nome = parametros.nome();
        String host = parametros.host();
        int porta = parametros.porta();
        Duration timeout = parametros.timeout();
        ultimoNome = nome;
        ultimoHost = host;
        ultimaPorta = Integer.toString(porta);
        ultimoTimeoutSeg = Long.toString(timeout.toSeconds());
        aplicarRmiHostname(parametros.rmiHostname());
        telaConexao.bloquear(true);
        Sessao nova = new Sessao();
        sessao = nova;
        new SwingWorker<ServicosRemotos, Void>() {
            @Override
            protected ServicosRemotos doInBackground() throws ConexaoRmiException {
                return ConexaoRmi.conectar(host, porta, timeout);
            }

            @Override
            protected void done() {
                aoConexaoConcluida(this, nova, nome, host, porta);
            }
        }.execute();
    }

    /**
     * Aplica o endereço deste computador como {@code java.rmi.server.hostname} antes de qualquer
     * classe de transporte do RMI ser carregada.
     */
    private void aplicarRmiHostname(String rmiHostname) {
        if (rmiHostnameFixado) {
            return;
        }
        rmiHostnameFixado = true;
        ultimoRmiHostname = rmiHostname;
        if (!rmiHostname.isEmpty()) {
            System.setProperty(PROPRIEDADE_RMI_HOSTNAME, rmiHostname);
        }
    }

    private void aoConexaoConcluida(SwingWorker<ServicosRemotos, Void> tarefa, Sessao s, String nome,
                                    String host, int porta) {
        if (sessao != s || s.encerrada) {
            return;
        }
        ServicosRemotos servicos;
        try {
            servicos = tarefa.get();
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            falharConexao(s, causa instanceof ConexaoRmiException c ? motivoDe(c) : descrever(causa),
                    host, porta);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            falharConexao(s, "conexão interrompida", host, porta);
            return;
        }
        s.controlador = new ControladorCliente(servicos, s.executor, Despachante.EDT, s);
        s.controlador.entrar(nome).whenComplete((v, t) -> naEdt(() -> aoEntradaConcluida(s, host, porta)));
    }

    private void aoEntradaConcluida(Sessao s, String host, int porta) {
        if (sessao != s || s.encerrada || s.ativa) {
            return;
        }
        // Entrada rejeitada (domínio) ou falhou: o controlador já informou o motivo ao ouvinte.
        String motivo = s.ultimaMensagem.isBlank() ? "a entrada na sala falhou" : s.ultimaMensagem;
        falharConexao(s, motivo, host, porta);
    }

    private void falharConexao(Sessao s, String motivo, String host, int porta) {
        if (sessao == s) {
            sessao = null;
        }
        s.encerrar();
        telaConexao.mostrarFalha(motivo, host, porta);
        trocar(CARTA_CONEXAO);
    }

    private void aoMarcarPronto() {
        Sessao s = sessao;
        if (s == null || !s.ativa) {
            telaSala.reabilitarPronto();
            return;
        }
        int errosAntes = s.falhas;
        s.controlador.marcarPronto().whenComplete((v, t) -> naEdt(() -> {
            if (s.falhas != errosAntes) {
                telaSala.reabilitarPronto();
            }
        }));
    }

    private void aoSelecionar(int posicao) {
        Sessao s = sessao;
        if (s != null && s.ativa) {
            s.controlador.selecionar(posicao);
        }
    }

    private void aoResponderContinuar(boolean continuar) {
        Sessao s = sessao;
        if (s != null && s.ativa) {
            s.controlador.responderContinuar(continuar);
        }
    }

    /** Descarta a sessão atual e volta à tela de conexão com novo controlador e novo executor. */
    private void reiniciar() {
        Sessao s = sessao;
        sessao = null;
        if (s != null) {
            s.encerrar();
        }
        fecharResultado();
        fecharAvisoPerda();
        montarTelas();
        janela.pack();
    }

    private PainelResultado painelResultado() {
        if (painelResultado == null) {
            painelResultado = new PainelResultado(this::aoResponderContinuar);
            JDialog dialogo = DialogoResultado.criar(janela, painelResultado);
            dialogo.setVisible(true);
        }
        return painelResultado;
    }

    private void fecharResultado() {
        if (painelResultado != null) {
            painelResultado.fechar();
            painelResultado = null;
        }
    }

    private void oferecerVoltarAConexao() {
        if (dialogoPerda != null) {
            return;
        }
        JDialog dialogo = new JDialog(janela, "Conexão perdida", java.awt.Dialog.ModalityType.MODELESS);
        dialogo.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JLabel texto = new JLabel("<html>A conexão com o Middleware foi perdida.<br>"
                + "Deseja voltar à tela de conexão?</html>");
        texto.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        JButton voltar = new JButton("Voltar à conexão");
        voltar.setMnemonic('V');
        voltar.addActionListener(e -> reiniciar());
        JButton permanecer = new JButton("Permanecer aqui");
        permanecer.setMnemonic('P');
        permanecer.addActionListener(e -> fecharAvisoPerda());
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        botoes.add(voltar);
        botoes.add(permanecer);
        dialogo.getContentPane().add(texto, BorderLayout.CENTER);
        dialogo.getContentPane().add(botoes, BorderLayout.SOUTH);
        dialogo.pack();
        dialogo.setLocationRelativeTo(janela);
        dialogoPerda = dialogo;
        dialogo.setVisible(true);
    }

    private void fecharAvisoPerda() {
        if (dialogoPerda != null) {
            dialogoPerda.dispose();
            dialogoPerda = null;
        }
    }

    /**
     * Fechamento da janela: avisa o Middleware, esperando no máximo 2 s, e encerra a JVM. A espera
     * ocorre fora da EDT.
     */
    private void encerrarAplicacao() {
        if (encerrando) {
            return;
        }
        encerrando = true;
        janela.setVisible(false);
        Sessao s = sessao;
        CompletableFuture<Void> aviso = s != null && s.controlador != null && !s.sumarioMostrado
                ? s.controlador.notificarEncerramento()
                : CompletableFuture.completedFuture(null);
        Thread espera = new Thread(() -> {
            try {
                aviso.get(ESPERA_ENCERRAMENTO_SEG, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                LOG.log(System.Logger.Level.DEBUG, "Aviso de encerramento não confirmado: " + e);
            } finally {
                System.exit(0);
            }
        }, "encerramento-cliente");
        espera.start();
    }

    /**
     * Extrai o motivo de uma {@link ConexaoRmiException}, cuja mensagem já cita host e porta, para
     * que {@link TelaConexao#mostrarFalha} não os repita.
     */
    static String motivoDe(ConexaoRmiException e) {
        String marcador = e.getHost() + ":" + e.getPorta() + ": ";
        String mensagem = e.getMessage() == null ? "" : e.getMessage();
        int indice = mensagem.indexOf(marcador);
        String motivo = indice >= 0 ? mensagem.substring(indice + marcador.length()) : mensagem;
        return motivo.isBlank() ? "motivo não informado" : motivo;
    }

    private static String descrever(Throwable erro) {
        String mensagem = erro.getMessage();
        return mensagem == null || mensagem.isBlank()
                ? erro.getClass().getSimpleName()
                : erro.getClass().getSimpleName() + ": " + mensagem;
    }

    private static void naEdt(Runnable tarefa) {
        if (SwingUtilities.isEventDispatchThread()) {
            tarefa.run();
        } else {
            SwingUtilities.invokeLater(tarefa);
        }
    }

    /**
     * Uma sessão de uso: executor, controlador e o ouvinte que roteia os eventos às telas. Todos os
     * métodos de {@link OuvinteControlador} rodam na EDT e são ignorados depois de {@link
     * #encerrar()}.
     */
    private final class Sessao implements OuvinteControlador {

        final ExecutorRmi executor = new ExecutorRmi();
        ControladorCliente controlador;
        boolean ativa;
        boolean encerrada;
        boolean perdida;
        boolean sumarioMostrado;
        boolean jogoJaDimensionado;
        String ultimaMensagem = "";
        int falhas;
        long partidaDoResultado = -1;
        List<PlacarJogadorDTO> placar = List.of();

        void encerrar() {
            encerrada = true;
            ativa = false;
            if (controlador != null) {
                controlador.encerrar();
            }
            executor.shutdown();
        }

        @Override
        public void mostrarStatus(String mensagem) {
            if (encerrada) {
                return;
            }
            ultimaMensagem = mensagem;
            if (mensagem != null && mensagem.startsWith("Falha")) {
                falhas++;
            }
            if (!ativa) {
                return;
            }
            if (perdida) {
                mostrarPerda();
                return;
            }
            statusSala.mostrar(mensagem);
            telaJogo.mostrarStatus(mensagem);
        }

        @Override
        public void mostrarErro(String mensagem) {
            if (encerrada) {
                return;
            }
            ultimaMensagem = mensagem;
            falhas++;
            if (!ativa) {
                return;
            }
            if (perdida) {
                mostrarPerda();
                return;
            }
            statusSala.mostrarErro(mensagem);
            telaJogo.mostrarErro(mensagem);
        }

        private void mostrarPerda() {
            statusSala.mostrarFixa(ControladorCliente.MENSAGEM_CONEXAO_PERDIDA);
            telaJogo.mostrarStatusFixo(ControladorCliente.MENSAGEM_CONEXAO_PERDIDA);
        }

        @Override
        public void entradaAceita(EntradaSalaDTO entrada) {
            if (encerrada) {
                return;
            }
            ativa = true;
            telaSala.definirMeuId(entrada.idParticipante());
            telaSala.atualizarSala(entrada.sala());
            trocar(CARTA_SALA);
            janela.pack();
        }

        @Override
        public void atualizarSala(SalaDTO sala) {
            if (!encerrada) {
                telaSala.atualizarSala(sala);
            }
        }

        @Override
        public void atualizarVisao(VisaoTabuleiro visao, EstadoPublicoDTO estado) {
            if (encerrada || !ativa) {
                return;
            }
            telaJogo.aplicar(visao, estado, controlador.catalogo().orElse(null));
            placar = estado.placar();
            if (estado.status() == StatusPartida.EM_ANDAMENTO) {
                if (estado.idPartida() > partidaDoResultado) {
                    fecharResultado();
                }
                if (!CARTA_JOGO.equals(cartaAtual) && !sumarioMostrado) {
                    trocar(CARTA_JOGO);
                    if (!jogoJaDimensionado) {
                        jogoJaDimensionado = true;
                        janela.pack();
                    }
                }
            }
        }

        @Override
        public void atualizarPlacar(List<PlacarJogadorDTO> novoPlacar) {
            if (encerrada) {
                return;
            }
            placar = novoPlacar;
            telaJogo.atualizarPlacar(novoPlacar, idDaVez());
        }

        @Override
        public void mudouTurno(MudancaTurnoDTO mudanca, boolean minhaVez) {
            if (!encerrada && !placar.isEmpty()) {
                telaJogo.atualizarPlacar(placar, mudanca.idJogadorDaVez());
            }
        }

        private String idDaVez() {
            return controlador.modelo().getUltimaMudancaTurno().map(MudancaTurnoDTO::idJogadorDaVez)
                    .or(() -> controlador.modelo().getEstadoPublico().map(EstadoPublicoDTO::idJogadorDaVez))
                    .orElse(null);
        }

        @Override
        public void mostrarResultado(ResultadoPartidaDTO resultado) {
            if (encerrada) {
                return;
            }
            partidaDoResultado = resultado.idPartida();
            painelResultado().mostrarResultado(resultado);
        }

        @Override
        public void mostrarPergunta(PerguntaContinuarDTO pergunta) {
            if (encerrada) {
                return;
            }
            partidaDoResultado = Math.max(partidaDoResultado, pergunta.idPartida());
            painelResultado().habilitarResposta(pergunta);
        }

        @Override
        public void partidaCancelada(CancelamentoPartidaDTO cancelamento) {
            if (encerrada) {
                return;
            }
            partidaDoResultado = Math.max(partidaDoResultado, cancelamento.idPartida());
            fecharResultado();
            if (CARTA_JOGO.equals(cartaAtual)) {
                trocar(CARTA_SALA);
            }
        }

        @Override
        public void mostrarSumario(SumarioSessaoDTO sumario) {
            if (encerrada) {
                return;
            }
            sumarioMostrado = true;
            fecharResultado();
            telaSumario.mostrarSumario(sumario);
            trocar(CARTA_SUMARIO);
            janela.pack();
        }

        @Override
        public void conexaoPerdida() {
            // Antes da entrada ser aceita, a falha segue pelo fluxo de TelaConexao.mostrarFalha.
            if (encerrada || !ativa) {
                return;
            }
            perdida = true;
            fecharResultado();
            telaJogo.desabilitarTudo();
            mostrarPerda();
            if (!sumarioMostrado) {
                oferecerVoltarAConexao();
            }
        }
    }
}
