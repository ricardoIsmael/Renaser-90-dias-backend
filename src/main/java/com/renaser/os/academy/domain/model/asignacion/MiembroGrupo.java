package com.renaser.os.academy.domain.model.asignacion;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/** Un usuario dentro de un grupo. Clave natural (grupoId, usuarioId) — no tiene id propio. */
public final class MiembroGrupo {

    private final GrupoId grupoId;
    private final UserId usuarioId;
    private final Instant creadoEn;

    public MiembroGrupo(GrupoId grupoId, UserId usuarioId, Instant creadoEn) {
        this.grupoId = Objects.requireNonNull(grupoId, "grupoId es obligatorio");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
    }

    public GrupoId grupoId() {
        return grupoId;
    }

    public UserId usuarioId() {
        return usuarioId;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MiembroGrupo that)) {
            return false;
        }
        return grupoId.equals(that.grupoId) && usuarioId.equals(that.usuarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grupoId, usuarioId);
    }

    @Override
    public String toString() {
        return "MiembroGrupo[" + grupoId + ", " + usuarioId + "]";
    }
}
