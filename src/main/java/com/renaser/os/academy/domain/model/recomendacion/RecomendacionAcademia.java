package com.renaser.os.academy.domain.model.recomendacion;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Cache diaria (una fila por participante por dia) de la recomendacion de
 * clase generada por IA — Academia Adaptativa. La generacion real (Gemini,
 * radar de energia/animo) es Ola 5 (fuera de alcance, ver
 * {@code RecomendarClasePort}); esta clase solo modela la fila de cache tal
 * como ya la define el baseline (`recomendaciones_academia`, PK
 * (participante_id, fecha)).
 */
public final class RecomendacionAcademia {

    private final UserId participanteId;
    private final LocalDate fecha;
    private final LeccionId leccionId;
    private final String motivo;
    private final Instant creadoEn;

    public RecomendacionAcademia(UserId participanteId, LocalDate fecha, LeccionId leccionId, String motivo,
                                  Instant creadoEn) {
        this.participanteId = Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        this.fecha = Objects.requireNonNull(fecha, "fecha es obligatoria");
        this.leccionId = Objects.requireNonNull(leccionId, "leccionId es obligatorio");
        this.motivo = requireMotivo(motivo);
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
    }

    private static String requireMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo es obligatorio");
        }
        return motivo;
    }

    public UserId participanteId() {
        return participanteId;
    }

    public LocalDate fecha() {
        return fecha;
    }

    public LeccionId leccionId() {
        return leccionId;
    }

    public String motivo() {
        return motivo;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecomendacionAcademia that)) {
            return false;
        }
        return participanteId.equals(that.participanteId) && fecha.equals(that.fecha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participanteId, fecha);
    }

    @Override
    public String toString() {
        return "RecomendacionAcademia[" + participanteId + ", " + fecha + "]";
    }
}
