package br.cefet.sd.memoria.cliente.modelo;

import br.cefet.sd.memoria.cliente.rmi.CallbackJogadorImpl;
import br.cefet.sd.memoria.cliente.rmi.Despachante;
import br.cefet.sd.memoria.cliente.rmi.ExecutorRmi;
import br.cefet.sd.memoria.cliente.rmi.OuvinteCallback;
import br.cefet.sd.memoria.cliente.rmi.ServicosRemotos;
import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.CatalogoImagensDTO;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import br.cefet.sd.memoria.comum.dto.TipoEvento;
import br.cefet.sd.memoria.comum.dto.VisaoGeralDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import br.cefet.sd.memoria.comum.excecao.JogadaRejeitadaException;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import java.rmi.RemoteException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Orquestra as operações do Cliente sobre o Middleware: {@code entrar}, {@code marcarPronto},
 * {@code selecionar}, {@code responderContinuar}, {@code consultarEstado}, {@code sair} e {@code
 * notificarEncerramento}.
 */
public final class ControladorCliente implements OuvinteCallback {

    /** Mensagem exibida quando uma chamada falha com {@link RemoteException}. */
    public static final String MENSAGEM_CONEXAO_PERDIDA =
            "A conexão com o Middleware foi perdida. Reinicie o Cliente para voltar a jogar.";

    /** Prefixo da mensagem de falha de sincronização. */
    public static final String MENSAGEM_FALHA_SINCRONIZACAO = "Falha na sincronização";

    private static final System.Logger LOG = System.getLogger(ControladorCliente.class.getName());

    /** Prazos de espera de cada tipo de chamada. */
    public record Prazos(Duration selecao, Duration consulta, Duration sincronizacaoInicial,
                         Duration comando, Duration entrada, Duration encerramento) {

        /** Prazos padrão. */
        public static final Prazos PADRAO = new Prazos(Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(2));

        /** Valida que nenhum prazo é nulo, zero ou negativo. */
        public Prazos {
            exigirPositivo(selecao, "selecao");
            exigirPositivo(consulta, "consulta");
            exigirPositivo(sincronizacaoInicial, "sincronizacaoInicial");
            exigirPositivo(comando, "comando");
            exigirPositivo(entrada, "entrada");
            exigirPositivo(encerramento, "encerramento");
        }

        /** Copia estes prazos trocando o prazo de seleção. */
        public Prazos comSelecao(Duration novo) {
            return new Prazos(novo, consulta, sincronizacaoInicial, comando, entrada, encerramento);
        }

        /** Copia estes prazos trocando o prazo de consulta e o da sincronização inicial. */
        public Prazos comConsulta(Duration novo) {
            return new Prazos(selecao, novo, novo, comando, entrada, encerramento);
        }

        private static void exigirPositivo(Duration prazo, String nome) {
            Objects.requireNonNull(prazo, nome);
            if (prazo.isZero() || prazo.isNegative()) {
                throw new IllegalArgumentException(nome + " deve ser positivo: " + prazo);
            }
        }
    }

    private final ServicosRemotos servicos;
    private final ExecutorRmi executor;
    private final Despachante despachante;
    private final OuvinteControlador ouvinte;
    private final Prazos prazos;
    private final ModeloCliente modelo = new ModeloCliente();
    private final ExecutorService vigias = Executors.newCachedThreadPool(new FabricaVigias());

    private CallbackJogadorImpl callbackImpl;
    private volatile String idParticipante;
    private volatile CatalogoImagensDTO catalogo;
    private boolean avisouConexaoPerdida;

    /** Cria o controlador com os prazos padrão. */
    public ControladorCliente(ServicosRemotos servicos, ExecutorRmi executor, Despachante despachante,
                              OuvinteControlador ouvinte) {
        this(servicos, executor, despachante, ouvinte, Prazos.PADRAO);
    }

    /** Cria o controlador. */
    public ControladorCliente(ServicosRemotos servicos, ExecutorRmi executor, Despachante despachante,
                              OuvinteControlador ouvinte, Prazos prazos) {
        this.servicos = Objects.requireNonNull(servicos, "servicos");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.despachante = Objects.requireNonNull(despachante, "despachante");
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.prazos = Objects.requireNonNull(prazos, "prazos");
    }

    /** Modelo aplicado. Deve ser lido apenas na thread de despacho (a EDT em produção). */
    public ModeloCliente modelo() {
        return modelo;
    }

    /** Identificador do Jogador local, atribuído pelo Middleware na entrada. */
    public Optional<String> idParticipante() {
        return Optional.ofNullable(idParticipante);
    }

    /** Identificador da Partida corrente, tomado do último estado público aplicado. */
    public Optional<Long> idPartidaAtual() {
        return modelo.getEstadoPublico().map(EstadoPublicoDTO::idPartida);
    }

    /** Catálogo de imagens recebido na entrada. */
    public Optional<CatalogoImagensDTO> catalogo() {
        return Optional.ofNullable(catalogo);
    }

    /**
     * Cria (uma única vez) o Callback deste Cliente, que entrega as notificações do Middleware a
     * este controlador na thread de despacho.
     */
    public synchronized CallbackJogadorImpl callback() {
        if (callbackImpl == null) {
            callbackImpl = new CallbackJogadorImpl(this, despachante);
        }
        return callbackImpl;
    }

    /** Entra na Sala exportando o Callback deste controlador. */
    public CompletableFuture<Void> entrar(String nome) {
        return entrarInterno(nome, null);
    }

    /**
     * Variante de {@link #entrar(String)} com um Callback já pronto (por exemplo, o utilitário de
     * carga ou um dublê de teste). O chamador é responsável por exportar e desexportar esse
     * Callback.
     */
    public CompletableFuture<Void> entrar(String nome, CallbackJogador callback) {
        return entrarInterno(nome, Objects.requireNonNull(callback, "callback"));
    }

    private CompletableFuture<Void> entrarInterno(String nome, CallbackJogador fornecido) {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (modelo.isConexaoPerdida()) {
            return recusar(conclusao, MENSAGEM_CONEXAO_PERDIDA, true);
        }
        if (idParticipante != null) {
            return recusar(conclusao, "Você já entrou na sala.", false);
        }
        CallbackJogadorImpl proprio = fornecido == null ? callback() : null;
        executar(prazos.entrada(), () -> {
            CallbackJogador stub = fornecido != null ? fornecido : proprio.exportar();
            try {
                return servicos.sala().entrar(nome, stub);
            } catch (RemoteException | ExcecaoDominio | RuntimeException e) {
                if (proprio != null) {
                    proprio.desexportar();
                }
                throw e;
            }
        }, entrada -> aoEntrar(entrada, conclusao), erro -> {
            try {
                if (proprio != null) {
                    proprio.desexportar();
                }
                tratarFalha(erro, "na entrada", prazos.entrada(), false, false);
            } finally {
                conclusao.complete(null);
            }
        });
        return conclusao;
    }

    private void aoEntrar(EntradaSalaDTO entrada, CompletableFuture<Void> conclusao) {
        try {
            idParticipante = entrada.idParticipante();
            catalogo = entrada.catalogo();
            modelo.aplicarSala(entrada.sala());
            ouvinte.entradaAceita(entrada);
            modelo.getSala().ifPresent(ouvinte::atualizarSala);
            ouvinte.mostrarStatus("Você entrou na sala. Aguardando os demais jogadores.");
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Falha ao tratar a entrada na sala", e);
        }
        sincronizar(prazos.sincronizacaoInicial()).whenComplete((v, t) -> conclusao.complete(null));
    }

    /** Marca o Jogador local como pronto. */
    public CompletableFuture<Void> marcarPronto() {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (!podeEnviar(conclusao)) {
            return conclusao;
        }
        String id = idParticipante;
        executar(prazos.comando(), () -> {
            servicos.sala().marcarPronto(id);
            return null;
        }, concluindo(conclusao, r -> ouvinte.mostrarStatus("Você está pronto. Aguardando os demais jogadores.")),
                concluindo(conclusao, erro -> tratarFalha(erro, "ao marcar pronto", prazos.comando(), false, false)));
        return conclusao;
    }

    /**
     * Envia a seleção de uma carta. Não altera o modelo nem a visão: a carta só aparece quando o
     * Callback confirmar.
     */
    public CompletableFuture<Void> selecionar(int posicao) {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (!podeEnviar(conclusao)) {
            return conclusao;
        }
        if (modelo.isFalhaSincronizacao()) {
            return recusar(conclusao, MENSAGEM_FALHA_SINCRONIZACAO
                    + ": seleções desabilitadas até uma consulta de estado bem-sucedida.", true);
        }
        Optional<Long> partida = idPartidaAtual();
        if (partida.isEmpty()) {
            return recusar(conclusao, "Não há partida em andamento.", false);
        }
        String id = idParticipante;
        long idPartida = partida.get();
        executar(prazos.selecao(), () -> {
            servicos.partida().selecionarCarta(id, idPartida, posicao);
            return null;
        }, concluindo(conclusao, r -> {  }),
                concluindo(conclusao, erro -> tratarFalha(erro, "no envio da seleção", prazos.selecao(), false, true)));
        return conclusao;
    }

    /**
     * Envia a resposta à pergunta "deseja continuar?". Após o envio as opções são desabilitadas por
     * {@link OuvinteControlador#perguntaRespondida()}.
     */
    public CompletableFuture<Void> responderContinuar(boolean continuar) {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (!podeEnviar(conclusao)) {
            return conclusao;
        }
        String id = idParticipante;
        executar(prazos.comando(), () -> {
            servicos.sessao().responderContinuar(id, continuar);
            return null;
        }, concluindo(conclusao, r -> {
            ouvinte.perguntaRespondida();
            ouvinte.mostrarStatus(continuar ? "Resposta enviada: continuar. Aguardando os demais jogadores."
                    : "Resposta enviada: encerrar.");
        }), concluindo(conclusao, erro -> {
            if (erro instanceof ExcecaoDominio) {
                ouvinte.perguntaRespondida();
            }
            tratarFalha(erro, "ao responder", prazos.comando(), false, false);
        }));
        return conclusao;
    }

    /**
     * Consulta o estado atual e o aplica ao modelo. Em caso de sucesso, aplica Sala e Partida (só o
     * que for mais novo que o já aplicado) e limpa {@code falhaSincronizacao}.
     */
    public CompletableFuture<Void> consultarEstado() {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (!podeEnviar(conclusao)) {
            return conclusao;
        }
        return sincronizar(prazos.consulta());
    }

    private CompletableFuture<Void> sincronizar(Duration prazo) {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        String id = idParticipante;
        executar(prazo, () -> servicos.partida().consultarEstado(id),
                concluindo(conclusao, this::aplicarVisaoGeral),
                concluindo(conclusao, erro -> aoFalharSincronizacao(erro, prazo)));
        return conclusao;
    }

    private void aplicarVisaoGeral(VisaoGeralDTO visao) {
        boolean estavaComFalha = modelo.isFalhaSincronizacao();
        modelo.setFalhaSincronizacao(false);
        modelo.aplicarSala(visao.sala());
        modelo.getSala().ifPresent(ouvinte::atualizarSala);
        if (visao.partida() != null) {
            modelo.aplicar(visao.partida());
        }
        atualizarVisao();
        modelo.getEstadoPublico().ifPresent(e -> ouvinte.atualizarPlacar(e.placar()));
        ouvinte.mostrarStatus(estavaComFalha ? "Sincronização concluída: seleções reabilitadas."
                : MensagensStatus.paraEvento(TipoEvento.CONSULTA));
    }

    private void aoFalharSincronizacao(Throwable erro, Duration prazo) {
        modelo.setFalhaSincronizacao(true);
        atualizarVisao();
        if (erro instanceof RemoteException remota) {
            ouvinte.mostrarErro(MENSAGEM_FALHA_SINCRONIZACAO + ": " + descrever(remota));
            tratarFalhaRemotaNaDespacho(remota);
        } else {
            tratarFalha(erro, "na sincronização", prazo, true, true);
        }
    }

    /**
     * Sai da Sala e, em caso de sucesso, desexporta o Callback. Se o Middleware rejeitar (por
     * exemplo, durante uma Partida), o motivo é exibido e o Jogador continua na Sala.
     */
    public CompletableFuture<Void> sair() {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        if (!podeEnviar(conclusao)) {
            return conclusao;
        }
        String id = idParticipante;
        executar(prazos.comando(), () -> {
            servicos.sala().sair(id);
            return null;
        }, concluindo(conclusao, r -> {
            liberarCallback();
            idParticipante = null;
            catalogo = null;
            ouvinte.saiu();
        }), concluindo(conclusao, erro -> tratarFalha(erro, "ao sair da sala", prazos.comando(), false, false)));
        return conclusao;
    }

    /** Informa ao Middleware que o Cliente está sendo encerrado. */
    public CompletableFuture<Void> notificarEncerramento() {
        CompletableFuture<Void> conclusao = new CompletableFuture<>();
        String id = idParticipante;
        if (id == null || modelo.isConexaoPerdida()) {
            liberarCallback();
            conclusao.complete(null);
            return conclusao;
        }
        Future<Void> chamada;
        try {
            chamada = executor.submit(() -> {
                servicos.sala().notificarEncerramentoCliente(id);
                return null;
            });
        } catch (RejectedExecutionException e) {
            liberarCallback();
            conclusao.complete(null);
            return conclusao;
        }
        try {
            vigias.execute(() -> {
                try {
                    chamada.get(prazos.encerramento().toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException | ExecutionException e) {
                    chamada.cancel(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    chamada.cancel(true);
                } finally {
                    liberarCallback();
                    conclusao.complete(null);
                }
            });
        } catch (RejectedExecutionException e) {
            chamada.cancel(true);
            liberarCallback();
            conclusao.complete(null);
        }
        return conclusao;
    }

    /**
     * Libera os recursos próprios do controlador (threads de espera e Callback). Não encerra o
     * {@link ExecutorRmi}, que pertence a quem o criou.
     */
    public void encerrar() {
        liberarCallback();
        vigias.shutdownNow();
    }

    /**
     * Trata uma {@link RemoteException} de qualquer chamada RMI: marca {@code conexaoPerdida} no
     * modelo, desabilita as ações de jogo (redesenhando o tabuleiro e avisando {@link
     * OuvinteControlador#conexaoPerdida()}) e exibe {@link #MENSAGEM_CONEXAO_PERDIDA}.
     */
    public void tratarFalhaRemota(RemoteException falha) {
        Objects.requireNonNull(falha, "falha");
        ui(() -> tratarFalhaRemotaNaDespacho(falha));
    }

    private void tratarFalhaRemotaNaDespacho(RemoteException falha) {
        LOG.log(System.Logger.Level.WARNING, "Falha remota: " + descrever(falha));
        modelo.setConexaoPerdida(true);
        atualizarVisao();
        ouvinte.mostrarErro(MENSAGEM_CONEXAO_PERDIDA);
        if (!avisouConexaoPerdida) {
            avisouConexaoPerdida = true;
            ouvinte.conexaoPerdida();
        }
    }

    /** Trata uma falha de comando na thread de despacho. */
    private void tratarFalha(Throwable erro, String acao, Duration prazo, boolean comoErro,
                             boolean mantemTabuleiro) {
        if (erro instanceof RemoteException remota) {
            tratarFalhaRemotaNaDespacho(remota);
        } else if (erro instanceof JogadaRejeitadaException rejeitada) {
            ouvinte.mostrarErro(textoRejeicao(rejeitada));
        } else if (erro instanceof ExcecaoDominio dominio) {
            ouvinte.mostrarErro(textoDominio(dominio));
        } else {
            String motivo = erro instanceof TimeoutException
                    ? "tempo esgotado (" + formatar(prazo) + ")"
                    : descrever(erro);
            String mensagem = (comoErro ? MENSAGEM_FALHA_SINCRONIZACAO : "Falha " + acao) + ": " + motivo
                    + (mantemTabuleiro ? ". O tabuleiro foi mantido." : ".");
            if (comoErro) {
                ouvinte.mostrarErro(mensagem);
            } else {
                ouvinte.mostrarStatus(mensagem);
            }
        }
    }

    private static String formatar(Duration prazo) {
        long ms = prazo.toMillis();
        return ms % 1000 == 0 ? ms / 1000 + " s" : ms + " ms";
    }

    private static String textoRejeicao(JogadaRejeitadaException e) {
        return "Jogada rejeitada: " + e.getMotivo().getMensagem();
    }

    private static String textoDominio(ExcecaoDominio e) {
        String mensagem = e.getMessage();
        return mensagem == null || mensagem.isBlank() ? e.getCodigo().name() : mensagem;
    }

    private static String descrever(Throwable erro) {
        String mensagem = erro.getMessage();
        return mensagem == null || mensagem.isBlank()
                ? erro.getClass().getSimpleName()
                : erro.getClass().getSimpleName() + ": " + mensagem;
    }

    @Override
    public void aoAtualizarSala(SalaDTO sala) {
        if (modelo.aplicarSala(sala)) {
            ouvinte.atualizarSala(sala);
        }
    }

    @Override
    public void aoDesconectarJogador(DesconexaoDTO desconexao) {
        if (modelo.aplicarDesconexao(desconexao)) {
            ouvinte.mostrarStatus(MensagensStatus.desconexao(desconexao.nomeJogador()));
        }
    }

    @Override
    public void aoAtualizarTabuleiro(EstadoPublicoDTO estado) {
        boolean haviaPartida = modelo.temPartidaAplicada();
        if (!modelo.aplicar(estado)) {
            return;
        }
        atualizarVisao();
        ouvinte.atualizarPlacar(estado.placar());
        ouvinte.mostrarStatus(mensagemDoEstado(estado, haviaPartida));
    }

    @Override
    public void aoMudarTurno(MudancaTurnoDTO mudanca) {
        if (modelo.aplicar(mudanca)) {
            boolean minhaVez = mudanca.idJogadorDaVez().equals(idParticipante);
            ouvinte.mudouTurno(mudanca, minhaVez);
            ouvinte.mostrarStatus(MensagensStatus.mudancaDeVez(mudanca.nomeJogadorDaVez()));
        }
    }

    @Override
    public void aoAtualizarPlacar(PlacarDTO placar) {
        if (modelo.aplicar(placar)) {
            ouvinte.atualizarPlacar(placar.placar());
        }
    }

    @Override
    public void aoFinalizarPartida(ResultadoPartidaDTO resultado) {
        if (modelo.aplicar(resultado)) {
            ouvinte.mostrarStatus(resultado.empate()
                    ? MensagensStatus.empate()
                    : MensagensStatus.vitoria(resultado.nomesVencedores().get(0)));
            ouvinte.mostrarResultado(resultado);
        }
    }

    @Override
    public void aoPerguntarContinuar(PerguntaContinuarDTO pergunta) {
        if (modelo.aplicar(pergunta)) {
            ouvinte.mostrarPergunta(pergunta);
        }
    }

    @Override
    public void aoCancelarPartida(CancelamentoPartidaDTO cancelamento) {
        if (modelo.aplicar(cancelamento)) {
            ouvinte.mostrarStatus(MensagensStatus.cancelamento(
                    cancelamento.nomeJogadorDesconectado(), cancelamento.causa()));
            ouvinte.partidaCancelada(cancelamento);
        }
    }

    @Override
    public void aoEncerrarSessao(SumarioSessaoDTO sumario) {
        if (modelo.aplicar(sumario)) {
            ouvinte.mostrarStatus("A sessão foi encerrada.");
            ouvinte.mostrarSumario(sumario);
        }
    }

    private String mensagemDoEstado(EstadoPublicoDTO estado, boolean haviaPartida) {
        String jogador = estado.nomeJogadorEvento();
        return switch (estado.ultimoEvento()) {
            case PAR_ENCONTRADO -> MensagensStatus.acerto(jogador);
            case ERRO -> MensagensStatus.erro(jogador);
            case INICIO_PARTIDA -> haviaPartida ? MensagensStatus.reinicio()
                    : MensagensStatus.paraEvento(TipoEvento.INICIO_PARTIDA);
            default -> MensagensStatus.paraEvento(estado.ultimoEvento());
        };
    }

    /**
     * Recalcula a visão do tabuleiro a partir do modelo e a entrega à interface, se houver estado.
     */
    private void atualizarVisao() {
        modelo.getEstadoPublico().ifPresent(estado -> ouvinte.atualizarVisao(
                VisaoTabuleiro.de(estado, idParticipante, modelo.isConexaoPerdida(),
                        modelo.isFalhaSincronizacao()), estado));
    }

    /**
     * Verifica se um comando remoto pode ser enviado (Jogador na Sala e conexão ativa). Se não,
     * exibe o motivo e completa o futuro.
     */
    private boolean podeEnviar(CompletableFuture<Void> conclusao) {
        if (modelo.isConexaoPerdida()) {
            recusar(conclusao, MENSAGEM_CONEXAO_PERDIDA, true);
            return false;
        }
        if (idParticipante == null) {
            recusar(conclusao, "Entre na sala antes de continuar.", false);
            return false;
        }
        return true;
    }

    private CompletableFuture<Void> recusar(CompletableFuture<Void> conclusao, String mensagem,
                                            boolean comoErro) {
        ui(() -> {
            try {
                if (comoErro) {
                    ouvinte.mostrarErro(mensagem);
                } else {
                    ouvinte.mostrarStatus(mensagem);
                }
            } finally {
                conclusao.complete(null);
            }
        });
        return conclusao;
    }

    private synchronized void liberarCallback() {
        if (callbackImpl != null) {
            callbackImpl.desexportar();
            callbackImpl = null;
        }
    }

    private <R> Consumer<R> concluindo(CompletableFuture<Void> conclusao, Consumer<R> tratador) {
        return valor -> {
            try {
                tratador.accept(valor);
            } finally {
                conclusao.complete(null);
            }
        };
    }

    /**
     * Executa {@code chamada} no {@link ExecutorRmi}, aguarda por {@code prazo} em uma thread vigia
     * (nunca na do chamador) e entrega o desfecho, na thread de despacho, a {@code aoSucesso} ou
     * {@code aoFalha}.
     */
    private <R> void executar(Duration prazo, Callable<R> chamada, Consumer<R> aoSucesso,
                              Consumer<Throwable> aoFalha) {
        Future<R> futuro;
        try {
            futuro = executor.submit(chamada);
        } catch (RejectedExecutionException e) {
            ui(() -> aoFalha.accept(e));
            return;
        }
        try {
            vigias.execute(() -> aguardar(futuro, prazo, aoSucesso, aoFalha));
        } catch (RejectedExecutionException e) {
            futuro.cancel(true);
            ui(() -> aoFalha.accept(e));
        }
    }

    private <R> void aguardar(Future<R> futuro, Duration prazo, Consumer<R> aoSucesso,
                              Consumer<Throwable> aoFalha) {
        R resultado = null;
        Throwable erro = null;
        try {
            resultado = futuro.get(prazo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            erro = e;
        } catch (ExecutionException e) {
            erro = e.getCause() != null ? e.getCause() : e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futuro.cancel(true);
            erro = e;
        }
        R valor = resultado;
        Throwable falha = erro;
        ui(() -> {
            if (falha == null) {
                aoSucesso.accept(valor);
            } else {
                aoFalha.accept(falha);
            }
        });
    }

    /**
     * Entrega a tarefa à thread de despacho; exceções da tarefa são registradas, não propagadas.
     */
    private void ui(Runnable tarefa) {
        try {
            despachante.despachar(() -> {
                try {
                    tarefa.run();
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.ERROR, "Falha ao atualizar a interface", e);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Falha ao despachar atualização da interface", e);
        }
    }

    /** Fábrica de threads daemon "vigia-cliente-N" que aguardam as chamadas RMI. */
    private static final class FabricaVigias implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger contador = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable tarefa) {
            Thread thread = new Thread(tarefa, "vigia-cliente-" + contador.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
