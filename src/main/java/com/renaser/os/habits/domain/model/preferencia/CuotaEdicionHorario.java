package com.renaser.os.habits.domain.model.preferencia;

import java.time.LocalDate;

/**
 * Cuota semanal de reacomodo de horario — regla pura (`limits.ts` del repo viejo).
 *
 * <p>Vive en el dominio, y no como constantes privadas de un servicio, porque la comparten
 * TRES caminos que tienen que decir exactamente lo mismo: el PATCH que la cobra, el GET que
 * la informa, y la promocion nocturna de un {@link CambioHorarioPendiente} — que tambien la
 * cobra, el dia en que el cambio programado pasa a regir de verdad.
 */
public record CuotaEdicionHorario(int usados, int restantes, int limite, boolean semanaDeAcomodoLibre) {

    /** limits.ts — la primera semana de programa es de acomodo: los cambios inmediatos no cuestan cupo. */
    public static final int DIAS_DE_ACOMODO_LIBRE = 7;

    /** limits.ts — habitos DISTINTOS reacomodables por semana de programa, pasada la semana libre. */
    public static final int HABITOS_POR_SEMANA = 3;

    /** Literal del contrato viejo (D-36): la semana de acomodo no cobra cupo. */
    public static final String PERIODO_LIBRE = "FREE";

    /** Literal del contrato viejo (D-36): fuera de la semana de acomodo, la cuota es semanal. */
    public static final String PERIODO_SEMANAL = "WEEK";

    public static boolean esSemanaDeAcomodoLibre(int diaPrograma) {
        return diaPrograma <= DIAS_DE_ACOMODO_LIBRE;
    }

    /** {@code programWeekStart(today, programDay)} del repo viejo: la semana de programa arranca offset dias atras. */
    public static LocalDate inicioSemanaPrograma(LocalDate hoy, int diaPrograma) {
        int offset = (Math.max(diaPrograma, 1) - 1) % 7;
        return hoy.minusDays(offset);
    }

    public static CuotaEdicionHorario de(int usados, boolean semanaDeAcomodoLibre) {
        return new CuotaEdicionHorario(usados, Math.max(0, HABITOS_POR_SEMANA - usados), HABITOS_POR_SEMANA,
                semanaDeAcomodoLibre);
    }

    public String periodo() {
        return semanaDeAcomodoLibre ? PERIODO_LIBRE : PERIODO_SEMANAL;
    }
}
