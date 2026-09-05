package com.renaser.os.habits.application.ports.in.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * Autoservicio: catalogo SISTEMA activo + habitos PERSONAL activos del propio actor, sin
 * filtrar por dia — a diferencia de {@code ConsultarTracksDelDiaConCatalogoUseCase}, que solo
 * trae los tracks generados para hoy.
 */
public interface ConsultarMisHabitosUseCase {

    List<HabitoConDias> consultar(UserId actor);

    /**
     * El habito MAS los dias de la semana en que aplica, derivados del {@code TipoDia} de sus
     * horarios ({@code TipoDia.diasDeLaSemana}). Se devuelve junto al habito y no en una segunda
     * llamada porque el planificador semanal del movil los necesita para TODOS los habitos a la
     * vez: pedirlos por separado seria una consulta por habito (N+1).
     *
     * @param diasSemana union de los dias de todos sus horarios. Vacio nunca: un habito sin
     *                   horarios cae al conjunto completo (ver {@code MisHabitosService}).
     * @param diaDesbloqueo primer dia de programa en que el habito existe para el aprendiz — el
     *                      {@code dia_inicio} mas chico de sus horarios. 1 = disponible desde el
     *                      arranque.
     * @param diasParaDesbloqueo cuantos dias le faltan al aprendiz para llegar a {@code
     *                           diaDesbloqueo}. 0 = ya lo tiene disponible. Se calcula en el
     *                           servidor, que es donde vive el dia de programa; el cliente no lo
     *                           deduce (mismo criterio que {@code academy}, que ya expone
     *                           {@code diasFaltantes} asi).
     */
    record HabitoConDias(Habito habito, Set<DayOfWeek> diasSemana, int diaDesbloqueo,
                          int diasParaDesbloqueo) {

        /** Todavia no le toca: se muestra con candado, sin poder marcarlo ni pausarlo. */
        public boolean bloqueado() {
            return diasParaDesbloqueo > 0;
        }
    }
}
