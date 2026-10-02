package com.renaser.os.chat.domain.model.conversacion;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Dónde sigue la lista de soportes (D-249): la actividad (último mensaje, o la creación si no tiene
 * ninguno) y el id del último soporte de la página anterior. La lista va de más reciente a más vieja y, a
 * igual actividad, por id descendente; la página siguiente empieza ESTRICTAMENTE después de este par, así
 * que no repite ni salta ninguno aunque dos tengan el mismo instante.
 *
 * <p>Viaja opaco: la app lo devuelve tal cual lo recibió.
 */
public record CursorDeSoportes(Instant actividad, ConversacionId id) {

    private static final String SEPARADOR = "|";

    public CursorDeSoportes {
        if (actividad == null || id == null) {
            throw new IllegalArgumentException("El cursor de soportes necesita la actividad y el id");
        }
    }

    public String escribir() {
        String plano = actividad + SEPARADOR + id.value();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plano.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException (400) si no es un cursor que haya dado el servidor */
    public static CursorDeSoportes leer(String escrito) {
        try {
            String plano = new String(Base64.getUrlDecoder().decode(escrito), StandardCharsets.UTF_8);
            int corte = plano.indexOf(SEPARADOR);
            return new CursorDeSoportes(Instant.parse(plano.substring(0, corte)),
                    ConversacionId.of(UUID.fromString(plano.substring(corte + 1))));
        } catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException roto) {
            throw new IllegalArgumentException("Cursor de soportes inválido");
        }
    }
}
