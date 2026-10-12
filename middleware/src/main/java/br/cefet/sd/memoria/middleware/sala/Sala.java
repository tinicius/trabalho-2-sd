package br.cefet.sd.memoria.middleware.sala;

import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.CatalogoImagensDTO;
import br.cefet.sd.memoria.comum.dto.CausaDesconexao;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.dto.FaseSala;
import br.cefet.sd.memoria.comum.dto.ParticipanteDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import br.cefet.sd.memoria.comum.dto.TipoEvento;
import br.cefet.sd.memoria.comum.dto.VisaoGeralDTO;
import br.cefet.sd.memoria.comum.excecao.CodigoErro;
import br.cefet.sd.memoria.comum.excecao.EntradaRejeitadaException;
import br.cefet.sd.memoria.comum.excecao.JogadaRejeitadaException;
import br.cefet.sd.memoria.comum.excecao.MotivoRejeicao;
import br.cefet.sd.memoria.comum.excecao.OperacaoInvalidaException;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import br.cefet.sd.memoria.middleware.entrega.DistribuidorEventos;
import br.cefet.sd.memoria.middleware.entrega.EntregadorJogador;
import br.cefet.sd.memoria.middleware.entrega.EventoCallback;
import br.cefet.sd.memoria.middleware.nucleo.AcaoTemporizador;
import br.cefet.sd.memoria.middleware.nucleo.ContadorPartida;
import br.cefet.sd.memoria.middleware.nucleo.EventoPartida;
import br.cefet.sd.memoria.middleware.nucleo.GeradorMatriz;
import br.cefet.sd.memoria.middleware.nucleo.Partida;
import br.cefet.sd.memoria.middleware.nucleo.ResultadoJogadorPartida;
import br.cefet.sd.memoria.middleware.nucleo.ResultadoSelecao;
import br.cefet.sd.memoria.middleware.nucleo.ResultadoTemporizador;
import br.cefet.sd.memoria.middleware.nucleo.Sessao;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Sala: dona do único lock e de todo o estado oficial (fase, Jogadores, Sessão e Partida).
 * Nenhuma I/O de rede ocorre sob o lock; notificações são apenas enfileiradas no distribuidor.
 */
public class Sala {

    /** Jogador registrado. Campos mutáveis só são lidos ou alterados sob {@link Sala#lock}. */
    static final class Jogador {
        /** Identificador (UUID) devolvido ao Cliente; funciona como credencial de sessão. */
        final String id;
        /** Nome já sem espaços nas extremidades. */
        final String nome;
        /** Ordem de entrada, crescente na vida da Sala. */
        final long ordemEntrada;
        /** Callback remoto do Cliente. */
        final CallbackJogador callback;
        /** Fila e worker de entrega deste Jogador. */
        final EntregadorJogador entregador;
        /** Se o Jogador marcou prontidão no lobby. */
        boolean pronto;
        /** Marca de desconexão já detectada. */
        boolean desconectado;

        Jogador(String id, String nome, long ordemEntrada, CallbackJogador callback,
                EntregadorJogador entregador) {
            this.id = id;
            this.nome = nome;
            this.ordemEntrada = ordemEntrada;
            this.callback = callback;
            this.entregador = entregador;
        }
    }

    /** Vaga provisória enquanto o {@code ping} da entrada é feito fora do lock. */
    static final class Reserva {
        final String idTemporario = UUID.randomUUID().toString();
        final String nome;
        final CallbackJogador callback;

        Reserva(String nome, CallbackJogador callback) {
            this.nome = nome;
            this.callback = callback;
        }
    }

    /** Coleta de respostas sobre continuar: quem deve responder, o que já respondeu e a época do prazo. */
    static final class ColetaContinuar {
        /** Ids dos Jogadores de quem se espera resposta (os presentes quando a coleta abriu). */
        final Set<String> esperados;
        /** Respostas já recebidas ({@code true} = continuar); a primeira de cada Jogador prevalece. */
        final Map<String, Boolean> respostas = new LinkedHashMap<>();
        /** Época de nível de Sala com que o prazo foi armado. */
        final long epoca;

        ColetaContinuar(Set<String> esperados, long epoca) {
            this.esperados = esperados;
            this.epoca = epoca;
        }
    }

    /** Limite superior do tamanho do nome, em pontos de código. */
    static final int TAMANHO_MAX_NOME = 30;

    /** O único lock da Sala (justo). Todo acesso ao estado oficial ocorre sob ele. */
    final ReentrantLock lock = new ReentrantLock(true);

    private final ConfiguracaoSala configuracao;
    private final ExecutorService poolCallbacks;
    private final Agendador agendador;
    private final Relogio relogio;
    private final RegistroEventosSala registro;
    private final DistribuidorEventos distribuidor = new DistribuidorEventos();
    private final Temporizadores temporizadores;
    private final CatalogoImagensDTO catalogoDto;

    // ---- estado oficial: somente sob lock ----
    private FaseSala fase = FaseSala.LOBBY;
    private final LinkedHashMap<String, Jogador> jogadores = new LinkedHashMap<>();
    private final Map<String, Reserva> reservas = new LinkedHashMap<>();
    private Sessao sessao;
    private Partida partida;
    private long versaoSala;
    /** Token de época de nível de Sala (prazo de continuar). */
    private long epoca;
    private long ultimaOrdemEntrada;
    /** Ids de Jogadores da Sessão encerrada, para responder {@code SESSAO_ENCERRADA}. */
    private final Set<String> idsEnterrados = new HashSet<>();
    /** Coleta de "continuar" aberta; {@code null} fora da fase {@code COLETA_CONTINUAR}. */
    private ColetaContinuar coleta;
    /** Verdadeiro depois de {@link #desligar()}. */
    private boolean desligada;

    /** Cria uma Sala com agendador real, pool de Callbacks próprio e sem log. */
    public Sala(ConfiguracaoSala configuracao) {
        this(configuracao, EntregadorJogador.novoPoolCallbacks(), new AgendadorReal("sala"),
                RelogioSistema.INSTANCIA, RegistroEventosSala.NENHUM);
    }

    /**
     * Cria uma Sala com todas as dependências injetadas.
     *
     * @throws IllegalArgumentException se o catálogo não formar um {@code CatalogoImagensDTO} válido
     */
    public Sala(ConfiguracaoSala configuracao, ExecutorService poolCallbacks, Agendador agendador,
            Relogio relogio, RegistroEventosSala registro) {
        this.configuracao = Objects.requireNonNull(configuracao, "configuracao");
        this.poolCallbacks = Objects.requireNonNull(poolCallbacks, "poolCallbacks");
        this.agendador = Objects.requireNonNull(agendador, "agendador");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.registro = Objects.requireNonNull(registro, "registro");
        this.temporizadores = new Temporizadores(agendador, configuracao.intervaloErro(),
                configuracao.inatividade(), configuracao.prazoContinuar());
        // Falha cedo se as chaves do catálogo não forem 1..N/2 (contrato do CatalogoImagensDTO).
        this.catalogoDto = new CatalogoImagensDTO(
                configuracao.catalogo().recursosParaPares(configuracao.tabuleiro().posicoes() / 2));
    }

    /**
     * Registra um Jogador na Sala: reserva sob lock, {@code ping} fora do lock e confirmação sob lock.
     * Em caso de sucesso publica {@code aoAtualizarSala}; uma rejeição não altera nada.
     *
     * @throws EntradaRejeitadaException se a entrada for rejeitada (Partida em andamento, Sala cheia,
     *         nome inválido ou em uso, Callback inválido)
     */
    public EntradaSalaDTO entrar(String nome, CallbackJogador callback) throws EntradaRejeitadaException {
        Reserva reserva = reservarVaga(nome, callback);
        boolean pingOk;
        try {
            // Fora do lock: pode demorar até o timeout de ping.
            pingOk = EntregadorJogador.pingar(callback, configuracao.timeoutPing(), poolCallbacks);
        } catch (RuntimeException e) {
            pingOk = false;
        }
        return confirmarEntrada(reserva, pingOk);
    }

    /**
     * Remove voluntariamente o Jogador do lobby, publica a lista atualizada e reavalia o início
     * automático. Fora do {@code LOBBY} é rejeitada.
     *
     * @throws OperacaoInvalidaException com {@code PARTICIPANTE_NAO_REGISTRADO} ou
     *         {@code SAIDA_NAO_PERMITIDA_EM_PARTIDA}
     */
    public void sair(String idParticipante) throws OperacaoInvalidaException {
        lock.lock();
        try {
            Jogador j = idParticipante == null ? null : jogadores.get(idParticipante);
            if (j == null) {
                registrar(RegistroEventosSala.Tipo.SAIDA, null, "REJEITADA",
                        CodigoErro.PARTICIPANTE_NAO_REGISTRADO + ": id desconhecido");
                throw new OperacaoInvalidaException(CodigoErro.PARTICIPANTE_NAO_REGISTRADO,
                        "Participante não registrado na Sala.");
            }
            if (fase != FaseSala.LOBBY) {
                registrar(RegistroEventosSala.Tipo.SAIDA, j.nome, "REJEITADA",
                        CodigoErro.SAIDA_NAO_PERMITIDA_EM_PARTIDA + ": fase=" + fase);
                throw new OperacaoInvalidaException(CodigoErro.SAIDA_NAO_PERMITIDA_EM_PARTIDA,
                        "A saída não é permitida durante a Partida.");
            }
            removerDoLobby(j);
            registrar(RegistroEventosSala.Tipo.SAIDA, j.nome, "OK", "restantes=" + jogadores.size());
            avaliarInicioAutomatico();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marca o Jogador como pronto (idempotente) e inicia a Partida quando todos (de 2 até a
     * capacidade) estão prontos.
     *
     * @throws OperacaoInvalidaException com {@code PARTICIPANTE_NAO_REGISTRADO},
     *         {@code PARTIDA_EM_ANDAMENTO} ou {@code PARTICIPANTES_INSUFICIENTES}
     */
    public void marcarPronto(String idParticipante) throws OperacaoInvalidaException {
        lock.lock();
        try {
            Jogador j = idParticipante == null ? null : jogadores.get(idParticipante);
            if (j == null) {
                registrar(RegistroEventosSala.Tipo.PRONTO, null, "REJEITADA",
                        CodigoErro.PARTICIPANTE_NAO_REGISTRADO + ": id desconhecido");
                throw new OperacaoInvalidaException(CodigoErro.PARTICIPANTE_NAO_REGISTRADO,
                        "Participante não registrado na Sala.");
            }
            if (fase != FaseSala.LOBBY) {
                registrar(RegistroEventosSala.Tipo.PRONTO, j.nome, "REJEITADA",
                        CodigoErro.PARTIDA_EM_ANDAMENTO + ": fase=" + fase);
                throw new OperacaoInvalidaException(CodigoErro.PARTIDA_EM_ANDAMENTO,
                        "Há uma Partida ou Sessão em andamento; não é possível marcar prontidão.");
            }
            if (jogadores.size() < 2) {
                registrar(RegistroEventosSala.Tipo.PRONTO, j.nome, "REJEITADA",
                        CodigoErro.PARTICIPANTES_INSUFICIENTES + ": jogadores=" + jogadores.size());
                throw new OperacaoInvalidaException(CodigoErro.PARTICIPANTES_INSUFICIENTES,
                        "São necessários ao menos 2 Jogadores na Sala.");
            }
            if (j.pronto) {
                registrar(RegistroEventosSala.Tipo.PRONTO, j.nome, "IGNORADA", "já estava pronto");
                return;
            }
            j.pronto = true;
            publicarSala();
            registrar(RegistroEventosSala.Tipo.PRONTO, j.nome, "OK", "jogadores=" + jogadores.size());
            avaliarInicioAutomatico();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Informa que o Cliente está sendo encerrado. No {@code LOBBY} equivale a {@link #sair(String)};
     * nas demais fases é tratado como desconexão {@link CausaDesconexao#SAIDA_VOLUNTARIA}. Nunca
     * lança exceção de domínio.
     */
    public void notificarEncerramentoCliente(String idParticipante) {
        try {
            lock.lock();
            try {
                Jogador j = idParticipante == null ? null : jogadores.get(idParticipante);
                if (j == null) {
                    return;
                }
                if (fase == FaseSala.LOBBY) {
                    removerDoLobby(j);
                    registrar(RegistroEventosSala.Tipo.SAIDA, j.nome, "OK",
                            "encerramento do cliente; restantes=" + jogadores.size());
                    avaliarInicioAutomatico();
                } else {
                    tratarDesconexao(j.id, CausaDesconexao.SAIDA_VOLUNTARIA);
                }
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            registrar(RegistroEventosSala.Tipo.SAIDA, null, "ERRO_INTERNO", String.valueOf(e));
            registrarErro("Sala.notificarEncerramentoCliente", e);
        }
    }

    /**
     * Processa a seleção de uma carta sob o lock; a validação e a mutação ficam em
     * {@link Partida#selecionar}. Seleções aceitas são publicadas e reprogramam os temporizadores.
     *
     * @throws JogadaRejeitadaException com o {@link MotivoRejeicao} da primeira regra violada
     */
    public void selecionarCarta(String idParticipante, Long idPartida, Integer posicao)
            throws JogadaRejeitadaException {
        MotivoRejeicao rejeicao;
        boolean disputouLock = !lock.tryLock();
        if (disputouLock) {
            lock.lock();
        }
        try {
            rejeicao = processarSelecao(idParticipante, idPartida, posicao, disputouLock);
        } catch (RuntimeException e) {
            registrarErro("Sala.selecionarCarta", e);
            throw e;
        } finally {
            lock.unlock();
        }
        if (rejeicao != null) {
            throw new JogadaRejeitadaException(rejeicao);
        }
    }

    /**
     * Corpo de {@link #selecionarCarta(String, Long, Integer)} executado sob o lock.
     *
     * @return {@code null} se a seleção foi aceita; senão o motivo da rejeição
     */
    private MotivoRejeicao processarSelecao(String idParticipante, Long idPartida, Integer posicao,
            boolean disputouLock) {
        exigirLock();
        Jogador j = idParticipante == null ? null : jogadores.get(idParticipante);
        String ator = j != null ? j.nome : idParticipante;

        MotivoRejeicao previo = null;
        if (idParticipante != null && idsEnterrados.contains(idParticipante)) {
            previo = MotivoRejeicao.SESSAO_ENCERRADA;
        } else if (j == null) {
            previo = MotivoRejeicao.PARTICIPANTE_INVALIDO;
        } else if (partida == null || !partida.participanteConhecido(idParticipante)) {
            previo = MotivoRejeicao.PARTIDA_ANTERIOR_OU_INEXISTENTE;
        }
        if (previo != null) {
            registrarJogadaRejeitada(ator, idPartida, posicao, previo, disputouLock);
            return previo;
        }

        ResultadoSelecao resultado = partida.selecionar(idParticipante, idPartida, posicao, disputouLock);
        if (resultado instanceof ResultadoSelecao.Rejeitada r) {
            registrarJogadaRejeitada(ator, idPartida, posicao, r.motivo(), disputouLock);
            return r.motivo();
        }

        ResultadoSelecao.Aceita aceita = (ResultadoSelecao.Aceita) resultado;
        publicarEventos(aceita.eventos());
        reprogramarTemporizadores(aceita.acao());
        registrar(RegistroEventosSala.Tipo.JOGADA, ator, "ACEITA",
                "partida=" + partida.id() + " posicao=" + posicao + " acao=" + aceita.acao()
                        + " eventos=" + aceita.eventos().size() + " proximaSequencia="
                        + partida.proximaSequencia());
        if (aceita.acao() == AcaoTemporizador.PARTIDA_CONCLUIDA) {
            aoPartidaConcluida();
        }
        return null;
    }

    /** Registra uma jogada rejeitada no log, sem publicar nada. Exige o lock. */
    private void registrarJogadaRejeitada(String ator, Long idPartida, Integer posicao,
            MotivoRejeicao motivo, boolean disputouLock) {
        registrar(RegistroEventosSala.Tipo.JOGADA, ator, "REJEITADA",
                motivo + ": partida=" + idPartida + " posicao=" + posicao
                        + (disputouLock ? " (disputou o lock)" : ""));
    }

    /** Reprograma os temporizadores conforme a ação devolvida por uma seleção aceita. Exige o lock. */
    private void reprogramarTemporizadores(AcaoTemporizador acao) {
        exigirLock();
        switch (acao) {
            case INICIAR_INTERVALO_ERRO -> {
                temporizadores.cancelarInatividade();
                temporizadores.agendarFimDoErro(partida.epoca(), this::tarefaFimDoErro);
            }
            case REARMAR_INATIVIDADE -> {
                temporizadores.cancelarFimDoErro();
                temporizadores.agendarInatividade(partida.epoca(), this::tarefaInatividade);
            }
            case PARTIDA_CONCLUIDA -> temporizadores.cancelarTudo();
            case NENHUMA -> { }
        }
    }

    /**
     * Gancho chamado sob o lock quando uma seleção conclui a Partida: registra o resultado na
     * {@link Sessao} e abre a coleta de "continuar".
     */
    void aoPartidaConcluida() {
        exigirLock();
        garantirSessao();
        List<ResultadoJogadorPartida> resultados = new ArrayList<>();
        for (String id : partida.ordemJogadores()) {
            ContadorPartida c = partida.contador(id);
            resultados.add(new ResultadoJogadorPartida(id, partida.nomeDoJogador(id), c.pares(),
                    c.tentativas()));
        }
        sessao.registrarConcluida(resultados);
        registrar(RegistroEventosSala.Tipo.RESULTADO, null, "CONCLUIDA",
                "partida=" + partida.id() + " concluidasNaSessao=" + sessao.partidasConcluidas()
                        + " empates=" + sessao.empates().size());
        abrirColetaContinuar();
    }

    /**
     * Registra a resposta de um Jogador à pergunta de continuar. Resposta negativa encerra a Sessão;
     * quando todos os esperados presentes aceitam, inicia uma nova Partida.
     *
     * @throws OperacaoInvalidaException se o participante for desconhecido, a Sessão já encerrada,
     *         não houver pergunta aberta, a resposta for nula ou o Jogador já tiver respondido
     */
    public void responderContinuar(String idParticipante, Boolean continuar)
            throws OperacaoInvalidaException {
        lock.lock();
        try {
            Jogador j = idParticipante == null ? null : jogadores.get(idParticipante);
            if (j == null) {
                if (idParticipante != null && idsEnterrados.contains(idParticipante)) {
                    throw rejeitarContinuar(null, CodigoErro.SESSAO_ENCERRADA, "A Sessão foi encerrada.");
                }
                throw rejeitarContinuar(null, CodigoErro.PARTICIPANTE_NAO_REGISTRADO,
                        "Participante não registrado na Sala.");
            }
            if (fase != FaseSala.COLETA_CONTINUAR || coleta == null) {
                throw rejeitarContinuar(j.nome, CodigoErro.SEM_PERGUNTA_ABERTA,
                        "Não há pergunta de continuar em aberto.");
            }
            if (continuar == null) {
                throw rejeitarContinuar(j.nome, CodigoErro.JOGADA_REJEITADA,
                        "A resposta sobre continuar não pode ser nula.");
            }
            if (coleta.respostas.containsKey(j.id)) {
                throw rejeitarContinuar(j.nome, CodigoErro.JA_RESPONDEU,
                        "O Jogador já respondeu a esta pergunta.");
            }
            coleta.respostas.put(j.id, continuar);
            registrar(RegistroEventosSala.Tipo.CONTINUAR, j.nome, continuar ? "SIM" : "NAO",
                    "respostas=" + coleta.respostas.size() + "/" + coleta.esperados.size());
            if (!continuar) {
                encerrarSessao();
                return;
            }
            boolean todosAceitaram = true;
            for (String esperado : coleta.esperados) {
                if (!Boolean.TRUE.equals(coleta.respostas.get(esperado))) {
                    todosAceitaram = false;
                    break;
                }
            }
            if (todosAceitaram && jogadores.size() >= 2) {
                temporizadores.cancelarPrazoContinuar();
                iniciarNovaPartida();
            }
        } finally {
            lock.unlock();
        }
    }

    private OperacaoInvalidaException rejeitarContinuar(String ator, CodigoErro codigo, String mensagem) {
        registrar(RegistroEventosSala.Tipo.CONTINUAR, ator, "REJEITADA", codigo + ": " + mensagem);
        return new OperacaoInvalidaException(codigo, mensagem);
    }

    /**
     * Consulta somente leitura: devolve a Sala atual e, se houver Partida, seu Estado Público
     * ({@code CONSULTA}, com catálogo). Não consome sequência nem publica eventos.
     *
     * @throws OperacaoInvalidaException com {@code PARTICIPANTE_NAO_REGISTRADO}
     */
    public VisaoGeralDTO consultarEstado(String idParticipante) throws OperacaoInvalidaException {
        lock.lock();
        try {
            if (idParticipante == null || !jogadores.containsKey(idParticipante)) {
                throw new OperacaoInvalidaException(CodigoErro.PARTICIPANTE_NAO_REGISTRADO,
                        "Participante não registrado na Sala.");
            }
            return new VisaoGeralDTO(montarSalaDto(),
                    partida == null ? null : partida.estadoPublico(TipoEvento.CONSULTA, null));
        } finally {
            lock.unlock();
        }
    }

    /** Fase atual da Sala. */
    public FaseSala fase() {
        lock.lock();
        try {
            return fase;
        } finally {
            lock.unlock();
        }
    }

    /** Retrato do estado da Sala, sem alterar a versão. */
    public SalaDTO estadoSala() {
        lock.lock();
        try {
            return montarSalaDto();
        } finally {
            lock.unlock();
        }
    }

    /** Parâmetros desta Sala. */
    public ConfiguracaoSala configuracao() {
        return configuracao;
    }

    /** Desliga a Sala: cancela temporizadores e fecha os entregadores (idempotente). */
    public void desligar() {
        lock.lock();
        try {
            desligada = true;
            temporizadores.cancelarTudo();
            distribuidor.removerTodosAgora();
        } finally {
            lock.unlock();
        }
    }

    /** Fase 1 (sob lock): valida na ordem de precedência e cria a reserva. */
    private Reserva reservarVaga(String nome, CallbackJogador callback) throws EntradaRejeitadaException {
        lock.lock();
        try {
            String nomeLog = nome;
            if (fase == FaseSala.PARTIDA) {
                throw rejeitarEntrada(CodigoErro.PARTIDA_JA_COMECOU, nomeLog,
                        "A Partida já começou.");
            }
            if (fase != FaseSala.LOBBY) {
                throw rejeitarEntrada(CodigoErro.SESSAO_EM_ANDAMENTO, nomeLog,
                        "Há uma Sessão em andamento; a entrada só é aceita no lobby.");
            }
            if (callback == null) {
                throw rejeitarEntrada(CodigoErro.CALLBACK_INVALIDO, nomeLog, "Callback ausente.");
            }
            if (jogadores.size() + reservas.size() >= configuracao.capacidade()) {
                throw rejeitarEntrada(CodigoErro.SALA_CHEIA, nomeLog, "A Sala está cheia.");
            }
            String aparado = nome == null ? null : nome.strip();
            if (aparado == null || aparado.isEmpty()
                    || aparado.codePointCount(0, aparado.length()) > TAMANHO_MAX_NOME) {
                throw rejeitarEntrada(CodigoErro.NOME_INVALIDO, nomeLog,
                        "O nome deve ter de 1 a " + TAMANHO_MAX_NOME + " caracteres.");
            }
            if (nomeEmUso(aparado)) {
                throw rejeitarEntrada(CodigoErro.NOME_EM_USO, aparado, "O nome já está em uso.");
            }
            Reserva reserva = new Reserva(aparado, callback);
            reservas.put(reserva.idTemporario, reserva);
            return reserva;
        } finally {
            lock.unlock();
        }
    }

    /** Fase 3 (sob lock): confirma ou desfaz a reserva. */
    private EntradaSalaDTO confirmarEntrada(Reserva reserva, boolean pingOk) throws EntradaRejeitadaException {
        lock.lock();
        try {
            reservas.remove(reserva.idTemporario);
            if (!pingOk) {
                throw rejeitarEntrada(CodigoErro.CALLBACK_INVALIDO, reserva.nome,
                        "O Callback não respondeu à verificação de conectividade.");
            }
            if (fase == FaseSala.PARTIDA) {
                throw rejeitarEntrada(CodigoErro.PARTIDA_JA_COMECOU, reserva.nome, "A Partida já começou.");
            }
            if (fase != FaseSala.LOBBY) {
                throw rejeitarEntrada(CodigoErro.SESSAO_EM_ANDAMENTO, reserva.nome,
                        "Há uma Sessão em andamento; a entrada só é aceita no lobby.");
            }
            garantirSessao();
            String id = UUID.randomUUID().toString();
            EntregadorJogador entregador = new EntregadorJogador(id, reserva.callback,
                    configuracao.timeoutCallback(), poolCallbacks,
                    (idJogador, causa) -> aoFalhaCallback(idJogador, causa));
            Jogador novo = new Jogador(id, reserva.nome, ++ultimaOrdemEntrada, reserva.callback, entregador);
            jogadores.put(id, novo);
            distribuidor.adicionar(entregador);
            SalaDTO sala = publicarSala();
            registrar(RegistroEventosSala.Tipo.ENTRADA, novo.nome, "OK",
                    "id=" + id + " jogadores=" + jogadores.size());
            return new EntradaSalaDTO(id, catalogoDto, sala);
        } finally {
            lock.unlock();
        }
    }

    private boolean nomeEmUso(String nomeAparado) {
        for (Jogador j : jogadores.values()) {
            if (j.nome.equalsIgnoreCase(nomeAparado)) {
                return true;
            }
        }
        for (Reserva r : reservas.values()) {
            if (r.nome.equalsIgnoreCase(nomeAparado)) {
                return true;
            }
        }
        return false;
    }

    private EntradaRejeitadaException rejeitarEntrada(CodigoErro codigo, String nomeLog, String mensagem) {
        registrar(RegistroEventosSala.Tipo.ENTRADA, nomeLog, "REJEITADA", codigo + ": " + mensagem);
        return new EntradaRejeitadaException(codigo, mensagem);
    }

    /** Remove o Jogador no lobby, fecha seu entregador e publica a Sala aos restantes. Exige o lock. */
    private void removerDoLobby(Jogador j) {
        exigirLock();
        jogadores.remove(j.id);
        distribuidor.remover(j.id);
        publicarSala();
    }

    /**
     * Inicia a Partida se a Sala está no lobby, tem de 2 até a capacidade Jogadores e todos estão
     * prontos. Exige o lock.
     *
     * @return {@code true} se uma Partida foi iniciada
     */
    boolean avaliarInicioAutomatico() {
        exigirLock();
        if (fase != FaseSala.LOBBY) {
            return false;
        }
        int n = jogadores.size();
        if (n < 2 || n > configuracao.capacidade()) {
            return false;
        }
        for (Jogador j : jogadores.values()) {
            if (!j.pronto || j.desconectado) {
                return false;
            }
        }
        iniciarNovaPartida();
        return true;
    }

    /**
     * Inicia uma nova Partida com os Jogadores presentes: sorteia a matriz, define o primeiro da vez,
     * publica o evento inicial, zera prontidões e arma a inatividade. A {@link Sessao} é preservada.
     * Exige o lock.
     */
    void iniciarNovaPartida() {
        exigirLock();
        garantirSessao();
        List<Jogador> presentes = new ArrayList<>(jogadores.values());
        List<String> ids = new ArrayList<>(presentes.size());
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, Long> ordens = new LinkedHashMap<>();
        for (Jogador j : presentes) {
            ids.add(j.id);
            nomes.put(j.id, j.nome);
            ordens.put(j.id, j.ordemEntrada);
        }
        int posicoes = configuracao.tabuleiro().posicoes();
        int[] matriz = sortearMatriz(posicoes);
        String primeiro = sessao.selecionarPrimeiro(ids, ordens);
        long idPartida = sessao.proximoIdPartida();
        Partida nova = new Partida(idPartida, configuracao.tabuleiro().linhas(),
                configuracao.tabuleiro().colunas(), matriz, ids, nomes, ids.indexOf(primeiro),
                catalogoDto, (int) configuracao.prazoContinuar().toSeconds());

        temporizadores.cancelarTudo();
        epoca++;
        partida = nova;
        coleta = null;
        fase = FaseSala.PARTIDA;
        for (Jogador j : presentes) {
            j.pronto = false;
        }
        publicarEventos(nova.iniciar());
        temporizadores.agendarInatividade(nova.epoca(), this::tarefaInatividade);
        registrar(RegistroEventosSala.Tipo.INICIO_PARTIDA, nomes.get(primeiro), "OK",
                "partida=" + idPartida + " jogadores=" + ids.size() + " posicoes=" + posicoes);
    }

    private int[] sortearMatriz(int posicoes) {
        if (configuracao.random() != null) {
            return GeradorMatriz.gerar(posicoes, configuracao.random());
        }
        return GeradorMatriz.gerarProducao(posicoes);
    }

    /**
     * Chamado pelo {@link EntregadorJogador}, fora do lock, quando um Callback falha: adquire o lock e
     * delega a {@link #tratarDesconexao(String, CausaDesconexao)}. Nenhuma exceção escapa.
     */
    void aoFalhaCallback(String idJogador, CausaDesconexao causa) {
        lock.lock();
        try {
            tratarDesconexao(idJogador, causa);
        } catch (RuntimeException e) {
            registrar(RegistroEventosSala.Tipo.DESCONEXAO, null, "ERRO_INTERNO",
                    "id=" + idJogador + " causa=" + causa + ": " + e);
            registrarErro("Sala.aoFalhaCallback", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Trata a desconexão de um Jogador de forma idempotente: remove-o, notifica os restantes e age
     * conforme a fase (reavalia o início no lobby, cancela a Partida ou encerra a coleta). Exige o lock.
     */
    void tratarDesconexao(String idJogador, CausaDesconexao causa) {
        exigirLock();
        if (desligada) {
            return;
        }
        Jogador j = idJogador == null ? null : jogadores.get(idJogador);
        if (j == null || j.desconectado) {
            return;
        }
        FaseSala faseNaDesconexao = fase;
        removerDesconectado(j, causa);
        switch (faseNaDesconexao) {
            case LOBBY -> avaliarInicioAutomatico();
            case PARTIDA -> tratarDesconexaoEmPartida(j, causa);
            case COLETA_CONTINUAR -> tratarDesconexaoNaColeta(j);
            default -> { }
        }
    }

    /** Remove o Jogador desconectado, marca-o na Sessão e publica a desconexão e a Sala. Exige o lock. */
    private void removerDesconectado(Jogador j, CausaDesconexao causa) {
        exigirLock();
        j.desconectado = true;
        jogadores.remove(j.id);
        distribuidor.remover(j.id);
        if (sessao != null) {
            sessao.marcarDesconectado(j.id);
        }
        long versao = ++versaoSala;
        distribuidor.publicar(EventoCallback.desconexao(new DesconexaoDTO(versao, j.nome, causa)));
        publicarSala();
        registrar(RegistroEventosSala.Tipo.DESCONEXAO, j.nome, causa.name(),
                "fase=" + fase + " restantes=" + jogadores.size());
    }

    /**
     * Desconexão durante a fase {@code PARTIDA}: cancela a Partida uma única vez e decide o destino
     * da Sessão pelo número de restantes. Exige o lock.
     */
    private void tratarDesconexaoEmPartida(Jogador desconectado, CausaDesconexao causa) {
        exigirLock();
        Partida p = partida;
        if (p == null || p.status() != StatusPartida.EM_ANDAMENTO) {
            return;
        }
        cancelarPartida(p, desconectado, causa);
        int restantes = jogadores.size();
        if (restantes >= 2) {
            long prazoSeg = configuracao.prazoContinuar().toSeconds();
            publicarEventos(List.of(EventoPartida.pergunta(
                    new PerguntaContinuarDTO(p.id(), p.consumirSequencia(), (int) prazoSeg))));
            abrirColetaContinuar();
        } else {
            encerrarSessao();
        }
    }

    /** Cancela a Partida, registra-a na Sessão e publica {@code aoCancelarPartida}. Exige o lock. */
    private void cancelarPartida(Partida p, Jogador primeiroDesconectado, CausaDesconexao causa) {
        exigirLock();
        p.cancelar();
        temporizadores.cancelarTudo();
        epoca++;
        garantirSessao();
        sessao.registrarCancelada();
        CancelamentoPartidaDTO dto = new CancelamentoPartidaDTO(p.id(), p.consumirSequencia(),
                primeiroDesconectado.nome, causa);
        publicarEventos(List.of(EventoPartida.cancelamento(dto)));
        registrar(RegistroEventosSala.Tipo.CANCELAMENTO, primeiroDesconectado.nome, causa.name(),
                "partida=" + p.id() + " sequencia=" + dto.sequencia() + " restantes=" + jogadores.size()
                        + " canceladas=" + sessao.partidasCanceladas());
    }

    /** Desconexão durante a coleta de "continuar": conta como negativa e encerra a Sessão. Exige o lock. */
    private void tratarDesconexaoNaColeta(Jogador desconectado) {
        exigirLock();
        if (coleta != null) {
            coleta.esperados.remove(desconectado.id);
            coleta.respostas.putIfAbsent(desconectado.id, Boolean.FALSE);
        }
        registrar(RegistroEventosSala.Tipo.CONTINUAR, desconectado.nome, "NEGATIVA_POR_DESCONEXAO",
                "restantes=" + jogadores.size());
        encerrarSessao();
    }

    /**
     * Abre a coleta de "continuar": muda a fase, arma o prazo e publica a Sala. Não publica a
     * pergunta (quem chama já o fez). Exige o lock.
     */
    void abrirColetaContinuar() {
        exigirLock();
        temporizadores.cancelarTudo();
        epoca++;
        fase = FaseSala.COLETA_CONTINUAR;
        coleta = new ColetaContinuar(new LinkedHashSet<>(jogadores.keySet()), epoca);
        temporizadores.agendarPrazoContinuar(epoca, this::tarefaPrazoContinuar);
        publicarSala();
        registrar(RegistroEventosSala.Tipo.CONTINUAR, null, "ABERTA",
                "esperados=" + coleta.esperados.size() + " prazoSeg="
                        + configuracao.prazoContinuar().toSeconds());
    }

    /**
     * Tarefa do prazo de continuar: se a época e a fase ainda valem, a ausência de resposta conta como
     * negativa e a Sessão é encerrada.
     */
    void tarefaPrazoContinuar(long epocaTarefa) {
        lock.lock();
        try {
            if (desligada || epocaTarefa != epoca || fase != FaseSala.COLETA_CONTINUAR) {
                return;
            }
            registrar(RegistroEventosSala.Tipo.CONTINUAR, null, "PRAZO_ESGOTADO",
                    "respostas=" + (coleta == null ? 0 : coleta.respostas.size()));
            encerrarSessao();
        } catch (RuntimeException e) {
            registrar(RegistroEventosSala.Tipo.CONTINUAR, null, "ERRO_INTERNO", String.valueOf(e));
            registrarErro("Sala.tarefaPrazoContinuar", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Encerra a Sessão: publica o sumário aos Jogadores restantes, fecha seus entregadores e
     * reinicia a Sala em {@code LOBBY}. Idempotente. Exige o lock.
     */
    void encerrarSessao() {
        exigirLock();
        temporizadores.cancelarTudo();
        epoca++;
        coleta = null;
        int destinatarios = jogadores.size();
        if (destinatarios > 0 && sessao != null) {
            long idPartida = partida == null ? 0L : partida.id();
            long sequencia = partida == null ? 1L : partida.consumirSequencia();
            SumarioSessaoDTO sumario = sessao.gerarSumario(idPartida, sequencia);
            distribuidor.publicar(EventoCallback.sumario(sumario));
            registrar(RegistroEventosSala.Tipo.SUMARIO, null, "ENVIADO",
                    "partida=" + idPartida + " sequencia=" + sequencia + " destinatarios=" + destinatarios
                            + " concluidas=" + sumario.partidasConcluidas() + " canceladas="
                            + sumario.partidasCanceladas());
        } else {
            registrar(RegistroEventosSala.Tipo.SUMARIO, null, "SEM_DESTINATARIOS", "Sessão encerrada");
        }
        idsEnterrados.addAll(jogadores.keySet());
        distribuidor.removerTodosAposEsvaziar();
        jogadores.clear();
        partida = null;
        sessao = null;
        fase = FaseSala.LOBBY;
    }

    /** Tarefa do temporizador de inatividade da vez; épocas obsoletas são ignoradas pelo núcleo. */
    void tarefaInatividade(long epocaTarefa) {
        executarTarefaTemporizador(epocaTarefa, false);
    }

    /** Tarefa do temporizador de fim do Intervalo_de_Erro; épocas obsoletas são ignoradas pelo núcleo. */
    void tarefaFimDoErro(long epocaTarefa) {
        executarTarefaTemporizador(epocaTarefa, true);
    }

    /**
     * Corpo comum das tarefas de temporizador: chama o núcleo sob o lock e, se aplicado, publica os
     * eventos e reprograma os temporizadores.
     */
    private void executarTarefaTemporizador(long epocaTarefa, boolean fimDoErro) {
        lock.lock();
        try {
            Partida p = partida;
            if (p == null || fase != FaseSala.PARTIDA) {
                return;
            }
            ResultadoTemporizador r = fimDoErro ? p.fecharAposErro(epocaTarefa)
                    : p.expirarInatividade(epocaTarefa);
            if (!r.aplicado()) {
                return;
            }
            publicarEventos(r.eventos());
            reprogramarTemporizadores(r.acao());
            registrar(RegistroEventosSala.Tipo.TROCA_DE_TURNO, p.nomeJogadorDaVez(),
                    fimDoErro ? "VEZ_PASSADA" : "INATIVIDADE",
                    "partida=" + p.id() + " eventos=" + r.eventos().size() + " proximaSequencia="
                            + p.proximaSequencia());
            if (r.acao() == AcaoTemporizador.PARTIDA_CONCLUIDA) {
                aoPartidaConcluida();
            }
        } catch (RuntimeException e) {
            registrar(RegistroEventosSala.Tipo.TROCA_DE_TURNO, null, "ERRO_INTERNO",
                    (fimDoErro ? "fimDoErro" : "inatividade") + ": " + e);
            registrarErro(fimDoErro ? "Sala.tarefaFimDoErro" : "Sala.tarefaInatividade", e);
        } finally {
            lock.unlock();
        }
    }

    /** Incrementa a versão da Sala, monta o {@code SalaDTO} e o enfileira para todos. Exige o lock. */
    SalaDTO publicarSala() {
        exigirLock();
        versaoSala++;
        SalaDTO dto = montarSalaDto();
        distribuidor.publicar(EventoCallback.sala(dto));
        return dto;
    }

    /** Enfileira os eventos de Partida, na ordem, para todos os Jogadores. Exige o lock. */
    void publicarEventos(List<EventoPartida> eventos) {
        exigirLock();
        for (EventoPartida e : eventos) {
            distribuidor.publicar(EventoCallback.de(e));
        }
    }

    /** Monta o {@code SalaDTO} com a versão atual (sem incrementá-la). Exige o lock. */
    SalaDTO montarSalaDto() {
        exigirLock();
        List<ParticipanteDTO> participantes = new ArrayList<>(jogadores.size());
        for (Jogador j : jogadores.values()) {
            participantes.add(new ParticipanteDTO(j.id, j.nome, j.pronto, Math.toIntExact(j.ordemEntrada)));
        }
        return new SalaDTO(versaoSala, participantes, configuracao.capacidade(), fase);
    }

    /** Cria a Sessão se ainda não existe (primeira entrada após o início ou o fim de uma Sessão). */
    private void garantirSessao() {
        if (sessao == null) {
            sessao = new Sessao();
            idsEnterrados.clear();
        }
    }

    /** Registra no gancho de log, ignorando qualquer falha dele. */
    void registrar(RegistroEventosSala.Tipo tipo, String ator, String resultado, String detalhes) {
        try {
            registro.registrar(tipo, ator, resultado, detalhes);
        } catch (RuntimeException e) {
            // o log nunca pode derrubar uma operação da Sala
        }
    }

    /** Registra uma exceção inesperada no gancho de log, ignorando falhas dele. */
    void registrarErro(String contexto, Throwable t) {
        try {
            registro.registrarErro(contexto, t);
        } catch (RuntimeException e) {
            // o log nunca pode derrubar uma operação da Sala
        }
    }

    /** Falha com {@link IllegalStateException} se a thread atual não detém o lock da Sala. */
    void exigirLock() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("operação exige o lock da Sala");
        }
    }

    /** Jogador pelo id, ou {@code null}. Exige o lock. */
    Jogador jogador(String id) {
        exigirLock();
        return id == null ? null : jogadores.get(id);
    }

    /** Partida corrente, ou {@code null}. Exige o lock. */
    Partida partida() {
        exigirLock();
        return partida;
    }

    /** Sessão corrente, ou {@code null}. Exige o lock. */
    Sessao sessao() {
        exigirLock();
        return sessao;
    }

    /** Versão atual dos eventos de sala. Exige o lock. */
    long versaoSala() {
        exigirLock();
        return versaoSala;
    }

    /** Token de época de nível de Sala. Exige o lock. */
    long epoca() {
        exigirLock();
        return epoca;
    }

    /** Ids de Jogadores de Sessões encerradas. Exige o lock. */
    Set<String> idsEnterrados() {
        exigirLock();
        return idsEnterrados;
    }

    /** Temporizadores da Sala. */
    Temporizadores temporizadores() {
        return temporizadores;
    }

    /** Distribuidor de eventos da Sala. */
    DistribuidorEventos distribuidor() {
        return distribuidor;
    }

    /** Agendador injetado. */
    Agendador agendador() {
        return agendador;
    }

    /** Relógio injetado. */
    Relogio relogio() {
        return relogio;
    }
}
