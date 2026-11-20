package com.renaser.os.mentoring.domain.model.aviso;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Cuándo sale y cómo se deduplica el aviso de rojo del mismo día (spec 057, FR-031 y FR-032). Puro: el reloj y la zona llegan como argumentos.
 *
 * <p>Sale a las 19:00 de la zona del mentor: el barrido corre cada 30 minutos y solo actúa en la ventana de 19:00 a 19:29 locales, que contiene
 * exactamente una corrida (minutos 0 y 30 UTC) en cualquier zona, también las de media hora y 45 minutos.
 */
public final class ReglaDelAvisoDeRojo {

    static final int HORA_DEL_AVISO = 19;
    static final int MINUTOS_DE_LA_VENTANA = 30;

    private ReglaDelAvisoDeRojo() {
    }

    /** Si ahora es la hora del aviso para un mentor de esa zona: de las 19:00 a las 19:29 locales. */
    public static boolean toca(Instant ahora, ZoneId zonaDelMentor) {
        var local = ahora.atZone(zonaDelMentor);
        return local.getHour() == HORA_DEL_AVISO && local.getMinute() < MINUTOS_DE_LA_VENTANA;
    }

    /** El lunes de la semana del aprendiz, mirada en SU día local: la semana en que cuenta el aviso. */
    public static LocalDate lunesDe(LocalDate diaLocalDelAprendiz) {
        return diaLocalDelAprendiz.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Un aviso por mentor, aprendiz y semana: repetir la corrida, o la semana siguiente con el mismo lunes, no crea otro. */
    public static UUID clave(UUID mentorId, UUID aprendizId, LocalDate lunesDeLaSemana) {
        return UUID.nameUUIDFromBytes((mentorId + "|" + aprendizId + "|ROJO_DEL_DIA|" + lunesDeLaSemana).getBytes(StandardCharsets.UTF_8));
    }
}
