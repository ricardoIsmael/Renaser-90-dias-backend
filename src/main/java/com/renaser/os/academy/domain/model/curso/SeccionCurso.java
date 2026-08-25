package com.renaser.os.academy.domain.model.curso;

import com.renaser.os.users.api.UserRole;

import java.util.Objects;

/**
 * Una seccion de un curso. Agrupa lecciones y puede tener su propio gate de
 * dia de programa, independiente del curso — a partir del dia 15 del
 * programa las secciones representan RANGOS ("CICLO 2 (DIA 17-25)") en vez
 * de un dia puntual (ver `clase-diaria/repository.ts:154-166` del repo
 * viejo, aunque esa regla de rango vive en `clase-diaria`, no aca).
 *
 * <p>Fiel a {@code puedeVerSeccion}, RenaserBack {@code repository.ts:751-757}.
 */
public final class SeccionCurso {

    private final SeccionCursoId id;
    private final CursoId cursoId;
    private final String titulo;
    private final int orden;
    private final Integer diaDesbloqueo;

    public SeccionCurso(SeccionCursoId id, CursoId cursoId, String titulo, int orden, Integer diaDesbloqueo) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.cursoId = Objects.requireNonNull(cursoId, "cursoId es obligatorio");
        this.titulo = Objects.requireNonNull(titulo, "titulo es obligatorio");
        this.orden = orden;
        this.diaDesbloqueo = diaDesbloqueo;
    }

    /** Las lecciones sueltas (sin seccion) no tienen este gate; el personal (no TRAINEE) tampoco. */
    public boolean visibleEnCatalogoPara(UserRole rol, Integer diaProgramaParticipante) {
        Objects.requireNonNull(rol, "rol es obligatorio");
        if (diaDesbloqueo == null || rol != UserRole.TRAINEE) {
            return true;
        }
        int diaActual = diaProgramaParticipante == null ? Curso.DIA_PROGRAMA_INICIAL : diaProgramaParticipante;
        return diaActual >= diaDesbloqueo;
    }

    public SeccionCursoId id() {
        return id;
    }

    public CursoId cursoId() {
        return cursoId;
    }

    public String titulo() {
        return titulo;
    }

    public int orden() {
        return orden;
    }

    public Integer diaDesbloqueo() {
        return diaDesbloqueo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeccionCurso that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "SeccionCurso[" + id + ", " + titulo + "]";
    }
}
