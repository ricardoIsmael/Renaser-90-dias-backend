package com.renaser.os.habits.application.ports.in.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Autoservicio: catalogo SISTEMA activo + habitos PERSONAL activos del propio actor, sin
 * filtrar por dia — a diferencia de {@code ConsultarTracksDelDiaConCatalogoUseCase}, que solo
 * trae los tracks generados para hoy.
 */
public interface ConsultarMisHabitosUseCase {

    List<Habito> consultar(UserId actor);
}
