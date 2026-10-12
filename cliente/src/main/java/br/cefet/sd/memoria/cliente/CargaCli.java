package br.cefet.sd.memoria.cliente;

import br.cefet.sd.memoria.cliente.rmi.ConexaoRmi;
import br.cefet.sd.memoria.cliente.rmi.ConexaoRmiException;
import br.cefet.sd.memoria.cliente.rmi.ServicosRemotos;
import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.CartaPublicaDTO;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoCartaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import br.cefet.sd.memoria.comum.dto.VisaoGeralDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import br.cefet.sd.memoria.comum.excecao.JogadaRejeitadaException;
import br.cefet.sd.memoria.comum.excecao.MotivoRejeicao;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import br.cefet.sd.memoria.comum.remoto.ServicoPartida;

import java.io.PrintStream;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Utilitário de carga sem interface gráfica. */
public final class CargaCli {

    /** Veredito impresso quando somente o Jogador da vez foi aceito. */
    public static final String VEREDITO_OK = "OK: somente o Jogador da vez foi aceito";

    /** Prefixo do veredito impresso em caso de falha. */
    public static final String PREFIXO_FALHA = "FALHA: ";

    private static final long TIMEOUT_CHAMADA_SEGUNDOS = 60L;
    private static final long TIMEOUT_BARREIRA_SEGUNDOS = 30L;
    private static final long INTERVALO_CONSULTA_MS = 500L;
    private static final long TIMEOUT_REVELAR_MS = 30_000L;

    private CargaCli() {
    }

    /** Parâmetros da execução de carga. */
    public record Configuracao(String host, int porta, int jogadores, int disparos, int rodadas,
                               int timeoutSegundos) {

        /** Valor padrão de {@code registry.host}. */
        public static final String HOST_PADRAO = "localhost";
        /** Valor padrão de {@code registry.porta}. */
        public static final int PORTA_PADRAO = 1099;
        /** Valor padrão de {@code jogadores}. */
        public static final int JOGADORES_PADRAO = 3;
        /** Valor padrão de {@code disparos}. */
        public static final int DISPAROS_PADRAO = 50;
        /** Valor padrão de {@code rodadas}. */
        public static final int RODADAS_PADRAO = 1;
        /** Valor padrão de {@code timeoutSeg}. */
        public static final int TIMEOUT_PADRAO_SEGUNDOS = 15;

        /** Valida os parâmetros. */
        public Configuracao {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("registry.host não pode ser vazio");
            }
            if (porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("registry.porta deve estar entre 1 e 65535: " + porta);
            }
            if (jogadores < 2) {
                throw new IllegalArgumentException("jogadores deve ser >= 2: " + jogadores);
            }
            if (disparos < jogadores) {
                throw new IllegalArgumentException(
                        "disparos deve ser >= jogadores (" + jogadores + "): " + disparos);
            }
            if (rodadas < 1) {
                throw new IllegalArgumentException("rodadas deve ser >= 1: " + rodadas);
            }
            if (timeoutSegundos < 1) {
                throw new IllegalArgumentException("timeoutSeg deve ser >= 1: " + timeoutSegundos);
            }
        }

        /** Cria a configuração padrão. */
        public static Configuracao padrao() {
            return new Configuracao(HOST_PADRAO, PORTA_PADRAO, JOGADORES_PADRAO, DISPAROS_PADRAO,
                    RODADAS_PADRAO, TIMEOUT_PADRAO_SEGUNDOS);
        }

        /** Interpreta argumentos no formato {@code --chave=valor}. */
        public static Configuracao interpretar(String[] args) {
            Objects.requireNonNull(args, "args");
            String host = HOST_PADRAO;
            int porta = PORTA_PADRAO;
            int jogadores = JOGADORES_PADRAO;
            int disparos = DISPAROS_PADRAO;
            int rodadas = RODADAS_PADRAO;
            int timeout = TIMEOUT_PADRAO_SEGUNDOS;
            for (String arg : args) {
                if (arg == null || !arg.startsWith("--") || arg.indexOf('=') < 3) {
                    throw new IllegalArgumentException("argumento inválido (use --chave=valor): " + arg);
                }
                int igual = arg.indexOf('=');
                String chave = arg.substring(2, igual);
                String valor = arg.substring(igual + 1);
                switch (chave) {
                    case "registry.host" -> host = valor;
                    case "registry.porta" -> porta = inteiro(chave, valor);
                    case "jogadores" -> jogadores = inteiro(chave, valor);
                    case "disparos" -> disparos = inteiro(chave, valor);
                    case "rodadas" -> rodadas = inteiro(chave, valor);
                    case "timeoutSeg" -> timeout = inteiro(chave, valor);
                    default -> throw new IllegalArgumentException("parâmetro desconhecido: " + chave);
                }
            }
            return new Configuracao(host, porta, jogadores, disparos, rodadas, timeout);
        }

        private static int inteiro(String chave, String valor) {
            try {
                return Integer.parseInt(valor.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(chave + " deve ser um inteiro: " + valor);
            }
        }
    }

    /** Resultado de uma rodada de disparos simultâneos. */
    public record RodadaCarga(int numero, int posicao, String idJogadorDaVez, int total,
                              int aceitas, int aceitasForaDaVez,
                              Map<MotivoRejeicao, Integer> rejeitadas, int foraDaVezDivergentes,
                              int errosRemotos, int outrosErros, String primeiroErro) {

        /** Copia defensivamente o mapa de rejeições. */
        public RodadaCarga {
            rejeitadas = Collections.unmodifiableMap(new EnumMap<>(rejeitadas));
        }

        /** Total de rejeições de domínio na rodada. */
        public int totalRejeitadas() {
            return rejeitadas.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** Total de chamadas que terminaram em erro técnico. */
        public int totalErros() {
            return errosRemotos + outrosErros;
        }
    }

    /** Resultado completo de uma execução de carga. */
    public record ResultadoCarga(Configuracao configuracao, List<RodadaCarga> rodadas, boolean ok,
                                 String veredito) {

        /** Copia defensivamente a lista de rodadas. */
        public ResultadoCarga {
            rodadas = List.copyOf(rodadas);
        }

        /** Total de chamadas em todas as rodadas. */
        public int totalChamadas() {
            return rodadas.stream().mapToInt(RodadaCarga::total).sum();
        }

        /** Total de chamadas aceitas. */
        public int aceitas() {
            return rodadas.stream().mapToInt(RodadaCarga::aceitas).sum();
        }

        /** Rejeições somadas por motivo (apenas motivos observados). */
        public Map<MotivoRejeicao, Integer> rejeitadasPorMotivo() {
            Map<MotivoRejeicao, Integer> soma = new EnumMap<>(MotivoRejeicao.class);
            for (RodadaCarga rodada : rodadas) {
                rodada.rejeitadas().forEach((motivo, qtd) -> soma.merge(motivo, qtd, Integer::sum));
            }
            return Collections.unmodifiableMap(soma);
        }

        /** Total de rejeições de domínio. */
        public int totalRejeitadas() {
            return rodadas.stream().mapToInt(RodadaCarga::totalRejeitadas).sum();
        }

        /** Total de erros técnicos (remotos e outros). */
        public int erros() {
            return rodadas.stream().mapToInt(RodadaCarga::totalErros).sum();
        }
    }

    /** Falha de preparação da carga (entrada, prontidão ou ausência do estado inicial). */
    public static final class ErroCargaException extends Exception {
        private static final long serialVersionUID = 1L;

        /** Cria a exceção. */
        public ErroCargaException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    /** Conecta ao Registry e executa a carga. */
    public static ResultadoCarga executar(Configuracao configuracao, PrintStream saida)
            throws ConexaoRmiException, ErroCargaException {
        Objects.requireNonNull(configuracao, "configuracao");
        Objects.requireNonNull(saida, "saida");
        saida.printf("Conectando a %s:%d ...%n", configuracao.host(), configuracao.porta());
        ServicosRemotos servicos = ConexaoRmi.conectar(configuracao.host(), configuracao.porta());
        return executar(configuracao, servicos, saida);
    }

    /** Executa a carga sobre serviços já localizados. */
    public static ResultadoCarga executar(Configuracao configuracao, ServicosRemotos servicos,
                                          PrintStream saida) throws ErroCargaException {
        Objects.requireNonNull(configuracao, "configuracao");
        Objects.requireNonNull(servicos, "servicos");
        Objects.requireNonNull(saida, "saida");

        List<Jogador> jogadores = new ArrayList<>();
        ExecutorService pool = null;
        try {
            preparar(configuracao, servicos, saida, jogadores);
            pool = Executors.newFixedThreadPool(configuracao.disparos(), new FabricaThreadsDaemon());
            ResultadoCarga resultado = disparar(configuracao, servicos.partida(), jogadores, pool, saida);
            imprimirResumo(resultado, saida);
            return resultado;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            encerrar(servicos, jogadores);
        }
    }

    private static void preparar(Configuracao cfg, ServicosRemotos servicos, PrintStream saida,
                                 List<Jogador> jogadores) throws ErroCargaException {
        int sufixo = ThreadLocalRandom.current().nextInt(1000, 10000);
        for (int i = 1; i <= cfg.jogadores(); i++) {
            String nome = "Carga" + i + "-" + sufixo;
            Jogador jogador = new Jogador(nome);
            jogadores.add(jogador);
            try {
                CallbackJogador stub = jogador.callback.exportar();
                EntradaSalaDTO entrada = servicos.sala().entrar(nome, stub);
                jogador.id = entrada.idParticipante();
            } catch (ExcecaoDominio e) {
                throw new ErroCargaException("entrada de " + nome + " rejeitada: " + e.getMessage(), e);
            } catch (RemoteException e) {
                throw new ErroCargaException("falha de comunicação ao entrar como " + nome + ": "
                        + e.getMessage(), e);
            }
            saida.printf("Jogador %d entrou: %s (id %s)%n", i, nome, jogador.id);
        }
        for (Jogador jogador : jogadores) {
            try {
                servicos.sala().marcarPronto(jogador.id);
            } catch (ExcecaoDominio e) {
                throw new ErroCargaException("prontidão de " + jogador.nome + " rejeitada: "
                        + e.getMessage(), e);
            } catch (RemoteException e) {
                throw new ErroCargaException("falha de comunicação ao marcar pronto " + jogador.nome
                        + ": " + e.getMessage(), e);
            }
        }
        saida.printf("%d Jogadores prontos; aguardando o estado inicial da Partida ...%n", jogadores.size());
    }

    private static ResultadoCarga disparar(Configuracao cfg, ServicoPartida partida,
                                           List<Jogador> jogadores, ExecutorService pool,
                                           PrintStream saida) throws ErroCargaException {
        List<RodadaCarga> rodadas = new ArrayList<>();
        long timeoutMs = TimeUnit.SECONDS.toMillis(cfg.timeoutSegundos());
        EstadoPublicoDTO anterior = null;
        boolean anteriorAvancou = false;

        for (int numero = 1; numero <= cfg.rodadas(); numero++) {
            final EstadoPublicoDTO base = anterior;
            final boolean exigirNovo = anteriorAvancou;
            long espera = numero == 1 ? timeoutMs : Math.max(timeoutMs, TIMEOUT_REVELAR_MS);
            EstadoPublicoDTO estado = aguardarEstado(jogadores, partida, espera,
                    e -> estadoPronto(e) && (!exigirNovo || ehMaisNovo(e, base)));
            if (estado == null) {
                String motivo = "não foi recebido um estado utilizável da Partida para a rodada "
                        + numero + " em " + cfg.timeoutSegundos() + " s";
                if (numero == 1) {
                    throw new ErroCargaException(motivo, null);
                }
                String veredito = PREFIXO_FALHA + motivo;
                return new ResultadoCarga(cfg, rodadas, false, veredito);
            }

            int posicao = primeiraFechada(estado);
            RodadaCarga rodada = executarRodada(numero, cfg, partida, jogadores, pool, estado, posicao);
            rodadas.add(rodada);
            saida.printf("Rodada %d: posição %d, vez de %s -> %d aceita(s), %d rejeitada(s), %d erro(s)%n",
                    numero, posicao, nomeDe(jogadores, estado.idJogadorDaVez()), rodada.aceitas(),
                    rodada.totalRejeitadas(), rodada.totalErros());

            anterior = estado;
            anteriorAvancou = rodada.aceitas() > 0;
        }
        String veredito = avaliar(rodadas);
        return new ResultadoCarga(cfg, rodadas, VEREDITO_OK.equals(veredito), veredito);
    }

    private static RodadaCarga executarRodada(int numero, Configuracao cfg, ServicoPartida partida,
                                              List<Jogador> jogadores, ExecutorService pool,
                                              EstadoPublicoDTO estado, int posicao) {
        final long idPartida = estado.idPartida();
        final String idDaVez = estado.idJogadorDaVez();
        final int disparos = cfg.disparos();
        final CyclicBarrier barreira = new CyclicBarrier(disparos);

        List<Future<Chamada>> futuros = new ArrayList<>(disparos);
        for (int i = 0; i < disparos; i++) {
            final Jogador jogador = jogadores.get(i % jogadores.size());
            Callable<Chamada> tarefa = () -> {
                try {
                    barreira.await(TIMEOUT_BARREIRA_SEGUNDOS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Chamada.erro(jogador, false, "interrompido na barreira");
                } catch (Exception e) {
                    return Chamada.erro(jogador, false, "barreira: " + e);
                }
                try {
                    partida.selecionarCarta(jogador.id, idPartida, posicao);
                    return Chamada.aceita(jogador);
                } catch (JogadaRejeitadaException e) {
                    return Chamada.rejeitada(jogador, e.getMotivo());
                } catch (RemoteException e) {
                    return Chamada.erro(jogador, true, e.toString());
                } catch (ExcecaoDominio e) {
                    return Chamada.erro(jogador, false, e.toString());
                } catch (RuntimeException e) {
                    return Chamada.erro(jogador, false, e.toString());
                }
            };
            futuros.add(pool.submit(tarefa));
        }

        int aceitas = 0;
        int aceitasForaDaVez = 0;
        int divergentes = 0;
        int remotos = 0;
        int outros = 0;
        String primeiroErro = null;
        Map<MotivoRejeicao, Integer> rejeitadas = new EnumMap<>(MotivoRejeicao.class);

        for (Future<Chamada> futuro : futuros) {
            Chamada chamada;
            try {
                chamada = futuro.get(TIMEOUT_CHAMADA_SEGUNDOS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                chamada = Chamada.erro(null, false, "interrompido aguardando chamada");
            } catch (ExecutionException e) {
                chamada = Chamada.erro(null, false, String.valueOf(e.getCause()));
            } catch (TimeoutException e) {
                futuro.cancel(true);
                chamada = Chamada.erro(null, true, "chamada excedeu " + TIMEOUT_CHAMADA_SEGUNDOS + " s");
            }
            boolean daVez = chamada.jogador != null && Objects.equals(chamada.jogador.id, idDaVez);
            if (chamada.aceita) {
                aceitas++;
                if (!daVez) {
                    aceitasForaDaVez++;
                }
            } else if (chamada.motivo != null) {
                rejeitadas.merge(chamada.motivo, 1, Integer::sum);
                if (!daVez && chamada.motivo != MotivoRejeicao.FORA_DA_VEZ) {
                    divergentes++;
                }
            } else {
                if (chamada.remoto) {
                    remotos++;
                } else {
                    outros++;
                }
                if (primeiroErro == null) {
                    primeiroErro = chamada.descricaoErro;
                }
            }
        }
        return new RodadaCarga(numero, posicao, idDaVez, disparos, aceitas, aceitasForaDaVez,
                rejeitadas, divergentes, remotos, outros, primeiroErro);
    }

    /**
     * Avalia as rodadas. A primeira deve ter exatamente uma chamada aceita, feita pelo Jogador da
     * vez, e todas as chamadas de quem não era da vez devem ter sido {@code FORA_DA_VEZ}.
     */
    private static String avaliar(List<RodadaCarga> rodadas) {
        for (RodadaCarga r : rodadas) {
            if (r.totalErros() > 0) {
                return PREFIXO_FALHA + "rodada " + r.numero() + " teve " + r.totalErros()
                        + " erro(s) técnico(s): " + r.primeiroErro();
            }
            if (r.aceitas() > 1) {
                return PREFIXO_FALHA + "rodada " + r.numero() + " aceitou " + r.aceitas()
                        + " chamadas simultâneas para a mesma posição (esperado: no máximo 1)";
            }
            if (r.aceitasForaDaVez() > 0) {
                return PREFIXO_FALHA + "rodada " + r.numero()
                        + " aceitou uma chamada de Jogador que não era o da vez";
            }
        }
        RodadaCarga primeira = rodadas.get(0);
        if (primeira.aceitas() != 1) {
            return PREFIXO_FALHA + "na primeira rodada era esperada exatamente 1 chamada aceita, mas "
                    + "foram " + primeira.aceitas();
        }
        if (primeira.foraDaVezDivergentes() > 0) {
            return PREFIXO_FALHA + primeira.foraDaVezDivergentes()
                    + " chamada(s) de Jogadores fora da vez não foram rejeitadas com FORA_DA_VEZ";
        }
        return VEREDITO_OK;
    }

    private static boolean estadoPronto(EstadoPublicoDTO e) {
        return e.status() == StatusPartida.EM_ANDAMENTO
                && e.idJogadorDaVez() != null
                && primeiraFechada(e) >= 0
                && reveladas(e) <= 1;
    }

    private static boolean ehMaisNovo(EstadoPublicoDTO atual, EstadoPublicoDTO base) {
        return base == null || compararVersao(atual, base) > 0;
    }

    private static int compararVersao(EstadoPublicoDTO a, EstadoPublicoDTO b) {
        int porPartida = Long.compare(a.idPartida(), b.idPartida());
        return porPartida != 0 ? porPartida : Long.compare(a.sequencia(), b.sequencia());
    }

    private static int primeiraFechada(EstadoPublicoDTO e) {
        List<CartaPublicaDTO> cartas = e.cartas();
        for (int i = 0; i < cartas.size(); i++) {
            if (cartas.get(i).estado() == EstadoCartaDTO.FECHADA) {
                return i;
            }
        }
        return -1;
    }

    private static long reveladas(EstadoPublicoDTO e) {
        return e.cartas().stream().filter(c -> c.estado() == EstadoCartaDTO.REVELADA).count();
    }

    /**
     * Espera até que o estado mais recente observado satisfaça a condição. Além dos Callbacks,
     * consulta o Middleware periodicamente (pelo primeiro Jogador) para tolerar notificações
     * perdidas.
     */
    private static EstadoPublicoDTO aguardarEstado(List<Jogador> jogadores, ServicoPartida partida,
                                                   long timeoutMs, Predicate<EstadoPublicoDTO> condicao) {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        long proximaConsulta = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INTERVALO_CONSULTA_MS);
        while (true) {
            EstadoPublicoDTO atual = maisRecente(jogadores);
            if (atual != null && condicao.test(atual)) {
                return atual;
            }
            long agora = System.nanoTime();
            if (agora - limite >= 0) {
                return null;
            }
            if (agora - proximaConsulta >= 0) {
                consultar(jogadores.get(0), partida);
                proximaConsulta = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INTERVALO_CONSULTA_MS);
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private static void consultar(Jogador jogador, ServicoPartida partida) {
        try {
            VisaoGeralDTO visao = partida.consultarEstado(jogador.id);
            if (visao != null && visao.partida() != null) {
                jogador.callback.registrar(visao.partida());
            }
        } catch (RemoteException | ExcecaoDominio | RuntimeException e) {
            // consulta é apenas um reforço dos Callbacks; ignora e tenta de novo depois
        }
    }

    private static EstadoPublicoDTO maisRecente(List<Jogador> jogadores) {
        EstadoPublicoDTO melhor = null;
        for (Jogador j : jogadores) {
            EstadoPublicoDTO e = j.callback.ultimo.get();
            if (e != null && (melhor == null || compararVersao(e, melhor) > 0)) {
                melhor = e;
            }
        }
        return melhor;
    }

    private static String nomeDe(List<Jogador> jogadores, String id) {
        for (Jogador j : jogadores) {
            if (Objects.equals(j.id, id)) {
                return j.nome;
            }
        }
        return String.valueOf(id);
    }

    /** Sai da Sala (melhor esforço) e remove a exportação de todos os Callbacks. */
    private static void encerrar(ServicosRemotos servicos, List<Jogador> jogadores) {
        for (Jogador jogador : jogadores) {
            if (jogador.id != null) {
                try {
                    servicos.sala().sair(jogador.id);
                } catch (RemoteException | ExcecaoDominio | RuntimeException e) {
                    // melhor esforço: o Middleware também detecta Callbacks inativos
                }
            }
        }
        for (Jogador jogador : jogadores) {
            jogador.callback.desexportar();
        }
    }

    private static void imprimirResumo(ResultadoCarga r, PrintStream saida) {
        Configuracao c = r.configuracao();
        saida.println();
        saida.println("===== Resumo da carga =====");
        saida.printf("Jogadores: %d | disparos por rodada: %d | rodadas executadas: %d%n",
                c.jogadores(), c.disparos(), r.rodadas().size());
        saida.printf("Total de chamadas: %d%n", r.totalChamadas());
        saida.printf("Aceitas: %d%n", r.aceitas());
        saida.printf("Rejeitadas: %d%n", r.totalRejeitadas());
        for (Map.Entry<MotivoRejeicao, Integer> e : r.rejeitadasPorMotivo().entrySet()) {
            saida.printf("  %s: %d%n", e.getKey(), e.getValue());
        }
        saida.printf("Erros (remotos/outros): %d%n", r.erros());
        for (RodadaCarga rodada : r.rodadas()) {
            if (rodada.primeiroErro() != null) {
                saida.printf("  rodada %d, primeiro erro: %s%n", rodada.numero(), rodada.primeiroErro());
            }
        }
        saida.println(r.veredito());
    }

    /** Jogador simulado: nome, identificador atribuído pelo Middleware e Callback próprio. */
    private static final class Jogador {
        final String nome;
        final CallbackObservador callback = new CallbackObservador();
        volatile String id;

        Jogador(String nome) {
            this.nome = nome;
        }
    }

    /** Desfecho de uma chamada de {@code selecionarCarta}. */
    private static final class Chamada {
        final Jogador jogador;
        final boolean aceita;
        final MotivoRejeicao motivo;
        final boolean remoto;
        final String descricaoErro;

        private Chamada(Jogador jogador, boolean aceita, MotivoRejeicao motivo, boolean remoto,
                        String descricaoErro) {
            this.jogador = jogador;
            this.aceita = aceita;
            this.motivo = motivo;
            this.remoto = remoto;
            this.descricaoErro = descricaoErro;
        }

        static Chamada aceita(Jogador j) {
            return new Chamada(j, true, null, false, null);
        }

        static Chamada rejeitada(Jogador j, MotivoRejeicao motivo) {
            return new Chamada(j, false, motivo, false, null);
        }

        static Chamada erro(Jogador j, boolean remoto, String descricao) {
            return new Chamada(j, false, null, remoto, descricao);
        }
    }

    /**
     * Callback simples: guarda o {@link EstadoPublicoDTO} mais recente (para descobrir a Partida e
     * o Jogador da vez) e ignora as demais notificações. Todos os métodos retornam imediatamente.
     */
    private static final class CallbackObservador implements CallbackJogador {
        final AtomicReference<EstadoPublicoDTO> ultimo = new AtomicReference<>();
        private CallbackJogador stub;

        synchronized CallbackJogador exportar() throws RemoteException {
            if (stub == null) {
                stub = (CallbackJogador) UnicastRemoteObject.exportObject(this, 0);
            }
            return stub;
        }

        synchronized void desexportar() {
            stub = null;
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException e) {
            }
        }

        void registrar(EstadoPublicoDTO estado) {
            ultimo.accumulateAndGet(estado, (atual, novo) ->
                    atual == null || compararVersao(novo, atual) > 0 ? novo : atual);
        }

        @Override
        public void ping() {
        }

        @Override
        public void aoAtualizarSala(SalaDTO sala) {
        }

        @Override
        public void aoDesconectarJogador(DesconexaoDTO desconexao) {
        }

        @Override
        public void aoAtualizarTabuleiro(EstadoPublicoDTO estado) {
            if (estado != null) {
                registrar(estado);
            }
        }

        @Override
        public void aoMudarTurno(MudancaTurnoDTO mudanca) {
        }

        @Override
        public void aoAtualizarPlacar(PlacarDTO placar) {
        }

        @Override
        public void aoFinalizarPartida(ResultadoPartidaDTO resultado) {
        }

        @Override
        public void aoPerguntarContinuar(PerguntaContinuarDTO pergunta) {
        }

        @Override
        public void aoCancelarPartida(CancelamentoPartidaDTO cancelamento) {
        }

        @Override
        public void aoEncerrarSessao(SumarioSessaoDTO sumario) {
        }
    }

    /** Fábrica de threads daemon para as chamadas simultâneas. */
    private static final class FabricaThreadsDaemon implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger contador = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable tarefa) {
            Thread thread = new Thread(tarefa, "carga-disparo-" + contador.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    /** Ponto de entrada do utilitário de carga. */
    public static void main(String[] args) {
        Configuracao configuracao;
        try {
            configuracao = Configuracao.interpretar(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro nos argumentos: " + e.getMessage());
            System.err.println("Uso: CargaCli [--registry.host=localhost] [--registry.porta=1099] "
                    + "[--jogadores=3] [--disparos=50] [--rodadas=1] [--timeoutSeg=15]");
            System.exit(2);
            return;
        }
        int codigo;
        try {
            ResultadoCarga resultado = executar(configuracao, System.out);
            codigo = resultado.ok() ? 0 : 1;
        } catch (ConexaoRmiException e) {
            System.err.println("Falha de conexão: " + e.getMessage());
            codigo = 3;
        } catch (ErroCargaException e) {
            System.err.println(PREFIXO_FALHA + e.getMessage());
            codigo = 3;
        }
        System.exit(codigo);
    }
}
