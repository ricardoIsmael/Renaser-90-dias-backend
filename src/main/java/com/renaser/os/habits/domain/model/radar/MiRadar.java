package com.renaser.os.habits.domain.model.radar;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Mi Radar (spec 012): desde el día 8, a qué horas le pregunta el Código Renaser a la persona, y por día de la semana,
 * igual que los hábitos en Planificar. Valor inmutable.
 *
 * <ul>
 *   <li>{@code horas}: las de TODOS los días. En punto, de 03:00 a 23:00 del mismo día (nada después de la
 *       medianoche), hasta 12, sin repetir. Sin horas es apagado; encendido siempre tiene al menos una.</li>
 *   <li>{@code dias}: los días de la semana (lunes = 1 … domingo = 7, cada uno una vez) que tienen horas PROPIAS, con
 *       las mismas reglas. Un día con {@code horas = []} es «Sin Radar ese día». Un día que no está sigue las de todos
 *       los días. Apagado no tiene días.</li>
 * </ul>
 *
 * <p>Las reglas viven acá y no solo en la app (FR-005a, SC-008): la app valida antes de guardar y el servidor vuelve a
 * validar. Lo que no cumple lanza {@link IllegalArgumentException}, que el servidor responde con 400. Que no se repitan
 * las horas ni los días lo impone esta clase y no la base: un CHECK de Postgres no puede mirar repetidos (V119).
 */
public record MiRadar(List<Integer> horas, List<DiaPropio> dias) {

    public static final int HORA_PRIMERA = 3;
    public static final int HORA_ULTIMA = 23;
    public static final int MAXIMO_DE_HORAS = 12;

    public MiRadar {
        horas = ordenadasYValidas(horas);
        dias = diasOrdenadosYValidos(dias);
        if (horas.isEmpty() && !dias.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una hora");
        }
    }

    public static MiRadar apagado() {
        return new MiRadar(List.of(), List.of());
    }

    /** Encendido con las mismas horas todos los días. */
    public static MiRadar encendido(List<Integer> horas) {
        return encendido(horas, List.of());
    }

    /** @throws IllegalArgumentException si no hay ninguna hora para todos los días o algo no cumple las reglas */
    public static MiRadar encendido(List<Integer> horas, List<DiaPropio> dias) {
        if (horas == null || horas.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una hora");
        }
        return new MiRadar(horas, dias);
    }

    public boolean estaEncendido() {
        return !horas.isEmpty();
    }

    /**
     * Las horas a las que pregunta ese día de la semana: las propias del día si las tiene, y si no las de todos los
     * días. Vacía si Mi Radar está apagado o si el día es «Sin Radar ese día».
     */
    public List<Integer> horasDelDia(DayOfWeek diaDeLaSemana) {
        return dias.stream().filter(propio -> propio.dia() == diaDeLaSemana.getValue()).findFirst()
                .map(DiaPropio::horas).orElse(horas);
    }

    /**
     * Un día de la semana con horas propias (spec 012, cuarta ronda). {@code dia} sigue el orden de ISO: lunes = 1 …
     * domingo = 7, el mismo de {@link DayOfWeek#getValue()}. Con {@code horas} vacías es «Sin Radar ese día».
     */
    public record DiaPropio(int dia, List<Integer> horas) {

        public DiaPropio {
            if (dia < 1 || dia > 7) {
                throw new IllegalArgumentException("El día de Mi Radar va de 1 (lunes) a 7 (domingo)");
            }
            horas = ordenadasYValidas(horas);
        }
    }

    private static List<DiaPropio> diasOrdenadosYValidos(List<DiaPropio> dias) {
        if (dias == null) {
            throw new IllegalArgumentException("Los días de Mi Radar son obligatorios");
        }
        if (dias.stream().anyMatch(propio -> propio == null)) {
            throw new IllegalArgumentException("Cada día de Mi Radar necesita su número y sus horas");
        }
        if (dias.stream().map(DiaPropio::dia).distinct().count() != dias.size()) {
            throw new IllegalArgumentException("No repitas el mismo día");
        }
        return dias.stream().sorted(Comparator.comparingInt(DiaPropio::dia)).toList();
    }

    private static List<Integer> ordenadasYValidas(List<Integer> horas) {
        if (horas == null) {
            throw new IllegalArgumentException("Las horas de Mi Radar son obligatorias");
        }
        if (horas.size() > MAXIMO_DE_HORAS) {
            throw new IllegalArgumentException("Puedes elegir hasta " + MAXIMO_DE_HORAS + " horas");
        }
        if (horas.stream().anyMatch(hora -> hora == null || hora < HORA_PRIMERA || hora > HORA_ULTIMA)) {
            throw new IllegalArgumentException("Las horas de Mi Radar van de 03:00 a 23:00, en punto");
        }
        if (new HashSet<>(horas).size() != horas.size()) {
            throw new IllegalArgumentException("No repitas la misma hora");
        }
        return horas.stream().sorted().toList();
    }
}
