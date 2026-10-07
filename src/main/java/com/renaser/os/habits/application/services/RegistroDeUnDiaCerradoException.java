package com.renaser.os.habits.application.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * D-259 — se quiso registrar un habito de un dia que ya termino en la zona del participante.
 *
 * <p>La regla, confirmada por el dueño el 2026-10-06: «la programacion es por dia. Un habito se puede registrar durante
 * su dia aunque se le haya pasado la hora: vencer la hora solo afecta los puntos. Solo los del dia: un habito de un dia
 * que ya termino no se registra.»
 *
 * <p>Extiende {@link IllegalStateException} a proposito: {@code GlobalExceptionHandler} ya la responde como 409 con
 * este mensaje, que es el que la app muestra. El mensaje nombra el dia porque es lo que la persona necesita para
 * entender por que no entra («era del 5 de octubre»), no un estado interno.
 */
final class RegistroDeUnDiaCerradoException extends IllegalStateException {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es"));

    RegistroDeUnDiaCerradoException(LocalDate fechaDelRegistro) {
        super("Este hábito era del " + fechaDelRegistro.format(DIA) + "; ese día ya cerró y no se puede registrar.");
    }
}
