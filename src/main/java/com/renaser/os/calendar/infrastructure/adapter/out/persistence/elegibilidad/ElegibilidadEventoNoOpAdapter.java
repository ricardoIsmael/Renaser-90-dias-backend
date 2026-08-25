package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * NoOp DELIBERADO — ver el javadoc completo de {@link ConsultarElegibilidadEventoPort} y
 * docs/MODULO_CALENDAR.md §6. Sin el % de cumplimiento semanal real (habits+rocks), la
 * unica respuesta honesta es "no elegible": nunca se inventan datos (CLAUDE.MD §0.6).
 */
@Component
class ElegibilidadEventoNoOpAdapter implements ConsultarElegibilidadEventoPort {

    @Override
    public boolean esElegible(UserId usuarioId, TipoEvento tipoEvento) {
        return false;
    }
}
