package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.Ocurrencia;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cuándo se puede pasar lista de una ocurrencia: desde {@link #ANTES_DEL_INICIO} antes de que empiece hasta
 * {@link #DESPUES_DEL_FIN} después de que termine (D-256, supuesto S-1: a confirmar con el dueño).
 *
 * <p><b>Por qué no hay zona horaria acá.</b> Los dos bordes son DURACIONES contadas desde instantes (el
 * inicio efectivo de la ocurrencia y su fin), no «el día del evento»: 30 min antes de las 20:00 de Lima son
 * las 19:30 de Lima en cualquier zona en que corra el servidor. Nada depende de la fecha calendario, que es
 * lo que rompió E-91 (regla 02). El test {@code VentanaDePasarListaTest} lo fija con el reloj en la
 * madrugada UTC, cuando en Lima todavía es el día anterior.
 *
 * <p>Sin duración ({@code duracionMinutos} nulo, el evento no dice cuánto dura) el fin es el inicio: no se
 * inventa una duración.
 */
public record VentanaDePasarLista(Instant abre, Instant cierra) {

    public static final Duration ANTES_DEL_INICIO = Duration.ofMinutes(30);
    public static final Duration DESPUES_DEL_FIN = Duration.ofHours(12);

    public VentanaDePasarLista {
        Objects.requireNonNull(abre, "abre es obligatorio");
        Objects.requireNonNull(cierra, "cierra es obligatorio");
    }

    public static VentanaDePasarLista de(Ocurrencia ocurrencia) {
        Instant inicio = ocurrencia.iniciaEn();
        Integer minutos = ocurrencia.duracionMinutos();
        Instant fin = minutos == null ? inicio : inicio.plus(Duration.ofMinutes(minutos));
        return new VentanaDePasarLista(inicio.minus(ANTES_DEL_INICIO), fin.plus(DESPUES_DEL_FIN));
    }

    /** Los dos bordes cuentan como abiertos. */
    public boolean estaAbierta(Instant ahora) {
        return !ahora.isBefore(abre) && !ahora.isAfter(cierra);
    }

    public boolean yaAbrio(Instant ahora) {
        return !ahora.isBefore(abre);
    }
}
