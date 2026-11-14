package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Los eventos del calendario con el enlace de Meet automático (spec 040). Es lo que llama el controller: crear, editar, borrar y cancelar una
 * fecha hacen lo de siempre y, si el evento pide Meet, además le piden a Google la sala <b>después</b> de guardar —nunca con una transacción
 * abierta— y nunca fallan por culpa de Google (FR-001, SC-003).
 */
public interface EventosConMeetUseCase {

    /** Igual que el listado de siempre; en un evento con Meet, cada fecha trae su propio enlace (FR-017). */
    List<OcurrenciaVista> listar(UserId actor, Instant desde, Instant hasta, Formacion formacion);

    /** El evento y, para ADMIN y ALCHEMIST, cómo van sus salas. */
    EventoConMeetVista obtener(UserId actor, EventoId eventoId);

    /** 409 si pide Meet y no hay cuenta de Google conectada; 404 si Soporte TI apagó la función. */
    EventoConMeetVista crear(CrearEventoCommand comando);

    EventoConMeetVista actualizar(ActualizarEventoCommand comando);

    void eliminar(UserId actor, EventoId eventoId);

    void cancelarOcurrencia(UserId actor, EventoId eventoId, Instant inicioOcurrencia);

    /** Spec 053 FR-014: esta fecha y las siguientes; si es la primera, se elimina el evento entero. */
    void cancelarDesde(UserId actor, EventoId eventoId, Instant inicioOcurrencia);
}
