package com.renaser.os.onboarding.domain.model.respuesta;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Solo mayores de 18 (D-80, decisión del dueño del 2026-10-06).
 *
 * <p>La Política de Privacidad publicada dice que el programa es solo para mayores de edad, y hasta
 * este cambio la regla vivía únicamente en el formulario del móvil (que además dejaba elegir desde
 * los 14). Acá se impone al guardar la respuesta {@value #CLAVE_PREGUNTA} de la Ficha Inicial: una
 * fecha de alguien que todavía no cumplió 18 se rechaza (400) con {@link #SOLO_MAYORES_DE_18}.
 *
 * <p><b>«Hoy» es el día de Lima</b> (regla 02 §1), no el del servidor: a las 02:00 UTC del 7 de
 * octubre, en Lima todavía es 6, y quien cumple 18 el 7 todavía no entra. La app aplica el mismo
 * criterio en sus ruedas. Quien nació un 29 de febrero cumple el 1 de marzo en años no bisiestos
 * ({@link Period#between}).
 *
 * <p><b>Un texto que no es una fecha ISO se deja pasar, como antes.</b> Validar el formato de las
 * respuestas FECHA es otra regla, que nadie pidió; acá solo se mira la edad de una fecha real.
 */
public final class FechaDeNacimiento {

    public static final String CLAVE_PREGUNTA = "birth_date";
    public static final int EDAD_MINIMA = 18;
    public static final String SOLO_MAYORES_DE_18 = "Renaser es solo para mayores de 18 años";

    private static final ZoneId ZONA_DEL_PROGRAMA = ZoneId.of("America/Lima");

    private FechaDeNacimiento() {
    }

    /** Lanza {@link IllegalArgumentException} si {@code valorIso} es de alguien con menos de 18 años en el día de Lima. */
    public static void requireMayorDeEdad(String valorIso, Instant ahora) {
        LocalDate nacimiento = comoFecha(valorIso);
        if (nacimiento == null) {
            return;
        }
        LocalDate hoyEnLima = ahora.atZone(ZONA_DEL_PROGRAMA).toLocalDate();
        if (Period.between(nacimiento, hoyEnLima).getYears() < EDAD_MINIMA) {
            throw new IllegalArgumentException(SOLO_MAYORES_DE_18);
        }
    }

    private static LocalDate comoFecha(String valorIso) {
        if (valorIso == null) {
            return null;
        }
        try {
            return LocalDate.parse(valorIso.trim());
        } catch (DateTimeParseException noEsUnaFecha) {
            return null;
        }
    }
}
