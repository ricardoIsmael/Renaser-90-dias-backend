package com.renaser.os.rocks.application.ports.out.medicion;

import com.renaser.os.shared.domain.UserId;

/**
 * Si el Mapa de Renacimiento de la persona ya esta activo (D-284). El puerto dice <b>que necesito</b>, no
 * de donde sale: hoy lo resuelve {@code onboarding.api.MapaActivoFinder}.
 *
 * <p>Del Mapa depende si el objetivo de 90 dias todavia se puede reemplazar: mientras no este activo
 * manda lo que escribe el Mapa; una vez activo, el objetivo queda fijo (D-234).
 */
public interface ConsultarEstadoDelMapaPort {

    /** {@code false} tambien si la persona nunca abrio el Mapa. */
    boolean activo(UserId participanteId);
}
