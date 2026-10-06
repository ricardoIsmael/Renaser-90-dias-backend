package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.asistencia.VerRespuestasDelEventoUseCase;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Hoja «Quién respondió» (D-256): solo lectura, sin transacción propia. */
@Service
public class RespuestasDelEventoService implements VerRespuestasDelEventoUseCase {

    private final AccesoALaListaService acceso;
    private final PersonasConvocadasService personas;

    RespuestasDelEventoService(AccesoALaListaService acceso, PersonasConvocadasService personas) {
        this.acceso = acceso;
        this.personas = personas;
    }

    @Override
    public RespuestasDelEvento respuestas(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        var ocurrencia = acceso.autorizar(actor, eventoId, inicioOcurrencia);
        return new RespuestasDelEvento(ocurrencia.slot(), personas.de(ocurrencia.evento(), ocurrencia.slot()));
    }
}
