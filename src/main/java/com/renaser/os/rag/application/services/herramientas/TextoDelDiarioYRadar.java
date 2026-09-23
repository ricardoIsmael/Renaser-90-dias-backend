package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

/**
 * Lo comun a las cuatro herramientas de la Bitacora Nocturna y el Codigo Renaser (2026-09-23):
 * mostrar un texto personal recortado, escribir una hora local como la lee una persona y traducir
 * el rechazo de {@code habits} a un {@code Fallo} legible.
 *
 * <p><b>El contenido es personal:</b> al log va solo el TIPO de la excepcion, nunca su mensaje ni
 * lo que la persona escribio.
 */
final class TextoDelDiarioYRadar {

    private static final Logger log = LoggerFactory.getLogger(TextoDelDiarioYRadar.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PUNTOS_SUSPENSIVOS = "…";

    private TextoDelDiarioYRadar() {
    }

    /** Recorta solo para MOSTRAR; lo que se guarda es siempre el texto completo. */
    static String recortado(String texto, int maximo) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.length() <= maximo) {
            return limpio;
        }
        // No partir un emoji (par sustituto) al medio: quedaria un caracter invalido a la vista.
        int corte = Character.isHighSurrogate(limpio.charAt(maximo - 1)) ? maximo - 1 : maximo;
        return limpio.substring(0, corte).stripTrailing() + PUNTOS_SUSPENSIVOS;
    }

    /** "jueves 24/09 a las 21:14", en la hora local que ya resolvio {@code habits}. */
    static String momento(LocalDateTime horaLocal) {
        return FechaDelPlan.legible(horaLocal.toLocalDate()) + " a las " + horaLocal.format(HORA);
    }

    static String hora(LocalDateTime horaLocal) {
        return horaLocal.format(HORA);
    }

    /**
     * @param siNoAutorizado el motivo cuando {@code habits} niega el acceso (suspendida, o en el
     *                       radar, sin el programa andando)
     */
    static ResultadoHerramienta rechazo(String herramienta, RuntimeException rechazo, String siNoAutorizado) {
        log.info("[rag] {} rechazada por habits: {}", herramienta, rechazo.getClass().getSimpleName());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NotAuthorizedException noAutorizado -> siNoAutorizado;
            case NoSuchElementException sinPrograma -> "No encontre un programa activo para esta cuenta.";
            default -> "No pude hacerlo en este momento. Puede intentarlo desde la app.";
        });
    }
}
