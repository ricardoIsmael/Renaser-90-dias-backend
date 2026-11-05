package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;

import java.time.LocalDate;

/**
 * El id del evento de Google sale del de Renaser (spec 040, FR-002; copiado de RenaserOs): el uuid sin guiones, que es base32hex
 * válido (0-9 y a-f están dentro de 0-9 y a-v). Crear dos veces lo mismo da 409 en vez de dos eventos, y de ahí sale el «sin duplicados»
 * (FR-007, SC-011).
 *
 * <p>Un evento que se repite no es una serie para Google: cada fecha es un evento propio con su sala (decisión 4 del dueño), y su id lleva
 * la fecha pegada: {@code <uuid sin guiones><AAAAMMDD>}.
 */
public final class IdDeEventoDeGoogle {

    private IdDeEventoDeGoogle() {
    }

    public static String de(EventoId eventoId, LocalDate fecha, boolean eventoQueSeRepite) {
        String base = eventoId.value().toString().replace("-", "");
        if (!eventoQueSeRepite) {
            return base;
        }
        return base + String.format("%04d%02d%02d", fecha.getYear(), fecha.getMonthValue(), fecha.getDayOfMonth());
    }
}
