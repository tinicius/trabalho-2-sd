package br.cefet.sd.memoria.middleware.entrega;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Conjunto thread-safe dos {@link EntregadorJogador} dos Jogadores da Sala, na ordem de entrada. */
public final class DistribuidorEventos {

    private final Object monitor = new Object();
    private final Map<String, EntregadorJogador> entregadores = new LinkedHashMap<>();

    /** Registra o entregador de um Jogador; falha se já houver um para o mesmo id. */
    public void adicionar(EntregadorJogador entregador) {
        Objects.requireNonNull(entregador, "entregador");
        synchronized (monitor) {
            if (entregadores.putIfAbsent(entregador.idJogador(), entregador) != null) {
                throw new IllegalStateException("jogador já registrado: " + entregador.idJogador());
            }
        }
    }

    /** Remove o Jogador e fecha seu entregador imediatamente, descartando a fila. */
    public boolean remover(String idJogador) {
        EntregadorJogador removido;
        synchronized (monitor) {
            removido = entregadores.remove(idJogador);
        }
        if (removido == null) {
            return false;
        }
        removido.fecharAgora();
        return true;
    }

    /** Remove o Jogador, deixando seu entregador esvaziar a fila antes de terminar. */
    public boolean removerAposEsvaziar(String idJogador) {
        EntregadorJogador removido;
        synchronized (monitor) {
            removido = entregadores.remove(idJogador);
        }
        if (removido == null) {
            return false;
        }
        removido.fecharAposEsvaziar();
        return true;
    }

    /** Remove todos os Jogadores deixando cada entregador esvaziar sua fila. */
    public void removerTodosAposEsvaziar() {
        for (EntregadorJogador e : esvaziarMapa()) {
            e.fecharAposEsvaziar();
        }
    }

    /** Remove todos os Jogadores descartando as filas. */
    public void removerTodosAgora() {
        for (EntregadorJogador e : esvaziarMapa()) {
            e.fecharAgora();
        }
    }

    /** Enfileira o evento para todos os Jogadores, na ordem de entrada, sem bloquear. */
    public void publicar(EventoCallback evento) {
        Objects.requireNonNull(evento, "evento");
        synchronized (monitor) {
            for (EntregadorJogador e : entregadores.values()) {
                e.enfileirar(evento);
            }
        }
    }

    /** Enfileira o evento apenas para um Jogador; {@code false} se ele não está registrado. */
    public boolean publicarPara(String idJogador, EventoCallback evento) {
        Objects.requireNonNull(evento, "evento");
        EntregadorJogador destino;
        synchronized (monitor) {
            destino = entregadores.get(idJogador);
        }
        if (destino == null) {
            return false;
        }
        destino.enfileirar(evento);
        return true;
    }

    /** Indica se o Jogador está registrado. */
    public boolean contem(String idJogador) {
        synchronized (monitor) {
            return entregadores.containsKey(idJogador);
        }
    }

    /** Quantidade de Jogadores registrados. */
    public int quantidade() {
        synchronized (monitor) {
            return entregadores.size();
        }
    }

    private List<EntregadorJogador> esvaziarMapa() {
        synchronized (monitor) {
            List<EntregadorJogador> copia = new ArrayList<>(entregadores.values());
            entregadores.clear();
            return copia;
        }
    }
}
