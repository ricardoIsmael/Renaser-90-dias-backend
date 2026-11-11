package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.PlanDeLaSemana;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * «Solo esta semana» (spec 028, D-296): el plan de UN habito en las fechas de una semana (lunes a domingo), con lo que la persona decidio solo
 * para esa semana encima del plan de siempre. Contraparte de {@link EditarHorarioSemanalUseCase}, que fija el plan de siempre por dia de la
 * semana. Hora, aviso y «Como suena» se guardan por fecha en la tabla que ya guarda «ese dia» ({@code horarios_habito_por_fecha}).
 *
 * <p>Reglas: solo fechas POSTERIORES a hoy (D-91: hoy y lo pasado van con candado); solo un habito que hoy le corresponde a la persona
 * (404 si no); no cobra cupo ni deja rastro en el historial de reacomodos; un habito obligatorio del programa no se puede apagar.
 */
public interface PlanDeLaSemanaUseCase {

    /** Las siete fechas de la semana, resueltas. */
    List<PlanDeLaSemana.DiaDeLaFecha> consultar(UserId actorId, HabitoId habitoId, SemanaCalendaria semana);

    /**
     * Guarda SOLO LO TOCADO de varias fechas de la semana, todo o nada.
     *
     * @throws IllegalArgumentException si no hay cambios, repite una fecha, una fecha cae fuera de la semana o no es posterior a hoy, o un
     *                                  valor no es valido (hora fuera del dia, aviso fuera de rango)
     * @throws IllegalStateException    si se apaga un habito obligatorio
     * @throws java.util.NoSuchElementException si el habito no existe o no le corresponde a la persona
     */
    void guardar(UserId actorId, HabitoId habitoId, SemanaCalendaria semana, List<CambioDeFecha> cambios);

    /** Lo mismo con el instante ya decidido (spec 048): lo hecho sin red corre sus reglas (ventana, cuota, semana libre) con la hora real. */
    void guardar(UserId actorId, HabitoId habitoId, SemanaCalendaria semana, List<CambioDeFecha> cambios,
                 java.time.Instant ahora);

    /** Vuelve al plan de siempre: borra las fechas propias de ese habito en la semana, solo las posteriores a hoy. Idempotente. */
    void volverAlPlanDeSiempre(UserId actorId, HabitoId habitoId, SemanaCalendaria semana);

    /** Lo mismo con el instante ya decidido (spec 048): lo hecho sin red corre sus reglas (ventana, cuota, semana libre) con la hora real. */
    void volverAlPlanDeSiempre(UserId actorId, HabitoId habitoId, SemanaCalendaria semana, java.time.Instant ahora);
}
