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
 * cumplido se puede completar igual y paga menos o 0 (D-259). Esta herramienta decia {@code ya_vencio=si} y "ya
 * vencieron hoy: no los cuentes como pendientes", y el acompanante contesto "el agua tibia y el ritual de la manana
 * ya vencieron" de dos habitos que seguian PENDIENTE. Ahora dice que ya no dan puntos y que igual puede hacerlos,
 * y los cuenta entre los que le faltan.
 *
 * <p><b>Spec 038 (D-259): un registro EXPIRADO es un DIA CERRADO</b> y NO se puede registrar: el barrido lo cerro porque su dia
 * termino ({@code habits} lo rechaza con 409). Antes esta lista lo contaba entre los que «igual puede hacer»; ahora sale como
 * {@code dia_cerrado}, no cuenta entre los que le faltan y no lleva marca de evidencia (no se le deja boton).
 *
 * <p><b>Lo que falta, contado y en orden.</b> Primero el total y el reparto por dimension (Cuerpo,
 * Mente, Emociones, Espiritu), despues la lista: los que todavia dan puntos antes, cada grupo por la
 * hora que la persona tiene registrada. Asi el modelo nombra los dos o tres primeros sin tener que
 * ordenar ni sumar nada (regla del prompt: las cuentas salen de las herramientas).
 */
public final class LoQueLeFaltaHoy {

    /** Los estados que todavia se pueden completar (D-259: un EXPIRADO es un dia cerrado y ya no). */
    private static final Set<String> SE_PUEDEN_HACER = Set.of("PENDIENTE", "EN_CURSO");
    private static final String DIA_CERRADO = "EXPIRADO";
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
        long diaCerrado = habitos.stream().filter(LoQueLeFaltaHoy::esDiaCerrado).count();
        StringBuilder texto = new StringBuilder(resumen(habitos.size(), faltan, conPuntos, diaCerrado)).append('\n');
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

    /** D-259: su dia ya cerro. Ya no se registra, ni hoy ni despues. */
    private static boolean esDiaCerrado(HabitoDelDia habito) {
        return DIA_CERRADO.equals(habito.estado());
    }

    private static boolean daPuntos(HabitoDelDia habito, Instant ahora) {
        return habito.sigueEnJuego() && habito.puntosEnJuego() > 0 && !yaNoDaPuntos(habito, ahora);
    }

    /** Primero los que todavia dan puntos; dentro de cada grupo, por la hora registrada (sin hora, al final). */
    private static Comparator<HabitoDelDia> prioridad(Instant ahora) {
        return Comparator.comparing((HabitoDelDia habito) -> !daPuntos(habito, ahora))
                .thenComparing(HabitoDelDia::horaInicio, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()));
    }

    private static String resumen(int total, List<HabitoDelDia> faltan, long conPuntos, long diaCerrado) {
        if (faltan.isEmpty()) {
            return diaCerrado == 0 ? "Hoy ya hizo todos sus habitos (" + total + ")."
                    : "Hoy no le queda ninguno por registrar (" + total + " en total): " + diaCerrado + " son de un dia que ya cerro "
                            + "y no se pueden registrar, y el resto ya esta hecho.";
        }
        long sinPuntos = faltan.size() - conPuntos;
        return "Le faltan " + faltan.size() + " de " + total + " habitos de hoy. Por dimension: " + porDimension(faltan)
                + ". " + conPuntos + " todavia dan puntos" + (sinPuntos == 0 ? "." : " y " + sinPuntos
                + " ya no dan puntos porque paso su hora, pero igual puede hacerlos.")
                + (diaCerrado == 0 ? "" : " Ademas " + diaCerrado + " son de un dia que ya cerro: no se pueden registrar ni cuentan entre los que le faltan.")
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
        if (esDiaCerrado(habito)) {
            return linea.append(" | dia_cerrado (ese dia ya termino: no se puede registrar, no le dejes boton)").toString();
        }
        if (habito.mideKilometros()) {
            linea.append(medicion(habito));
        }
        if (habito.seMideSolo()) {
            return linea.append(" | registro=automatico (conecto sus pasos: los km se miden solos. No pidas foto ni captura ni uses "
                    + "proponer_registrar_con_foto; que toque «Registrar» en la fila de Kilometros diarios en Entrenar cuando llegue a su meta)")
                    .toString();
        }
        if (habito.exigeEvidencia()) {
            linea.append(" | exige_evidencia=si");
            if (habito.seRegistraEscribiendo()) {
                linea.append(" | registro=escribir (ritual: sin foto, escribe como se siente)");
            }
        }
        return linea.toString();
    }

    /**
     * Spec 037 (T021 de la 038): {@code mide=km | meta=5 | lleva=3.2}. {@code lleva} es lo ya REGISTRADO hoy (0 si todavia nada): lo que la persona va
     * caminando en vivo lo lee su telefono, no el servidor. Con el dia registrado, de donde salio: {@code origen=pasos}, {@code origen=carrera} (las
     * carreras con GPS, spec 042) o {@code origen=captura}.
     */
    private static String medicion(HabitoDelDia habito) {
        var medicion = habito.medicion();
        StringBuilder texto = new StringBuilder(" | mide=km");
        if (medicion.meta() != null) {
            texto.append(" | meta=").append(numero(medicion.meta()));
        }
        texto.append(" | lleva=").append(medicion.valorDelDia() == null ? "0" : numero(medicion.valorDelDia()));
        if (medicion.origenDelDia() != null) {
            texto.append(" | origen=").append(origenEnPalabras(medicion.origenDelDia()));
        }
        return texto.toString();
    }

    private static String origenEnPalabras(String origen) {
        return switch (origen) {
            case "HEALTH_CONNECT" -> "pasos";
            case "GPS" -> "carrera";
            default -> "captura";
        };
    }

    /** «5» y «5.4», no «5.00» ni «5.40». */
    private static String numero(java.math.BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    /** "45 min" o "2 h 10 min": relativo, asi no depende de ninguna zona horaria. */
    private static String faltan(Instant ahora, Instant plazo) {
        long minutos = Duration.between(ahora, plazo).toMinutes();
        return minutos < 60 ? minutos + " min" : (minutos / 60) + " h " + (minutos % 60) + " min";
    }
}
