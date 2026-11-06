package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeet;
import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeetUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

@Service
class DisponibilidadDeMeetService implements DisponibilidadDeMeetUseCase {

    private final AccesoEventoService accesoEventoService;
    private final DisponibilidadDeLasSalas disponibilidad;

    DisponibilidadDeMeetService(AccesoEventoService accesoEventoService, DisponibilidadDeLasSalas disponibilidad) {
        this.accesoEventoService = accesoEventoService;
        this.disponibilidad = disponibilidad;
    }

    @Override
    public DisponibilidadDeMeet consultar(UserId actor) {
        accesoEventoService.requirePuedeCrearEventos(actor);
        return disponibilidad.de(actor);
    }
}
