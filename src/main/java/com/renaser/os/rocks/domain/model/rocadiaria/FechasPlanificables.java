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
 * <p><b>El corte es el domingo de la semana de hoy</b> (lunes a domingo, D-203), salvo en la semana 13,
 * que termina el día 90: ahí el corte es el día 90 aunque caiga después del domingo (inicio de
 * miércoles a domingo) o antes (inicio en lunes). Pasado el día 90 no queda ninguna fecha.
 *
 * <p><b>Corregido 2026-09-27 (D-203).</b> D-192 (2026-09-26) decía: «la semana de programa pasó a ser un
 * bloque de siete días del programa (ver {@link SemanaPrograma}); el corte ya no es el domingo sino el
 * último día de ESA semana». La semana volvió a ser de lunes a domingo y el corte, al domingo.
 *
 * <p><b>Mañana siempre entra, si es un día del programa (E-340).</b> El domingo, mañana ya es otra semana:
 * con solo el corte, la ventana quedaba "del lunes al domingo" al revés y el plan del lunes no se podía
 * armar el domingo a la noche —la ventana nocturna existe para eso, y el tablero ya lo ofrecía—. Pasaba
 * desde E-208 (2026-09-22), que amplió la regla vieja ({@code mañana} con la ventana abierta, o hoy y
 * {@code mañana} sin ella) al resto de la semana y sin querer le sacó el lunes al domingo. El lunes
 * cuelga igual de SU objetivo semanal: sin el plan de la semana que empieza, {@code NO_WEEKLY_ROCK}.
 *
 * @param desde inclusivo
 * @param hasta inclusivo, el domingo de la semana de programa de hoy (el día 90 en la 13); el domingo,
 *              el lunes
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
        return new FechasPlanificables(desde, hastaCuando(hoy, semanas));
    }

    /** El fin de la semana de hoy; el domingo, el lunes, si todavía es un día del programa. */
    private static LocalDate hastaCuando(LocalDate hoy, SemanaPrograma semanas) {
        LocalDate finDeLaSemana = semanas.limites(semanas.numeroSemanaParaFecha(hoy)).fin();
        LocalDate manana = hoy.plusDays(1);
        boolean mananaEmpiezaOtraSemana = manana.isAfter(finDeLaSemana) && !manana.isAfter(semanas.finDelPrograma());
        return mananaEmpiezaOtraSemana ? manana : finDeLaSemana;
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }
}
