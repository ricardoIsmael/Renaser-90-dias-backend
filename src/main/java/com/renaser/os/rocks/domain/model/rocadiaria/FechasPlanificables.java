package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Que fechas se pueden planificar hoy: <b>de manana hasta el final de la semana de programa</b>, y
 * ademas hoy mismo mientras la ventana nocturna no haya abierto.
 *
 * <p><b>Movida aca el 2026-09-26 (D-177)</b> desde {@code RocaDiariaService.requireFechaPlanificable},
 * sin cambiar la regla: la necesita tambien el caso de uso que agrega UNA accion a un dia que viene
 * ({@code AgregarRocaDiariaUseCase}), y copiarla habria dejado dos lugares para la misma regla —
 * exactamente lo que paso con el minimo de rocas semanales (E-205).
 *
 * <p><b>El corte es el fin de la semana de programa, y no es cosmetico:</b> cada objetivo diario
 * cuelga del objetivo semanal de su semana ({@code NO_WEEKLY_ROCK} si no existe), asi que ofrecer el
 * lunes que viene seria ofrecer algo que va a fallar al guardar.
 *
 * <p><b>2026-09-26 (D-192):</b> la semana de programa pasó a ser un bloque de siete días del programa
 * (ver {@link SemanaPrograma}); el corte ya no es el domingo sino el último día de ESA semana.
 *
 * @param desde inclusivo
 * @param hasta inclusivo, el último día de la semana de programa de hoy
 */
public record FechasPlanificables(LocalDate desde, LocalDate hasta) {

    public FechasPlanificables {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
    }

    /**
     * @param plazo {@link EstadoPlazo#EN_PLAZO} si la ventana nocturna (18:00) ya abrio: desde ahi el
     *              programa esta planificando el dia siguiente y volver sobre hoy es reacomodar el dia
     *              en curso
     */
    public static FechasPlanificables para(LocalDate hoy, EstadoPlazo plazo, SemanaPrograma semanas) {
        LocalDate desde = plazo == EstadoPlazo.EN_PLAZO ? hoy.plusDays(1) : hoy;
        LocalDate hasta = semanas.limites(semanas.numeroSemanaParaFecha(hoy)).fin();
        return new FechasPlanificables(desde, hasta);
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }
}
