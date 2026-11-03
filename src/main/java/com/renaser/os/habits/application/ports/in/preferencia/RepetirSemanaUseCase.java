package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Repetir la semana (spec 028, D-296): si la persona planifico una semana «solo para esa semana», se le ofrece dejar la siguiente igual. Es de
 * los HABITOS (decision 028-3); las acciones de los objetivos no se repiten.
 *
 * <p>Sin tabla nueva: repetir es escribir, en las fechas de la semana destino, las mismas filas por fecha que tiene la modelo, lo que deja la
 * semana destino indistinguible de haberla planificado a mano. Todo o nada e idempotente. No toca pausas, registros, puntos, racha ni semaforo,
 * ni cobra cupo. Solo escribe fechas posteriores a hoy y solo habitos que hoy le corresponden a la persona.
 */
public interface RepetirSemanaUseCase {

    /**
     * La oferta de repetir la semana en curso en la siguiente, o vacio si no hay nada que ofrecer (sin Dia 1, la semana modelo sin cambios
     * propios, la siguiente ya igual, o la siguiente fuera del programa).
     */
    Optional<OfertaDeRepeticion> ofrecer(UserId actorId);

    /**
     * Aplica la repeticion (se vuelve a planear con lo que hay ahora).
     *
     * @throws IllegalArgumentException si las semanas son iguales, o la destino no tiene ninguna fecha posterior a hoy
     * @throws IllegalStateException    si reemplazaria cambios propios de la destino y {@code reemplazar} es {@code false}
     */
    ResultadoDeRepeticion repetir(UserId actorId, SemanaCalendaria modelo, SemanaCalendaria destino, boolean reemplazar);

    /** «Deshacer»: TODA la semana (todos los habitos) vuelve al plan de siempre, solo en fechas posteriores a hoy. Devuelve cuantas fechas. */
    int deshacerSemana(UserId actorId, SemanaCalendaria semana);

    /** Lo que quedaria en una fecha del destino; {@code propio == null}: la fecha vuelve al plan de siempre. */
    record DiaDeLaOferta(LocalDate fecha, DayOfWeek diaSemana, boolean activo, LocalTime horaDisparo, Boolean recordatorioActivo,
                         List<Integer> antelaciones, CombinacionDeAlarma alarma, boolean reemplaza, boolean vuelveAlPlanDeSiempre) {
    }

    record HabitoDeLaOferta(HabitoId habitoId, String titulo, List<DiaDeLaOferta> dias) {
    }

    record HabitoExcluido(HabitoId habitoId, String titulo, String motivo) {
        public static final String NO_DISPONIBLE = "NOT_AVAILABLE";
    }

    record OfertaDeRepeticion(SemanaCalendaria modelo, SemanaCalendaria destino, String huella, boolean reemplazaLoPropio,
                              List<HabitoDeLaOferta> habitos, List<HabitoExcluido> excluidos) {
    }

    record ResultadoDeRepeticion(SemanaCalendaria destino, int diasCopiados, int diasQueVuelvenAlPlan, int habitos,
                                 List<HabitoExcluido> excluidos) {
    }
}
