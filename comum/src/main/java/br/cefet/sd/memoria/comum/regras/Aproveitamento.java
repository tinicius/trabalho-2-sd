package br.cefet.sd.memoria.comum.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cálculo e comparação do aproveitamento (pares / tentativas) de um jogador. */
public final class Aproveitamento {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int CASAS_DECIMAIS = 2;

    private Aproveitamento() {
    }

    /** Percentual de aproveitamento com 2 casas (HALF_UP); vale 0.00 sem tentativas. */
    public static BigDecimal percentual(long pares, long tentativas) {
        validar(pares, tentativas);
        if (tentativas == 0) {
            return BigDecimal.ZERO.setScale(CASAS_DECIMAIS, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(pares)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(tentativas), CASAS_DECIMAIS, RoundingMode.HALF_UP);
    }

    /** Compara dois aproveitamentos sem arredondamento (produto cruzado); sem tentativas vale 0. */
    public static int compararExato(long paresA, long tentA, long paresB, long tentB) {
        validar(paresA, tentA);
        validar(paresB, tentB);
        if (tentA == 0 && tentB == 0) {
            return 0;
        }
        if (tentA == 0) {
            // A vale 0; B vale paresB/tentB >= 0.
            return paresB == 0 ? 0 : -1;
        }
        if (tentB == 0) {
            return paresA == 0 ? 0 : 1;
        }
        long esquerda = Math.multiplyExact(paresA, tentB);
        long direita = Math.multiplyExact(paresB, tentA);
        return Long.compare(esquerda, direita);
    }

    private static void validar(long pares, long tentativas) {
        if (pares < 0) {
            throw new IllegalArgumentException("pares não pode ser negativo: " + pares);
        }
        if (tentativas < 0) {
            throw new IllegalArgumentException("tentativas não pode ser negativo: " + tentativas);
        }
        if (pares > tentativas) {
            throw new IllegalArgumentException(
                    "pares (" + pares + ") não pode exceder tentativas (" + tentativas + ")");
        }
    }
}
