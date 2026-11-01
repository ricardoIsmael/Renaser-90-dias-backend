package com.renaser.os.shared.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Las formaciones que puede ver quien actúa (spec 022, D-295). Es el valor que cada módulo recibe para filtrar y que el SQL
 * convierte en un predicado ({@code shared.infrastructure.persistence.FiltroDeFormacionSql}).
 *
 * <ul>
 *   <li>Administración, Alquimista y Soporte TI: {@link #todas()}.</li>
 *   <li>Aprendiz: solo la suya. Personal con varias formaciones: solo la activa (nunca ve nada mezclado).</li>
 *   <li>Personal sin ninguna asignada: {@link #ninguna()}; no ve nada.</li>
 * </ul>
 *
 * Inmutable. {@code ninguna()} es el estado seguro: un filtro con alcance vacío no deja pasar ninguna fila.
 */
public record AlcanceDeFormacion(Set<Formacion> formaciones) {

    public AlcanceDeFormacion {
        formaciones = formaciones.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(formaciones));
    }

    public static AlcanceDeFormacion todas() {
        return new AlcanceDeFormacion(EnumSet.allOf(Formacion.class));
    }

    public static AlcanceDeFormacion ninguna() {
        return new AlcanceDeFormacion(Set.of());
    }

    public static AlcanceDeFormacion de(Formacion... formaciones) {
        return de(Arrays.asList(formaciones));
    }

    public static AlcanceDeFormacion de(Collection<Formacion> formaciones) {
        return formaciones.isEmpty() ? ninguna() : new AlcanceDeFormacion(EnumSet.copyOf(formaciones));
    }

    public boolean incluye(Formacion formacion) {
        return formaciones.contains(formacion);
    }

    public boolean esVacio() {
        return formaciones.isEmpty();
    }

    public boolean esTodas() {
        return formaciones.size() == Formacion.values().length;
    }

    /** La única formación del alcance, si es una sola (el caso del aprendiz y del personal). */
    public Optional<Formacion> unica() {
        return formaciones.size() == 1 ? formaciones.stream().findFirst() : Optional.empty();
    }

    /**
     * Acota este alcance a una formación pedida por parámetro (el filtro «Formación» de las listas de Administración).
     * {@code null} = sin filtro. Pedir una formación fuera del alcance deja el alcance vacío: no revela nada.
     */
    public AlcanceDeFormacion acotadoA(Formacion pedida) {
        if (pedida == null) {
            return this;
        }
        return incluye(pedida) ? de(pedida) : ninguna();
    }

    /**
     * @throws ObjetoDeOtraFormacionException si la formación de lo pedido está fuera del alcance (404, como si no existiera)
     */
    public void exigirQueIncluya(Formacion formacionDelObjeto) {
        if (!incluye(formacionDelObjeto)) {
            throw new ObjetoDeOtraFormacionException();
        }
    }
}
