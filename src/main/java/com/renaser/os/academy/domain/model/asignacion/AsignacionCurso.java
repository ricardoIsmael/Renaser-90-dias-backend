package com.renaser.os.academy.domain.model.asignacion;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Una asignacion administrativa de un curso a un usuario o a un grupo — el
 * arco exclusivo usuario⊕grupo ya existia en el schema viejo y se conserva
 * (V1__baseline_renaser.sql, `asignacion_destino_unico`).
 *
 * <p><b>OJO — esto NO es lo mismo que el gate de catalogo de {@code Curso}.</b>
 * Hoy, en RenaserBack, las asignaciones no alteran si un TRAINEE ve un curso
 * en su catalogo (ver decision AC-01 en `Curso`). Esta clase modela la
 * vigencia de la asignacion en si — el concepto que consume
 * {@code academy.api.AccesoCursoFinder} para que `calendar` resuelva la
 * audiencia `CURSO` de un evento (contrato ya definido, ver
 * `docs/MODULO_ACADEMY.md` §4).
 */
public final class AsignacionCurso {

    private final AsignacionCursoId id;
    private final CursoId cursoId;
    private final UserId usuarioId;
    private final GrupoId grupoId;
    private final Instant desde;
    private final Instant hasta;
    private final Instant revocadaEn;
    private final UserId asignadaPor;
    private final Instant creadoEn;

    public AsignacionCurso(AsignacionCursoId id, CursoId cursoId, UserId usuarioId, GrupoId grupoId, Instant desde,
                            Instant hasta, Instant revocadaEn, UserId asignadaPor, Instant creadoEn) {
        this.id = id;
        this.cursoId = Objects.requireNonNull(cursoId, "cursoId es obligatorio");
        requireDestinoExclusivo(usuarioId, grupoId);
        this.usuarioId = usuarioId;
        this.grupoId = grupoId;
        this.desde = desde;
        this.hasta = hasta;
        this.revocadaEn = revocadaEn;
        this.asignadaPor = asignadaPor;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
    }

    private static void requireDestinoExclusivo(UserId usuarioId, GrupoId grupoId) {
        boolean tieneUsuario = usuarioId != null;
        boolean tieneGrupo = grupoId != null;
        if (tieneUsuario == tieneGrupo) {
            throw new IllegalArgumentException(
                    "una asignacion debe tener exactamente uno de usuarioId o grupoId (arco exclusivo)");
        }
    }

    /** Vigente = no revocada, ya empezo (o sin fecha de inicio) y no vencio (o sin fecha de fin). */
    public boolean vigente(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        if (revocadaEn != null) {
            return false;
        }
        if (desde != null && ahora.isBefore(desde)) {
            return false;
        }
        return hasta == null || !ahora.isAfter(hasta);
    }

    public boolean esDirecta() {
        return usuarioId != null;
    }

    public AsignacionCursoId id() {
        return id;
    }

    public CursoId cursoId() {
        return cursoId;
    }

    public UserId usuarioId() {
        return usuarioId;
    }

    public GrupoId grupoId() {
        return grupoId;
    }

    public Instant desde() {
        return desde;
    }

    public Instant hasta() {
        return hasta;
    }

    public Instant revocadaEn() {
        return revocadaEn;
    }

    public UserId asignadaPor() {
        return asignadaPor;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsignacionCurso that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AsignacionCurso[" + id + ", curso=" + cursoId + "]";
    }
}
