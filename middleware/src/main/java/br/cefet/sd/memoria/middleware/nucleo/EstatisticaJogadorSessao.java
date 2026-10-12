package br.cefet.sd.memoria.middleware.nucleo;

import java.util.Objects;

/** Estatísticas acumuladas de um Jogador na Sessão (apenas Partidas concluídas). */
public final class EstatisticaJogadorSessao {

    private final String id;
    private final String nome;
    private int vitorias;
    private int pares;
    private int tentativas;
    private boolean desconectado;

    /** Cria uma estatística já preenchida; exige valores não negativos e {@code pares <= tentativas}. */
    public EstatisticaJogadorSessao(String id, String nome, int vitorias, int pares, int tentativas,
                                    boolean desconectado) {
        this.id = Objects.requireNonNull(id, "id");
        this.nome = Objects.requireNonNull(nome, "nome");
        if (vitorias < 0 || pares < 0 || tentativas < 0) {
            throw new IllegalArgumentException("vitorias, pares e tentativas devem ser >= 0");
        }
        if (pares > tentativas) {
            throw new IllegalArgumentException("pares deve ser <= tentativas");
        }
        this.vitorias = vitorias;
        this.pares = pares;
        this.tentativas = tentativas;
        this.desconectado = desconectado;
    }

    /** Cria uma estatística zerada e conectada. */
    EstatisticaJogadorSessao(String id, String nome) {
        this(id, nome, 0, 0, 0, false);
    }

    /** @return identificador estável do participante */
    public String id() {
        return id;
    }

    /** @return nome de exibição */
    public String nome() {
        return nome;
    }

    /** @return vitórias acumuladas (vencedor único em Partida concluída) */
    public int vitorias() {
        return vitorias;
    }

    /** @return soma dos pares das Partidas concluídas */
    public int pares() {
        return pares;
    }

    /** @return soma das tentativas das Partidas concluídas */
    public int tentativas() {
        return tentativas;
    }

    /** @return {@code true} se o Jogador já saiu da Sala */
    public boolean desconectado() {
        return desconectado;
    }

    void acumular(int paresPartida, int tentativasPartida, boolean venceu) {
        pares += paresPartida;
        tentativas += tentativasPartida;
        if (venceu) {
            vitorias++;
        }
    }

    void marcarDesconectado() {
        desconectado = true;
    }

    @Override
    public String toString() {
        return "EstatisticaJogadorSessao[id=" + id + ", nome=" + nome + ", vitorias=" + vitorias
                + ", pares=" + pares + ", tentativas=" + tentativas + ", desconectado=" + desconectado + "]";
    }
}
