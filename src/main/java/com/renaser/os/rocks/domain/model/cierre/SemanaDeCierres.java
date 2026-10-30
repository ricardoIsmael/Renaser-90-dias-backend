package com.renaser.os.rocks.domain.model.cierre;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Qué semana y qué días de ella se muestran en la ficha de cierres (spec 017, FR-006): lunes a domingo, desde el Día 1 de la
 * persona hasta hoy. Todas las fechas son de SU zona; quien llama resuelve «hoy».
 */
public final class SemanaDeCierres {

    private SemanaDeCierres() {
    }

    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    /**
     * El lunes que se lee. Sin pedido, la semana en curso. Una semana que no empieza en lunes, que viene, o que es anterior a la
     * primera de la persona es un pedido mal hecho (400): el servidor no inventa semanas.
     *
     * @param primeraSemana el lunes de la semana de su primer día; {@code null} si todavía no eligió su Día 1 (sin tope)
     */
    public static LocalDate resolver(LocalDate pedida, LocalDate hoy, LocalDate primeraSemana) {
        LocalDate esta = lunesDe(hoy);
        if (pedida == null) {
            return esta;
        }
        if (pedida.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("INVALID_DATE: la semana empieza en lunes");
        }
        if (pedida.isAfter(esta)) {
            throw new IllegalArgumentException("INVALID_DATE: esa semana todavía no llega");
        }
        if (primeraSemana != null && pedida.isBefore(primeraSemana)) {
            throw new IllegalArgumentException("INVALID_DATE: esa semana es anterior al programa de la persona");
        }
        return pedida;
    }

    /**
     * Los días a mostrar de esa semana, en orden: desde el Día 1 (o el lunes, lo que llegue después) hasta hoy (o el domingo, lo
     * que llegue antes). Nunca un día que viene; sin Día 1 elegido, ninguno.
     */
    public static List<LocalDate> dias(LocalDate lunes, LocalDate hoy, LocalDate primerDia) {
        List<LocalDate> dias = new ArrayList<>();
        if (primerDia == null) {
            return dias;
        }
        LocalDate desde = primerDia.isAfter(lunes) ? primerDia : lunes;
        LocalDate domingo = lunes.plusDays(6);
        LocalDate hasta = hoy.isBefore(domingo) ? hoy : domingo;
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            dias.add(dia);
        }
        return dias;
    }
}
