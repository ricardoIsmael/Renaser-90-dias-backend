package com.renaser.os.academy.domain.model.asignacion;

import java.time.Instant;
import java.util.Objects;

/** Un grupo de usuarios al que se le puede asignar un curso de una sola vez (en vez de uno por uno). */
public final class Grupo {

    private final GrupoId id;
    private final String nombre;
    private final Instant creadoEn;

    public Grupo(GrupoId id, String nombre, Instant creadoEn) {
        this.id = id;
        this.nombre = requireNombre(nombre);
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
    }

    private static String requireNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("nombre es obligatorio");
        }
        return nombre.trim();
    }

    public GrupoId id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Grupo grupo)) {
            return false;
        }
        return Objects.equals(id, grupo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Grupo[" + id + ", " + nombre + "]";
    }
}
