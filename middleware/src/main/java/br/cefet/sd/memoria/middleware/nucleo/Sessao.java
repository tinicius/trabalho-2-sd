package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.dto.SumarioJogadorDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import br.cefet.sd.memoria.comum.regras.Aproveitamento;
import br.cefet.sd.memoria.comum.regras.ApuracaoPartida;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Estado acumulado de uma Sessão: contagem de Partidas, estatísticas por Jogador, empates e
 * rodízio do primeiro da vez. Não é thread-safe: a Sala serializa o acesso.
 */
public final class Sessao {

    private int partidasConcluidas;
    private int partidasCanceladas;
    private long ultimoIdPartida;
    private final Map<String, EstatisticaJogadorSessao> porJogador = new LinkedHashMap<>();
    private final List<List<String>> empates = new ArrayList<>();
    private String idIniciadorAnterior;
    private Long ordemIniciadorAnterior;

    /** Cria uma Sessão vazia (zero Partidas, sem estatísticas). */
    public Sessao() {
    }

    /** @return número de Partidas concluídas (vitória ou empate) */
    public int partidasConcluidas() {
        return partidasConcluidas;
    }

    /** @return número de Partidas canceladas */
    public int partidasCanceladas() {
        return partidasCanceladas;
    }

    /** @return grupos de nomes empatados, um por Partida empatada (cópia imutável) */
    public List<List<String>> empates() {
        List<List<String>> copia = new ArrayList<>(empates.size());
        for (List<String> grupo : empates) {
            copia.add(List.copyOf(grupo));
        }
        return Collections.unmodifiableList(copia);
    }

    /** @return estatísticas por Jogador (visão somente leitura), na ordem da primeira Partida concluída */
    public Collection<EstatisticaJogadorSessao> estatisticas() {
        return Collections.unmodifiableCollection(porJogador.values());
    }

    /** @return id do último iniciador de Partida, ou {@code null} se nenhuma Partida foi iniciada */
    public String idIniciadorAnterior() {
        return idIniciadorAnterior;
    }

    /** Reserva e devolve o identificador da próxima Partida (crescente, a partir de 1). */
    public long proximoIdPartida() {
        return ++ultimoIdPartida;
    }

    /**
     * Registra uma Partida concluída: acumula pares e tentativas e dá vitória apenas a vencedor
     * único; em empate, registra o grupo em {@link #empates()}.
     *
     * @throws IllegalArgumentException se a lista for nula, vazia ou tiver ids repetidos
     */
    public void registrarConcluida(List<ResultadoJogadorPartida> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            throw new IllegalArgumentException("resultados não pode ser nulo nem vazio");
        }
        Map<String, Integer> paresPorId = new LinkedHashMap<>();
        Map<String, String> nomePorId = new LinkedHashMap<>();
        for (ResultadoJogadorPartida r : resultados) {
            if (r == null) {
                throw new IllegalArgumentException("resultado nulo");
            }
            if (paresPorId.put(r.idJogador(), r.pares()) != null) {
                throw new IllegalArgumentException("jogador repetido: " + r.idJogador());
            }
            nomePorId.put(r.idJogador(), r.nome());
        }
        List<String> vencedores = ApuracaoPartida.vencedores(paresPorId);
        String vencedorUnico = vencedores.size() == 1 ? vencedores.get(0) : null;

        for (ResultadoJogadorPartida r : resultados) {
            EstatisticaJogadorSessao estatistica = porJogador.computeIfAbsent(
                    r.idJogador(), id -> new EstatisticaJogadorSessao(id, r.nome()));
            estatistica.acumular(r.pares(), r.tentativas(), r.idJogador().equals(vencedorUnico));
        }
        if (vencedorUnico == null) {
            List<String> nomes = new ArrayList<>(vencedores.size());
            for (String id : vencedores) {
                nomes.add(nomePorId.get(id));
            }
            empates.add(List.copyOf(nomes));
        }
        partidasConcluidas++;
    }

    /** Registra uma Partida cancelada, sem alterar as estatísticas por Jogador. */
    public void registrarCancelada() {
        partidasCanceladas++;
    }

    /** Marca um Jogador como desconectado; ids ausentes das estatísticas são ignorados. */
    public void marcarDesconectado(String idJogador) {
        EstatisticaJogadorSessao estatistica = porJogador.get(idJogador);
        if (estatistica != null) {
            estatistica.marcarDesconectado();
        }
    }

    /**
     * Escolhe e registra o primeiro Jogador da próxima Partida, em rodízio por {@code ordemEntrada}.
     *
     * @see #escolherPrimeiro(Collection, Map, OptionalLong)
     */
    public String selecionarPrimeiro(Collection<String> idsPresentes, Map<String, Long> ordemEntradaPorId) {
        OptionalLong anterior = ordemIniciadorAnterior == null
                ? OptionalLong.empty() : OptionalLong.of(ordemIniciadorAnterior);
        String escolhido = escolherPrimeiro(idsPresentes, ordemEntradaPorId, anterior);
        idIniciadorAnterior = escolhido;
        ordemIniciadorAnterior = ordemEntradaPorId.get(escolhido);
        return escolhido;
    }

    /**
     * Escolhe o presente de menor {@code ordemEntrada} maior que a do iniciador anterior ou, se
     * não houver, o de menor {@code ordemEntrada}.
     *
     * @throws IllegalArgumentException se não houver presentes ou faltar a ordem de algum presente
     */
    public static String escolherPrimeiro(Collection<String> idsPresentes,
                                          Map<String, Long> ordemEntradaPorId,
                                          OptionalLong ordemIniciadorAnterior) {
        if (idsPresentes == null || idsPresentes.isEmpty()) {
            throw new IllegalArgumentException("idsPresentes não pode ser nulo nem vazio");
        }
        String menor = null;
        long ordemMenor = 0;
        String proximo = null;
        long ordemProximo = 0;
        for (String id : idsPresentes) {
            Long ordem = ordemEntradaPorId.get(id);
            if (ordem == null) {
                throw new IllegalArgumentException("ordemEntrada ausente para " + id);
            }
            if (menor == null || ordem < ordemMenor) {
                menor = id;
                ordemMenor = ordem;
            }
            if (ordemIniciadorAnterior.isPresent() && ordem > ordemIniciadorAnterior.getAsLong()
                    && (proximo == null || ordem < ordemProximo)) {
                proximo = id;
                ordemProximo = ordem;
            }
        }
        return proximo != null ? proximo : menor;
    }

    /** Gera o Sumario_da_Sessao com os Jogadores que concluíram ao menos uma Partida. */
    public SumarioSessaoDTO gerarSumario(long idPartida, long sequencia) {
        List<SumarioJogadorDTO> jogadores = new ArrayList<>(porJogador.size());
        for (EstatisticaJogadorSessao e : porJogador.values()) {
            jogadores.add(new SumarioJogadorDTO(
                    e.nome(), e.vitorias(), e.pares(), e.tentativas(),
                    Aproveitamento.percentual(e.pares(), e.tentativas()),
                    e.tentativas() == 0, e.desconectado()));
        }
        return new SumarioSessaoDTO(idPartida, sequencia, partidasConcluidas, partidasCanceladas,
                empates(), jogadores, CalculadoraCampeao.campeoes(porJogador.values()));
    }
}
