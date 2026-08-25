package com.renaser.os.academy.domain.model.curso;

import java.util.Objects;

/** Un adjunto descargable de una leccion (PDF, enlace externo, etc.). */
public final class RecursoLeccion {

    private final Long id;
    private final LeccionId leccionId;
    private final String nombre;
    private final String url;
    private final int orden;

    public RecursoLeccion(Long id, LeccionId leccionId, String nombre, String url, int orden) {
        this.id = id;
        this.leccionId = Objects.requireNonNull(leccionId, "leccionId es obligatorio");
        this.nombre = nombre;
        this.url = Objects.requireNonNull(url, "url es obligatoria");
        this.orden = orden;
    }

    public Long id() {
        return id;
    }

    public LeccionId leccionId() {
        return leccionId;
    }

    public String nombre() {
        return nombre;
    }

    public String url() {
        return url;
    }

    public int orden() {
        return orden;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecursoLeccion that)) {
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
        return "RecursoLeccion[" + id + ", " + nombre + "]";
    }
}
