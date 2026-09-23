package com.renaser.os.rag.application.services.herramientas;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los argumentos de las herramientas de horario (fase 4, 2026-09-23), leidos y escritos en UN solo
 * lugar: la herramienta que propone y el {@code AccionConfirmable} que ejecuta tienen que entender
 * exactamente lo mismo de la invocacion guardada.
 *
 * <p>Todo metodo de lectura lanza {@link PropuestaImposibleException} con un motivo apto para el
 * modelo cuando el texto no se entiende: un modelo manda "7am" o el titulo del habito con total
 * naturalidad, y lo que corresponde es pedirle el formato, no adivinar.
 */
final class ArgumentosDeHorario {

    static final String HABITO_ID = "habito_id";
    static final String HORA_INICIO = "hora_inicio";
    static final String HORA_LIMITE = "hora_limite";
    static final String FECHA = "fecha";
    static final String DIA_SEMANA = "dia_semana";
    static final String ACCION = "accion";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HORA_FLEXIBLE = DateTimeFormatter.ofPattern("H:mm");
    private static final Locale CASTELLANO = Locale.forLanguageTag("es");

    /** "lunes".."domingo", sin tildes, y tambien los nombres de {@code DayOfWeek} por si el modelo los usa. */
    private static final Map<String, DayOfWeek> DIAS = Map.of("lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY,
            "miercoles", DayOfWeek.WEDNESDAY, "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY,
            "sabado", DayOfWeek.SATURDAY, "domingo", DayOfWeek.SUNDAY);

    private ArgumentosDeHorario() {
    }

    static UUID habitoId(String texto) {
        try {
            return UUID.fromString(requerido(texto).trim());
        } catch (IllegalArgumentException noEsUnIdentificador) {
            throw new PropuestaImposibleException("Ese habito_id no es valido. Consulta primero consultar_horarios "
                    + "y usa el habito_id que devuelve.");
        }
    }

    /** Acepta "7:00" y "07:00"; nunca "7am". */
    static LocalTime hora(String texto, String argumento) {
        try {
            return LocalTime.parse(requerido(texto).trim(), HORA_FLEXIBLE);
        } catch (DateTimeParseException formatoInvalido) {
            throw new PropuestaImposibleException("El argumento " + argumento + " tiene que venir como HH:mm en "
                    + "formato de 24 horas (por ejemplo 07:30 o 19:00).");
        }
    }

    /** {@code false} si no vino: es el caso "sin hora limite" o "sin fecha", no un error. */
    static boolean presente(String texto) {
        return texto != null && !texto.isBlank();
    }

    static LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(requerido(texto).trim());
        } catch (DateTimeParseException formatoInvalido) {
            throw new PropuestaImposibleException("La fecha tiene que venir como yyyy-MM-dd (por ejemplo 2026-09-24).");
        }
    }

    static DayOfWeek diaSemana(String texto) {
        String normalizado = Normalizer.normalize(requerido(texto).trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        DayOfWeek dia = DIAS.get(normalizado);
        if (dia != null) {
            return dia;
        }
        try {
            return DayOfWeek.valueOf(normalizado.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException diaDesconocido) {
            throw new PropuestaImposibleException("El dia_semana tiene que ser uno de: lunes, martes, miercoles, "
                    + "jueves, viernes, sabado o domingo.");
        }
    }

    /** "jueves 2026-09-24": la fecha sola no le dice nada a la persona, el dia de la semana si. */
    static String texto(LocalDate fecha) {
        return nombre(fecha.getDayOfWeek()) + " " + fecha;
    }

    static String nombre(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, CASTELLANO);
    }

    /** Siempre HH:mm: es lo que se guarda en la invocacion normalizada y lo que se muestra. */
    static String texto(LocalTime hora) {
        return hora.format(HORA);
    }

    private static String requerido(String texto) {
        if (!presente(texto)) {
            throw new PropuestaImposibleException("Falta un argumento obligatorio de la herramienta.");
        }
        return texto;
    }
}
