package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.regras.Aproveitamento;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Determina o(s) Campeão(ões) da Sessão pelo máximo lexicográfico de (vitórias, pares,
 * aproveitamento exato, ver {@link Aproveitamento#compararExato(long, long, long, long)}).
 */
public final class CalculadoraCampeao {

    private CalculadoraCampeao() {
    }

    /** Retorna os nomes dos campeões (todos os empatados), em ordem alfabética. */
    public static List<String> campeoes(Collection<EstatisticaJogadorSessao> todos) {
        if (todos == null) {
            throw new IllegalArgumentException("todos não pode ser nulo");
        }
        List<EstatisticaJogadorSessao> melhores = new ArrayList<>();
        for (EstatisticaJogadorSessao atual : todos) {
            if (atual == null) {
                throw new IllegalArgumentException("estatística nula");
            }
            if (melhores.isEmpty()) {
                melhores.add(atual);
                continue;
            }
            int cmp = comparar(atual, melhores.get(0));
            if (cmp > 0) {
                melhores.clear();
                melhores.add(atual);
            } else if (cmp == 0) {
                melhores.add(atual);
            }
        }
        List<String> nomes = new ArrayList<>(melhores.size());
        for (EstatisticaJogadorSessao e : melhores) {
            nomes.add(e.nome());
        }
        Collections.sort(nomes);
        return Collections.unmodifiableList(nomes);
    }

    /** Compara duas estatísticas por (vitórias, pares, aproveitamento exato). */
    static int comparar(EstatisticaJogadorSessao a, EstatisticaJogadorSessao b) {
        int cmp = Integer.compare(a.vitorias(), b.vitorias());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.pares(), b.pares());
        if (cmp != 0) {
            return cmp;
        }
        return Aproveitamento.compararExato(a.pares(), a.tentativas(), b.pares(), b.tentativas());
    }
}
