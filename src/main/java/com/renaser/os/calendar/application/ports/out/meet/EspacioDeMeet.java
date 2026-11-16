package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;

/**
 * El espacio que Google acaba de crear con la Meet API (spec 040 FR-040): su enlace y lo que la respuesta dice que quedó configurado.
 *
 * @param enlace      el {@code meetingUri} de la sala
 * @param acceso      el acceso que Google dice que tiene; {@code null} si no es ninguno de los dos que Renaser usa
 * @param artefactos  la grabación, la transcripción y las notas que Google dejó encendidas; {@code null} en lo que no se pidió al crear
 */
public record EspacioDeMeet(String enlace, AccesoSalaMeet acceso, ArtefactosDeLaSala artefactos) {
}
