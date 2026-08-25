package com.renaser.os.points.application.ports.out.ajuste;

import com.renaser.os.points.domain.model.ajuste.AjustePuntos;

public interface SaveAjustePort {

    /** Inserta un nuevo asiento en el ledger. Nunca se actualiza uno existente (append-only). */
    AjustePuntos save(AjustePuntos ajuste);
}
