package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.dto.CartaPublicaDTO;
import br.cefet.sd.memoria.comum.dto.CatalogoImagensDTO;
import br.cefet.sd.memoria.comum.dto.EstadoCartaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import br.cefet.sd.memoria.comum.dto.TipoEvento;
import br.cefet.sd.memoria.comum.excecao.MotivoRejeicao;
import br.cefet.sd.memoria.comum.regras.Aproveitamento;
import br.cefet.sd.memoria.comum.regras.ApuracaoPartida;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Núcleo de regras de uma Partida do jogo da memória: puro, síncrono e determinístico.
 * Não é seguro para uso concorrente; a {@code Sala} chama todos os métodos sob seu lock.
 */
public final class Partida implements VisaoPartida {

    private final long id;
    private final int linhas;
    private final int colunas;
    private final int n;
    /** Secreta: nunca sai desta classe. */
    private final int[] matriz;
    private final EstadoCarta[] cartas;
    private final List<String> ordem;
    private final Map<String, String> nomesPorId;
    private final CatalogoImagensDTO catalogo;
    private final int prazoContinuarSeg;
    private final Map<String, ContadorPartida> contadores = new LinkedHashMap<>();

    private int indiceVez;
    private int primeira = -1;
    private int segunda = -1;
    private SubEstado sub = SubEstado.AGUARDANDO_1A;
    private StatusPartida status = StatusPartida.EM_ANDAMENTO;
    private int cartasConquistadas;
    private long proximaSequencia = 1;
    private long epoca;
    private boolean iniciada;

    /**
     * Cria uma Partida em andamento, aguardando a 1ª carta do Jogador {@code indiceVezInicial}.
     *
     * @param matriz disposição secreta (cada id de 1..n/2 exatamente duas vezes); copiada
     * @param catalogo catálogo de imagens, enviado no evento inicial (ao menos n/2 pares)
     * @throws IllegalArgumentException se algum parâmetro for inconsistente
     * @throws NullPointerException     se algum objeto obrigatório for nulo
     */
    public Partida(long id, int linhas, int colunas, int[] matriz, List<String> ordemIdsJogadores,
            Map<String, String> nomesPorId, int indiceVezInicial, CatalogoImagensDTO catalogo,
            int prazoContinuarSeg) {
        if (linhas < 1 || colunas < 1) {
            throw new IllegalArgumentException("linhas e colunas devem ser >= 1");
        }
        this.n = Math.multiplyExact(linhas, colunas);
        this.matriz = GeradorMatriz.validarDisposicao(matriz, n);
        this.ordem = List.copyOf(Objects.requireNonNull(ordemIdsJogadores, "ordemIdsJogadores"));
        if (ordem.isEmpty()) {
            throw new IllegalArgumentException("ordemIdsJogadores nao pode ser vazia");
        }
        if (new HashSet<>(ordem).size() != ordem.size()) {
            throw new IllegalArgumentException("ordemIdsJogadores nao pode ter repeticoes");
        }
        Objects.requireNonNull(nomesPorId, "nomesPorId");
        Map<String, String> nomes = new LinkedHashMap<>();
        for (String idJogador : ordem) {
            String nome = nomesPorId.get(idJogador);
            if (nome == null) {
                throw new IllegalArgumentException("sem nome para o jogador " + idJogador);
            }
            nomes.put(idJogador, nome);
            contadores.put(idJogador, ContadorPartida.ZERO);
        }
        this.nomesPorId = Collections.unmodifiableMap(nomes);
        if (indiceVezInicial < 0 || indiceVezInicial >= ordem.size()) {
            throw new IllegalArgumentException("indiceVezInicial fora da ordem: " + indiceVezInicial);
        }
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        if (catalogo.recursoPorPar().size() < n / 2) {
            throw new IllegalArgumentException("catalogo com menos imagens que pares do tabuleiro");
        }
        if (prazoContinuarSeg < 0) {
            throw new IllegalArgumentException("prazoContinuarSeg deve ser >= 0");
        }
        this.id = id;
        this.linhas = linhas;
        this.colunas = colunas;
        this.indiceVez = indiceVezInicial;
        this.prazoContinuarSeg = prazoContinuarSeg;
        this.cartas = new EstadoCarta[n];
        java.util.Arrays.fill(cartas, EstadoCarta.FECHADA);
    }

    /**
     * Produz o evento inicial {@code INICIO_PARTIDA} (sequência 1, com catálogo) e incrementa a época.
     *
     * @throws IllegalStateException se já foi chamado
     */
    public List<EventoPartida> iniciar() {
        if (iniciada || proximaSequencia != 1 || status != StatusPartida.EM_ANDAMENTO) {
            throw new IllegalStateException("Partida ja iniciada");
        }
        iniciada = true;
        epoca++;
        List<EventoPartida> eventos = new ArrayList<>();
        eventos.add(EventoPartida.tabuleiro(
                montarEstado(consumirSequencia(), TipoEvento.INICIO_PARTIDA, null, true)));
        return List.copyOf(eventos);
    }

    /**
     * Processa a seleção de uma carta; se rejeitada, estado e sequência ficam intocados.
     *
     * @return {@link ResultadoSelecao.Rejeitada} ou {@link ResultadoSelecao.Aceita}
     */
    public ResultadoSelecao selecionar(String idParticipante, Long idPartida, Integer posicao,
            boolean disputouLock) {
        Optional<MotivoRejeicao> motivo =
                ValidadorJogada.validar(this, idParticipante, idPartida, posicao, disputouLock);
        if (motivo.isPresent()) {
            return new ResultadoSelecao.Rejeitada(motivo.get());
        }
        int p = posicao;
        String jogador = ordem.get(indiceVez);
        String nome = nomesPorId.get(jogador);
        List<EventoPartida> eventos = new ArrayList<>();
        epoca++;
        cartas[p] = EstadoCarta.REVELADA;

        if (sub == SubEstado.AGUARDANDO_1A) {
            primeira = p;
            sub = SubEstado.AGUARDANDO_2A;
            eventos.add(tabuleiro(TipoEvento.CARTA_REVELADA, nome));
            return new ResultadoSelecao.Aceita(eventos, AcaoTemporizador.REARMAR_INATIVIDADE);
        }

        segunda = p;
        if (matriz[primeira] == matriz[segunda]) {
            cartas[primeira] = EstadoCarta.CONQUISTADA;
            cartas[segunda] = EstadoCarta.CONQUISTADA;
            cartasConquistadas += 2;
            contadores.put(jogador, contadores.get(jogador).comParEncontrado());
            primeira = -1;
            segunda = -1;
            sub = SubEstado.AGUARDANDO_1A;
            if (cartasConquistadas == n) {
                status = StatusPartida.CONCLUIDA;
                eventos.add(tabuleiro(TipoEvento.PARTIDA_CONCLUIDA, nome));
                eventos.add(placarEvento());
                eventos.add(resultadoEvento());
                eventos.add(EventoPartida.pergunta(
                        new PerguntaContinuarDTO(id, consumirSequencia(), prazoContinuarSeg)));
                return new ResultadoSelecao.Aceita(eventos, AcaoTemporizador.PARTIDA_CONCLUIDA);
            }
            eventos.add(tabuleiro(TipoEvento.PAR_ENCONTRADO, nome));
            eventos.add(placarEvento());
            return new ResultadoSelecao.Aceita(eventos, AcaoTemporizador.REARMAR_INATIVIDADE);
        }

        contadores.put(jogador, contadores.get(jogador).comErro());
        sub = SubEstado.INTERVALO_ERRO;
        eventos.add(tabuleiro(TipoEvento.ERRO, nome));
        eventos.add(placarEvento());
        return new ResultadoSelecao.Aceita(eventos, AcaoTemporizador.INICIAR_INTERVALO_ERRO);
    }

    /**
     * Encerra o Intervalo_de_Erro: fecha as duas cartas e passa a vez. Retorna
     * {@link ResultadoTemporizador#IGNORADO} se a {@code epoca} está obsoleta ou o estado não confere.
     */
    public ResultadoTemporizador fecharAposErro(long epoca) {
        if (status != StatusPartida.EM_ANDAMENTO || epoca != this.epoca || sub != SubEstado.INTERVALO_ERRO) {
            return ResultadoTemporizador.IGNORADO;
        }
        String nomeQuePerdeuAVez = nomesPorId.get(ordem.get(indiceVez));
        fecharTentativaEPassarVez();
        return new ResultadoTemporizador(true, eventosDeVezPassada(TipoEvento.VEZ_PASSADA, nomeQuePerdeuAVez),
                AcaoTemporizador.REARMAR_INATIVIDADE);
    }

    /**
     * Trata a inatividade do Jogador da vez: fecha a tentativa incompleta, sem contar Tentativa, e
     * passa a vez. Retorna {@link ResultadoTemporizador#IGNORADO} se a {@code epoca} está obsoleta
     * ou o estado não confere.
     */
    public ResultadoTemporizador expirarInatividade(long epoca) {
        if (status != StatusPartida.EM_ANDAMENTO || epoca != this.epoca || sub == SubEstado.INTERVALO_ERRO) {
            return ResultadoTemporizador.IGNORADO;
        }
        String nomeInativo = nomesPorId.get(ordem.get(indiceVez));
        fecharTentativaEPassarVez();
        return new ResultadoTemporizador(true, eventosDeVezPassada(TipoEvento.INATIVIDADE, nomeInativo),
                AcaoTemporizador.REARMAR_INATIVIDADE);
    }

    /** Cancela a Partida em andamento e invalida os temporizadores (idempotente; não cria eventos). */
    public void cancelar() {
        if (status != StatusPartida.EM_ANDAMENTO) {
            return;
        }
        status = StatusPartida.CANCELADA;
        epoca++;
    }

    /** Reserva e devolve o próximo número de {@code Sequencia_de_Estado}. */
    public long consumirSequencia() {
        return proximaSequencia++;
    }

    /** Snapshot público atual; o {@code idPar} só é preenchido para cartas reveladas ou conquistadas. */
    public EstadoPublicoDTO estadoPublico(TipoEvento ultimo, String nomeJogadorEvento) {
        Objects.requireNonNull(ultimo, "ultimo");
        boolean comCatalogo = ultimo == TipoEvento.INICIO_PARTIDA || ultimo == TipoEvento.CONSULTA;
        return montarEstado(Math.max(1, proximaSequencia - 1), ultimo, nomeJogadorEvento, comCatalogo);
    }

    /** Placar atual, na ordem dos Jogadores. */
    public List<PlacarJogadorDTO> placar() {
        List<PlacarJogadorDTO> lista = new ArrayList<>(ordem.size());
        for (String idJogador : ordem) {
            ContadorPartida c = contadores.get(idJogador);
            lista.add(new PlacarJogadorDTO(idJogador, nomesPorId.get(idJogador), c.pares(), c.tentativas(),
                    Aproveitamento.percentual(c.pares(), c.tentativas()), c.tentativas() == 0));
        }
        return List.copyOf(lista);
    }

    /** Identificador da Partida. */
    public long id() {
        return id;
    }

    /** Número de linhas do tabuleiro. */
    public int linhas() {
        return linhas;
    }

    /** Número de colunas do tabuleiro. */
    public int colunas() {
        return colunas;
    }

    /** Ids dos Jogadores na ordem de turnos (imutável). */
    public List<String> ordemJogadores() {
        return ordem;
    }

    /** Nome do Jogador, ou {@code null} se o id não participa da Partida. */
    public String nomeDoJogador(String idJogador) {
        return nomesPorId.get(idJogador);
    }

    /** Nome do Jogador da vez. */
    public String nomeJogadorDaVez() {
        return nomesPorId.get(ordem.get(indiceVez));
    }

    /**
     * Contadores de um Jogador.
     *
     * @throws IllegalArgumentException se o id não participa da Partida
     */
    public ContadorPartida contador(String idJogador) {
        ContadorPartida c = contadores.get(idJogador);
        if (c == null) {
            throw new IllegalArgumentException("jogador desconhecido: " + idJogador);
        }
        return c;
    }

    /** Pares encontrados por todos os Jogadores (cartas conquistadas / 2). */
    public int paresConquistados() {
        return cartasConquistadas / 2;
    }

    /** Número total de pares do tabuleiro ({@code n / 2}). */
    public int totalPares() {
        return n / 2;
    }

    /** Próximo número de {@code Sequencia_de_Estado} a ser consumido (começa em 1). */
    public long proximaSequencia() {
        return proximaSequencia;
    }

    /** Época vigente dos temporizadores; incrementada a cada transição que os invalida. */
    public long epoca() {
        return epoca;
    }

    @Override
    public boolean participanteConhecido(String idParticipante) {
        return idParticipante != null && nomesPorId.containsKey(idParticipante);
    }

    @Override
    public OptionalLong idPartidaCorrente() {
        return OptionalLong.of(id);
    }

    @Override
    public StatusPartida status() {
        return status;
    }

    @Override
    public String idJogadorDaVez() {
        return ordem.get(indiceVez);
    }

    @Override
    public SubEstado subEstado() {
        return sub;
    }

    @Override
    public int tamanho() {
        return n;
    }

    @Override
    public int primeiraPosicao() {
        return primeira;
    }

    @Override
    public EstadoCarta estadoCarta(int posicao) {
        return cartas[posicao];
    }

    private void fecharTentativaEPassarVez() {
        if (primeira >= 0) {
            cartas[primeira] = EstadoCarta.FECHADA;
        }
        if (segunda >= 0) {
            cartas[segunda] = EstadoCarta.FECHADA;
        }
        primeira = -1;
        segunda = -1;
        sub = SubEstado.AGUARDANDO_1A;
        indiceVez = (indiceVez + 1) % ordem.size();
        epoca++;
    }

    private List<EventoPartida> eventosDeVezPassada(TipoEvento tipo, String nomeQuePerdeuAVez) {
        List<EventoPartida> eventos = new ArrayList<>();
        eventos.add(tabuleiro(tipo, nomeQuePerdeuAVez));
        eventos.add(EventoPartida.turno(new MudancaTurnoDTO(id, consumirSequencia(),
                ordem.get(indiceVez), nomesPorId.get(ordem.get(indiceVez)))));
        return List.copyOf(eventos);
    }

    private EventoPartida tabuleiro(TipoEvento tipo, String nomeJogadorEvento) {
        return EventoPartida.tabuleiro(montarEstado(consumirSequencia(), tipo, nomeJogadorEvento, false));
    }

    private EventoPartida placarEvento() {
        return EventoPartida.placar(new PlacarDTO(id, consumirSequencia(), placar()));
    }

    private EventoPartida resultadoEvento() {
        Map<String, Integer> pares = new LinkedHashMap<>();
        for (String idJogador : ordem) {
            pares.put(idJogador, contadores.get(idJogador).pares());
        }
        List<String> nomesVencedores = new ArrayList<>();
        for (String idVencedor : ApuracaoPartida.vencedores(pares)) {
            nomesVencedores.add(nomesPorId.get(idVencedor));
        }
        return EventoPartida.resultado(new ResultadoPartidaDTO(id, consumirSequencia(),
                nomesVencedores.size() >= 2, nomesVencedores, placar(), prazoContinuarSeg));
    }

    private EstadoPublicoDTO montarEstado(long sequencia, TipoEvento ultimo, String nomeJogadorEvento,
            boolean comCatalogo) {
        List<CartaPublicaDTO> publicas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            publicas.add(switch (cartas[i]) {
                case FECHADA -> new CartaPublicaDTO(EstadoCartaDTO.FECHADA, null);
                case REVELADA -> new CartaPublicaDTO(EstadoCartaDTO.REVELADA, matriz[i]);
                case CONQUISTADA -> new CartaPublicaDTO(EstadoCartaDTO.CONQUISTADA, matriz[i]);
            });
        }
        String idVez = status == StatusPartida.EM_ANDAMENTO ? ordem.get(indiceVez) : null;
        return new EstadoPublicoDTO(id, sequencia, linhas, colunas, publicas, ordem, idVez, placar(),
                status, ultimo, nomeJogadorEvento, comCatalogo ? catalogo : null);
    }
}
