package com.renaser.os.rag.domain.model.agenda;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Los dias de la semana dichos en espanol, como los pasa el modelo: "lunes, miercoles",
 * "lunes-viernes", "fin de semana" o "todos". Sin tildes ni mayusculas que importen.
 */
public final class DiasDeSemana {

    private static final Map<String, DayOfWeek> NOMBRES = Map.of(
            "lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    private DiasDeSemana() {
    }

    public static Set<DayOfWeek> leer(String texto) {
        String normal = normalizar(texto);
        if (normal.isEmpty()) {
            throw new IllegalArgumentException("Faltan los dias: por ejemplo 'lunes-viernes' o 'sabado, domingo'.");
        }
        if (normal.equals("todos")) {
            return EnumSet.allOf(DayOfWeek.class);
        }
        if (normal.equals("fin de semana")) {
            return EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (String parte : normal.split(",")) {
            agregar(parte.strip(), dias);
        }
        return dias;
    }

    public static String nombre(DayOfWeek dia) {
        return NOMBRES.entrySet().stream().filter(nombre -> nombre.getValue() == dia)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private static void agregar(String parte, Set<DayOfWeek> dias) {
        String[] rango = parte.split("\\s*(-| a )\\s*");
        DayOfWeek desde = dia(rango[0]);
        DayOfWeek hasta = rango.length > 1 ? dia(rango[1]) : desde;
        for (DayOfWeek dia = desde; ; dia = dia.plus(1)) {
            dias.add(dia);
            if (dia == hasta) {
                return;
            }
        }
    }

    private static DayOfWeek dia(String nombre) {
        DayOfWeek dia = NOMBRES.get(nombre.strip());
        if (dia == null) {
            throw new IllegalArgumentException("No entendi el dia '" + nombre.strip() + "': usa lunes a domingo.");
        }
        return dia;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip().replace(";", ",");
    }
}
