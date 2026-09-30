package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El texto de {@code consultar_habitos_del_dia} (E-455, 2026-09-30).
 *
 * <p><b>Un habito no vence; vence la ventana de sus puntos.</b> Un registro PENDIENTE con el plazo
 * cumplido, o ya EXPIRADO, se puede completar igual y paga 0 ({@code EstadoRegistro.completable}
 * en {@code habits}). Esta herramienta decia {@code ya_vencio=si} y "ya vencieron hoy: no los cuentes
 * como pendientes", y el acompanante contesto "el agua tibia y el ritual de la manana ya vencieron"
 * de dos habitos que seguian PENDIENTE. Ahora dice que ya no dan puntos y que igual puede hacerlos,
 * y los cuenta entre los que le faltan.
 *
 * <p><b>Lo que falta, contado y en orden.</b> Primero el total y el reparto por dimension (Cuerpo,
 * Mente, Emociones, Espiritu), despues la lista: los que todavia dan puntos antes, cada grupo por la
 * hora que la persona tiene registrada. Asi el modelo nombra los dos o tres primeros sin tener que
 * ordenar ni sumar nada (regla del prompt: las cuentas salen de las herramientas).
 */
public final class LoQueLeFaltaHoy {

    /** Los estados que todavia se pueden completar: el mismo {@code completable} de {@code habits}. */
    private static final Set<String> SE_PUEDEN_HACER = Set.of("PENDIENTE", "EN_CURSO", "EXPIRADO");
    private static final String SIN_DIMENSION = "Sin dimension";

    private LoQueLeFaltaHoy() {
    }

    public static String texto(List<HabitoDelDia> habitos, Instant ahora) {
        List<HabitoDelDia> faltan = habitos.stream().filter(LoQueLeFaltaHoy::faltaHacer)
                .sorted(prioridad(ahora)).toList();
        List<HabitoDelDia> resto = habitos.stream().filter(habito -> !faltaHacer(habito)).toList();
        long conPuntos = faltan.stream().filter(habito -> daPuntos(habito, ahora)).count();
        int totalEnJuego = faltan.stream().filter(habito -> daPuntos(habito, ahora))
                .mapToInt(HabitoDelDia::puntosEnJuego).sum();
        StringBuilder texto = new StringBuilder(resumen(habitos.size(), faltan, conPuntos)).append('\n');
        faltan.forEach(habito -> texto.append(linea(habito, ahora)).append('\n'));
        resto.forEach(habito -> texto.append(linea(habito, ahora)).append('\n'));
        return texto.append("Total en juego: ").append(totalEnJuego).append(" puntos en ").append(conPuntos)
                .append(" habito(s) que todavia dan puntos.").toString();
    }

    /** Ya no da puntos: su plazo paso, o ya no tiene nada en juego. Se puede hacer igual. */
    public static boolean yaNoDaPuntos(HabitoDelDia habito, Instant ahora) {
        return habito.plazo() != null && !habito.plazo().isAfter(ahora);
    }

    private static boolean faltaHacer(HabitoDelDia habito) {
        return SE_PUEDEN_HACER.contains(habito.estado());
    }

    private static boolean daPuntos(HabitoDelDia habito, Instant ahora) {
        return habito.sigueEnJuego() && habito.puntosEnJuego() > 0 && !yaNoDaPuntos(habito, ahora);
    }

    /** Primero los que todavia dan puntos; dentro de cada grupo, por la hora registrada (sin hora, al final). */
    private static Comparator<HabitoDelDia> prioridad(Instant ahora) {
        return Comparator.comparing((HabitoDelDia habito) -> !daPuntos(habito, ahora))
                .thenComparing(HabitoDelDia::horaInicio, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()));
    }

    private static String resumen(int total, List<HabitoDelDia> faltan, long conPuntos) {
        if (faltan.isEmpty()) {
            return "Hoy ya hizo todos sus habitos (" + total + ").";
        }
        long sinPuntos = faltan.size() - conPuntos;
        return "Le faltan " + faltan.size() + " de " + total + " habitos de hoy. Por dimension: " + porDimension(faltan)
                + ". " + conPuntos + " todavia dan puntos" + (sinPuntos == 0 ? "." : " y " + sinPuntos
                + " ya no dan puntos porque paso su hora, pero igual puede hacerlos.")
                + "\nPara decirle que le falta: el total, el reparto por dimension y solo los 2 o 3 primeros de "
                + "esta lista, que ya viene en orden (primero los que dan puntos, por su hora). No listes todos "
                + "salvo que lo pida. Un habito no vence: si ya no da puntos, di que ya paso la hora para sumar "
                + "sus puntos y que igual puede hacerlo.";
    }

    /** "Cuerpo 13, Mente 8": de mas a menos, en el orden en que aparecen si empatan. */
    private static String porDimension(List<HabitoDelDia> faltan) {
        Map<String, Long> cuenta = faltan.stream().collect(Collectors.groupingBy(
                habito -> habito.dimension() == null ? SIN_DIMENSION : habito.dimension(), LinkedHashMap::new,
                Collectors.counting()));
        return cuenta.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entrada -> entrada.getKey() + " " + entrada.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Una linea por habito: el modelo la parafrasea, asi que dice lo que hace falta y nada mas.
     *
     * <p>{@code exige_evidencia} solo cuando es cierto (2026-09-14): el agente no puede subir la foto,
     * la tarjeta de la camara la deja {@code proponer_registrar_con_foto} (D-171). Un habito renombrado
     * lleva tambien el titulo del programa (E-290).
     */
    private static String linea(HabitoDelDia habito, Instant ahora) {
        StringBuilder linea = new StringBuilder()
                .append("id=").append(habito.registroId())
                .append(habito.horaInicio() == null ? "" : " | hora=" + habito.horaInicio())
                .append(" | ").append(habito.titulo())
                .append(habito.tituloDelPrograma() == null ? "" : " (" + habito.tituloDelPrograma() + " del programa)")
                .append(habito.dimension() == null ? "" : " | " + habito.dimension())
                .append(" | estado=").append(habito.estado());
        if (faltaHacer(habito) && !daPuntos(habito, ahora)) {
            linea.append(" | ya_no_da_puntos (paso su hora; igual puede hacerlo)");
        } else if (habito.sigueEnJuego()) {
            linea.append(" | puntos_en_juego=").append(habito.puntosEnJuego()).append(" de ")
                    .append(habito.puntosMaximos());
            if (habito.plazo() != null) {
                linea.append(" | sus_puntos_terminan_en=").append(faltan(ahora, habito.plazo()));
            }
        }
        if (habito.exigeEvidencia()) {
            linea.append(" | exige_evidencia=si");
        }
        return linea.toString();
    }

    /** "45 min" o "2 h 10 min": relativo, asi no depende de ninguna zona horaria. */
    private static String faltan(Instant ahora, Instant plazo) {
        long minutos = Duration.between(ahora, plazo).toMinutes();
        return minutos < 60 ? minutos + " min" : (minutos / 60) + " h " + (minutos % 60) + " min";
    }
}
