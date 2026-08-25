package com.renaser.os.calendar.application.ports.out.evento;

import com.renaser.os.calendar.domain.model.evento.Excepcion;

public interface SaveExcepcionPort {

    /** UNIQUE (evento_id, inicio_ocurrencia) — upsertOverride() del repo viejo. */
    Excepcion upsert(Excepcion excepcion);
}
