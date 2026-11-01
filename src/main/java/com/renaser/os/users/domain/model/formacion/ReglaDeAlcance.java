package com.renaser.os.users.domain.model.formacion;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Qué formaciones ve cada persona (spec 022, D-295). Es LA regla del aislamiento y está en un solo lugar:
 *
 * <ul>
 *   <li><b>Administración, Alquimista y Soporte TI</b> ven las tres.</li>
 *   <li><b>Aprendiz</b>: solo la suya (la de su participación; la Escuela si por un error de datos no tuviera fila).</li>
 *   <li><b>Personal</b> (mentor, guía de aprendiz, líder de mentores): solo la <b>activa</b>, aunque tenga varias asignadas — nunca ve nada
 *       mezclado. Si la marcada como activa ya no está, cae a la primera asignada. Sin ninguna asignada, no ve nada.</li>
 * </ul>
 *
 * Lógica pura: recibe lo que la base dice de la persona.
 */
public final class ReglaDeAlcance {

    private ReglaDeAlcance() {
    }

    /** Una formación asignada al personal y si es la que eligió trabajar. */
    public record Asignacion(Formacion formacion, boolean activa) {
    }

    public static AlcanceDeFormacion alcanceDe(UserRole rol, Formacion deLaParticipacion, List<Asignacion> asignadas) {
        return switch (rol) {
            case ADMIN, ALCHEMIST, IT_SUPPORT -> AlcanceDeFormacion.todas();
            case TRAINEE -> AlcanceDeFormacion.de(deLaParticipacion == null ? Formacion.POR_DEFECTO : deLaParticipacion);
            case MENTOR, TRAINEE_GUIDE, MENTOR_LEAD -> formacionActiva(asignadas)
                    .map(AlcanceDeFormacion::de)
                    .orElseGet(AlcanceDeFormacion::ninguna);
        };
    }

    /** La activa del personal: la marcada, o la primera (por orden de las formaciones) si ninguna lo está. */
    public static Optional<Formacion> formacionActiva(List<Asignacion> asignadas) {
        Optional<Formacion> marcada = asignadas.stream().filter(Asignacion::activa).map(Asignacion::formacion).findFirst();
        if (marcada.isPresent()) {
            return marcada;
        }
        return asignadas.stream().map(Asignacion::formacion).sorted().findFirst();
    }

    /** Los roles que trabajan en una o varias formaciones asignadas (el resto ve una sola o todas). */
    public static boolean esPersonalConFormaciones(UserRole rol) {
        return rol == UserRole.MENTOR || rol == UserRole.TRAINEE_GUIDE || rol == UserRole.MENTOR_LEAD;
    }

    /** Los roles que ven las tres formaciones y no tienen una activa. */
    public static boolean veTodas(UserRole rol) {
        return rol == UserRole.ADMIN || rol == UserRole.ALCHEMIST || rol == UserRole.IT_SUPPORT;
    }
}
