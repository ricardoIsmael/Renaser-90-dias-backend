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
     * Lo mismo que {@link #consultar}, y ademas la racha de cada habito hasta hoy ({@code rachaDias}, D-254).
     * Es lo que lee la app ({@code GET /api/v1/habits}): decision 2 del dueño (2026-10-05), «mostrar la racha
     * congelada de un habito que hoy no tiene track (no le toca hoy o esta en pausa)». Metodo aparte para que
     * los llamadores de adentro que no la usan ({@code PlanDeHabitosService}) no paguen su lectura.
     */
    List<HabitoConDias> consultarConRachas(UserId actor);

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
     *                           {@code diasFaltantes} asi). D-200: tambien 0 si el habito ya
     *                           corrio desde ese dia y un retroceso dejo a la persona por debajo:
     *                           se sigue generando, asi que no lleva candado.
     * @param rachaDias la racha del habito hasta hoy, con la misma regla que la del track
     *                  ({@code RachaDelHabito}); {@code null} cuando no se calculo ({@link #consultar}).
     */
    record HabitoConDias(Habito habito, Set<DayOfWeek> diasSemana, int diaDesbloqueo,
                          int diasParaDesbloqueo, Integer rachaDias) {

        /** Sin racha calculada: la forma anterior a D-254. */
        public HabitoConDias(Habito habito, Set<DayOfWeek> diasSemana, int diaDesbloqueo, int diasParaDesbloqueo) {
            this(habito, diasSemana, diaDesbloqueo, diasParaDesbloqueo, null);
        }

        /** La misma vista con su racha (D-254). */
        public HabitoConDias conRacha(Integer dias) {
            return new HabitoConDias(habito, diasSemana, diaDesbloqueo, diasParaDesbloqueo, dias);
        }

        /** Todavia no le toca: se muestra con candado, sin poder marcarlo ni pausarlo. */
        public boolean bloqueado() {
            return diasParaDesbloqueo > 0;
        }
    }
}
