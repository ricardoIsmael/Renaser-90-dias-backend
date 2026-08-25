package com.renaser.os.points.domain.model.ajuste;

public record ResultadoAjuste(int deltaSolicitado, int deltaAplicado, int saldoAnterior, int saldoPosterior) {

    public ResultadoAjuste {
        if (saldoAnterior < 0 || saldoPosterior < 0) {
            throw new IllegalArgumentException("Los saldos de puntos de liga nunca son negativos (piso 0)");
        }
    }
}
