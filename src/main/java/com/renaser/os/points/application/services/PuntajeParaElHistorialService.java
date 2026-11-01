package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.out.puntaje.LoadPuntajePort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.PuntajeDelAprendizFinder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Los puntos de un aprendiz para la foto que deja al pasar de formación (spec 022). Solo lectura. */
@Service
class PuntajeParaElHistorialService implements PuntajeDelAprendizFinder {

    private final LoadPuntajePort cargar;

    PuntajeParaElHistorialService(LoadPuntajePort cargar) {
        this.cargar = cargar;
    }

    @Override
    public Optional<PuntajeDelAprendiz> de(UserId aprendiz) {
        return cargar.byParticipanteId(aprendiz)
                .map(p -> new PuntajeDelAprendiz(p.puntosLiga(), p.coherencia(), p.rachaMaxima()));
    }
}
