package com.renaser.os.academy.domain.model.progreso;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Marca que un usuario completo una leccion. Clave natural (usuarioId,
 * leccionId) — no tiene id propio. "Descompletar" (DELETE
 * `/api/v1/lecciones/{id}/complete`, espejo de `desmarcarLeccion` del repo
 * RN) es un borrado de esta fila, no una transicion de estado del objeto —
 * por eso vive en el puerto de salida ({@code SaveProgresoLeccionPort.desmarcarCompletada})
 * y no como metodo aca. Ver `docs/MODULO_ACADEMY.md` §5, decision AC-16.
 */
public final class ProgresoLeccion {

    private final UserId usuarioId;
    private final LeccionId leccionId;
    private final Instant completadaEn;

    public ProgresoLeccion(UserId usuarioId, LeccionId leccionId, Instant completadaEn) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        this.leccionId = Objects.requireNonNull(leccionId, "leccionId es obligatorio");
        this.completadaEn = Objects.requireNonNull(completadaEn, "completadaEn es obligatorio");
    }

    public UserId usuarioId() {
        return usuarioId;
    }

    public LeccionId leccionId() {
        return leccionId;
    }

    public Instant completadaEn() {
        return completadaEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProgresoLeccion that)) {
            return false;
        }
        return usuarioId.equals(that.usuarioId) && leccionId.equals(that.leccionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, leccionId);
    }

    @Override
    public String toString() {
        return "ProgresoLeccion[" + usuarioId + ", " + leccionId + "]";
    }
}
