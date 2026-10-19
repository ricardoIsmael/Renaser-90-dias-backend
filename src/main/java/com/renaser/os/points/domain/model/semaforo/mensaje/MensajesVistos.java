package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que la persona ya vio, para no repetirlo (FR-004): los mensajes de los últimos {@value #DIAS_SIN_REPETIR} días,
 * aparte el de ayer (el único que no se repite cuando ya vio todos los de su grupo) y, aparte, lo que el OTRO canal le
 * mostró hoy (D-281).
 *
 * <p>Los canales se suman: lo visto en Hoy cuenta para el chat y al revés. Lo que el otro canal mostró HOY es más
 * estricto que la ventana: nunca se repite el mismo día, ni siquiera cuando el catálogo del grupo está agotado.
 *
 * @param enLaVentana       ids vistos desde hace {@value #DIAS_SIN_REPETIR} días hasta ayer, inclusive, por cualquier canal
 * @param deAyer            ids vistos ayer (puede ser más de uno si el color cambió durante el día)
 * @param hoyEnElOtroCanal  ids que el otro canal mostró hoy, de cualquier grupo
 */
public record MensajesVistos(Set<String> enLaVentana, Set<String> deAyer, Set<String> hoyEnElOtroCanal) {

    public static final int DIAS_SIN_REPETIR = 14;

    public MensajesVistos {
        enLaVentana = Set.copyOf(enLaVentana);
        deAyer = Set.copyOf(deAyer);
        hoyEnElOtroCanal = Set.copyOf(hoyEnElOtroCanal);
    }

    /** Sin nada que el otro canal haya mostrado hoy. */
    public MensajesVistos(Set<String> enLaVentana, Set<String> deAyer) {
        this(enLaVentana, deAyer, Set.of());
    }

    public static MensajesVistos ninguno() {
        return new MensajesVistos(Set.of(), Set.of(), Set.of());
    }

    /** Primer día que cuenta como reciente según el {@code hoy} de la persona. */
    public static LocalDate desde(LocalDate hoy) {
        return hoy.minusDays(DIAS_SIN_REPETIR);
    }

    /**
     * Arma lo visto a partir de las filas guardadas. Las de hoy solo cuentan si son del otro canal que el que elige
     * ({@code canalPropio}); lo anterior a la ventana no cuenta.
     */
    public static MensajesVistos de(List<MensajeVisto> vistos, LocalDate hoy, CanalDelMensaje canalPropio) {
        LocalDate ayer = hoy.minusDays(1);
        Set<String> reciente = vistos.stream()
                .filter(v -> !v.fecha().isBefore(desde(hoy)) && !v.fecha().isAfter(ayer))
                .map(MensajeVisto::mensajeId).collect(Collectors.toSet());
        Set<String> deAyer = vistos.stream().filter(v -> v.fecha().equals(ayer))
                .map(MensajeVisto::mensajeId).collect(Collectors.toSet());
        Set<String> deHoyEnElOtroCanal = vistos.stream()
                .filter(v -> v.fecha().equals(hoy) && v.canal() != canalPropio)
                .map(MensajeVisto::mensajeId).collect(Collectors.toSet());
        return new MensajesVistos(reciente, deAyer, deHoyEnElOtroCanal);
    }
}
