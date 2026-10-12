package br.cefet.sd.memoria.comum.regras;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Apuração do resultado de uma partida. */
public final class ApuracaoPartida {

    private ApuracaoPartida() {
    }

    /** Devolve os jogadores com o máximo de pares (mais de um indica empate), na ordem de iteração do mapa. */
    public static List<String> vencedores(Map<String, Integer> paresPorJogador) {
        if (paresPorJogador == null) {
            throw new IllegalArgumentException("paresPorJogador não pode ser nulo");
        }
        if (paresPorJogador.isEmpty()) {
            throw new IllegalArgumentException("paresPorJogador não pode ser vazio");
        }
        int maximo = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entrada : paresPorJogador.entrySet()) {
            if (entrada.getKey() == null) {
                throw new IllegalArgumentException("jogador não pode ser nulo");
            }
            Integer pares = entrada.getValue();
            if (pares == null) {
                throw new IllegalArgumentException("pares nulos para o jogador " + entrada.getKey());
            }
            if (pares < 0) {
                throw new IllegalArgumentException(
                        "pares negativos para o jogador " + entrada.getKey() + ": " + pares);
            }
            maximo = Math.max(maximo, pares);
        }
        List<String> resultado = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : paresPorJogador.entrySet()) {
            if (entrada.getValue() == maximo) {
                resultado.add(entrada.getKey());
            }
        }
        return Collections.unmodifiableList(resultado);
    }
}
