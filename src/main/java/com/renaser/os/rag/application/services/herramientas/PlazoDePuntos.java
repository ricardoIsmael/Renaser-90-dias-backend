package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.TramoPuntos;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un habito que todavia se puede entregar, visto como la pregunta "¿cuanto paga si lo entrego en
 * tal instante?".
 *
 * <p><b>No conoce la escala de puntos.</b> Los tramos vienen resueltos desde {@code habits}
 * (D-97 vive alla): aca solo se ubica un instante en esa lista. Si la escala cambia, esta clase
 * no se entera.
 */
record PlazoDePuntos(HabitoDelDia habito) {

    PlazoDePuntos {
        Objects.requireNonNull(habito, "habito es obligatorio");
        if (!habito.sigueEnJuego()) {
            throw new IllegalArgumentException("Solo un habito con puntos en juego tiene plazo de puntos");
        }
    }

    /** {@code null} si el habito no tiene horario: entonces no vence y siempre paga lo mismo. */
    Instant vence() {
        return habito.plazo();
    }

    int puntosAhora() {
        return habito.puntosEnJuego();
    }

    /** El tramo en el que cae una entrega en {@code instante}; vacio si no hay escala o ya vencio. */
    Optional<TramoPuntos> tramoEn(Instant instante) {
        return habito.tramos().stream().filter(tramo -> instante.isBefore(tramo.hasta())).findFirst();
    }

    /** El tramo que sigue a {@code tramo}, si queda alguno antes del vencimiento. */
    Optional<TramoPuntos> siguienteA(TramoPuntos tramo) {
        List<TramoPuntos> tramos = habito.tramos();
        int posicion = tramos.indexOf(tramo);
        return posicion >= 0 && posicion + 1 < tramos.size() ? Optional.of(tramos.get(posicion + 1))
                : Optional.empty();
    }

    /** Lo menos que paga antes de vencer: el ultimo tramo de la escala. */
    int puntosMinimos() {
        List<TramoPuntos> tramos = habito.tramos();
        return tramos.isEmpty() ? puntosAhora() : tramos.get(tramos.size() - 1).puntos();
    }

    /**
     * Cuanto pagaria entregado en {@code instante}. Sin horario paga siempre lo mismo que ahora;
     * con horario, pasado el plazo ya no paga nada. Sin escala (un llamador que no la trajo) se
     * asume el puntaje de ahora hasta el plazo: no se inventa una escala que {@code habits} no dio.
     */
    int puntosEn(Instant instante) {
        if (habito.tramos().isEmpty()) {
            return vence() != null && instante.isAfter(vence()) ? 0 : puntosAhora();
        }
        return tramoEn(instante).map(TramoPuntos::puntos).orElse(0);
    }
}
