package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Los puntos de un aprendiz, para la foto que queda al pasarlo de formación (spec 022, {@code participaciones_anteriores}). <b>Lo implementa
 * {@code points}</b> (dueño del puntaje) y lo consume {@code users}, que no importa a nadie.
 */
public interface PuntajeDelAprendizFinder {

    Optional<PuntajeDelAprendiz> de(UserId aprendiz);

    record PuntajeDelAprendiz(int puntosLiga, BigDecimal coherencia, int rachaMaxima) {
    }
}
