package com.renaser.os.community.domain.model.cohorte;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Agrupacion administrativa de celulas (tabla `cohortes`). Traduccion 1:1 de
 * `features/community/service.ts` — sin logica de negocio propia mas alla de la
 * transicion de estado; el resto (autorizacion, conteo de celulas) vive en el caso de uso.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Cohorte {

    private final CohorteId id;
    private String nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private EstadoCohorte estado;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static Cohorte crear(String nombre, LocalDate fechaInicio, LocalDate fechaFin, Instant ahora) {
        requireNombreValido(nombre);
        Objects.requireNonNull(fechaInicio, "fechaInicio es obligatoria");
        requireRangoValido(fechaInicio, fechaFin);
        return new Cohorte(CohorteId.newId(), nombre, fechaInicio, fechaFin, EstadoCohorte.PLANIFICADA, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Cohorte rehydrate(CohorteId id, String nombre, LocalDate fechaInicio, LocalDate fechaFin,
                                     EstadoCohorte estado, Instant creadoEn, Instant actualizadoEn) {
        return new Cohorte(id, nombre, fechaInicio, fechaFin, estado, creadoEn, actualizadoEn);
    }

    public void actualizarDatos(String nombre, LocalDate fechaInicio, LocalDate fechaFin, Instant ahora) {
        String nombreEfectivo = nombre != null ? nombre : this.nombre;
        LocalDate inicioEfectivo = fechaInicio != null ? fechaInicio : this.fechaInicio;
        requireNombreValido(nombreEfectivo);
        requireRangoValido(inicioEfectivo, fechaFin);
        this.nombre = nombreEfectivo;
        this.fechaInicio = inicioEfectivo;
        this.fechaFin = fechaFin;
        this.actualizadoEn = ahora;
    }

    public void transicionarA(EstadoCohorte destino, Instant ahora) {
        Objects.requireNonNull(destino, "destino es obligatorio");
        if (!estado.puedeTransicionarA(destino)) {
            throw new IllegalArgumentException("Transicion de estado invalida: " + estado + " -> " + destino);
        }
        this.estado = destino;
        this.actualizadoEn = ahora;
    }

    private static void requireNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la cohorte es obligatorio");
        }
        if (nombre.length() > 200) {
            throw new IllegalArgumentException("El nombre de la cohorte no puede pasar de 200 caracteres");
        }
    }

    private static void requireRangoValido(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("fechaFin no puede ser anterior a fechaInicio");
        }
    }

    @Override
    public String toString() {
        return "Cohorte[" + id + ", " + nombre + ", " + estado + "]";
    }
}
