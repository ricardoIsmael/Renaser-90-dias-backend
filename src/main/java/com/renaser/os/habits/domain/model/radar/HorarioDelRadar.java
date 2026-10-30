package com.renaser.os.habits.domain.model.radar;

import java.time.ZonedDateTime;
import java.util.OptionalInt;

/**
 * Cuándo se puede registrar el Código Renaser y a qué hora estaba prevista la respuesta (spec 012). Funciones puras:
 * reciben el día del programa, Mi Radar y el momento LOCAL de la persona —su zona, no la del servidor: regla 02; de ahí
 * sale la hora y también el día de la semana, que con el reloj en la madrugada UTC puede ser el anterior— y no leen
 * ningún reloj.
 *
 * <p>Los días 0 a 7 son los de siempre, sin Mi Radar de por medio: se puede registrar a cualquier hora y la hora
 * prevista es la hora local si cae entre las 08 y las 19, las doce franjas de la primera semana. Desde el día 8 el
 * Radar es opcional y por día de la semana ({@link MiRadar#horasDelDia}): solo se puede registrar con Mi Radar
 * encendido, en un día que tenga Radar y de 03:00 a 23:59; la hora prevista es la hora local si la persona la eligió
 * para ese día. Un día «Sin Radar ese día» se comporta como Mi Radar apagado. El día 90 no cambia nada: después de él
 * sigue como la persona lo dejó.
 */
public final class HorarioDelRadar {

    /** Primer día del programa en que el Código Renaser pasa a ser Mi Radar. */
    public static final int PRIMER_DIA_DE_MI_RADAR = 8;

    private static final int PRIMERA_HORA_DE_LA_SEMANA_1 = 8;
    private static final int ULTIMA_HORA_DE_LA_SEMANA_1 = 19;

    private HorarioDelRadar() {
    }

    /** Si Mi Radar existe para quien va por este dia: antes del 8 el Codigo Renaser es el de la primera semana. */
    public static boolean aplicaMiRadar(int diaPrograma) {
        return diaPrograma >= PRIMER_DIA_DE_MI_RADAR;
    }

    /** {@code local} es el momento en la zona de la persona. */
    public static boolean puedeRegistrar(int diaPrograma, MiRadar miRadar, ZonedDateTime local) {
        if (!aplicaMiRadar(diaPrograma)) {
            return true;
        }
        int hora = local.getHour();
        return !miRadar.horasDelDia(local.getDayOfWeek()).isEmpty()
                && hora >= MiRadar.HORA_PRIMERA && hora <= MiRadar.HORA_ULTIMA;
    }

    /** Vacío si el registro es libre: no responde a ninguna hora prevista. */
    public static OptionalInt horaPrevista(int diaPrograma, MiRadar miRadar, ZonedDateTime local) {
        int hora = local.getHour();
        boolean esPrevista = aplicaMiRadar(diaPrograma)
                ? miRadar.horasDelDia(local.getDayOfWeek()).contains(hora)
                : hora >= PRIMERA_HORA_DE_LA_SEMANA_1 && hora <= ULTIMA_HORA_DE_LA_SEMANA_1;
        return esPrevista ? OptionalInt.of(hora) : OptionalInt.empty();
    }
}
