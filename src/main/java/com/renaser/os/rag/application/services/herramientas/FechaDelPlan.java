package com.renaser.os.rag.application.services.herramientas;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Las fechas de las herramientas del plan de habitos ({@code proponer_pausar_habito},
 * {@code proponer_dia_de_habito_semanal}): leer la que manda el modelo y escribirla como la lee
 * una persona ("jueves 24/09"). Una sola forma para las dos herramientas y sus confirmaciones.
 *
 * <p>No calcula "hoy" ni ningun rango: eso lo resuelve {@code habits} en la zona del participante
 * (regla 02). Aca solo se traduce texto.
 */
final class FechaDelPlan {

    private static final Locale CASTELLANO = Locale.forLanguageTag("es");
    private static final DateTimeFormatter DIA_Y_MES = DateTimeFormatter.ofPattern("dd/MM");

    private FechaDelPlan() {
    }

    static boolean ausente(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Vacio si no viene como {@code yyyy-MM-dd} (el modelo escribio "el jueves", por ejemplo). */
    static Optional<LocalDate> leer(String texto) {
        if (ausente(texto)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.trim()));
        } catch (DateTimeParseException formatoInvalido) {
            return Optional.empty();
        }
    }

    /** "jueves 24/09". */
    static String legible(LocalDate fecha) {
        return fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, CASTELLANO) + " " + fecha.format(DIA_Y_MES);
    }
}
