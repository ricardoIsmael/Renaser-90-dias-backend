package com.renaser.os.onboarding.domain.model.terminos;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;

import java.util.Objects;

/**
 * Cómo se nombra el programa en el «Acuerdo de transformación» (spec 039, D-312): «Formación RENASER», «Diplomado RENASER» o «Certificación RENASER», y su duración
 * («90 días», «3 meses», «1 año»). Es la línea «Programa» de «Mi aceptación» y lo que dice la introducción del texto de cada formación (V162). Lógica pura.
 *
 * <p>La Escuela se llama «Formación» aquí por decisión del dueño (2026-11-02): el catálogo de formaciones (spec 022) la nombra «Escuela Renaser» y no se toca.
 */
public final class ProgramaDelAcuerdo {

    private static final String SEPARADOR = " · ";

    private ProgramaDelAcuerdo() {
    }

    /** «Formación RENASER», «Diplomado RENASER» o «Certificación RENASER». */
    public static String nombre(Formacion formacion) {
        return switch (Objects.requireNonNull(formacion, "La formación es obligatoria")) {
            case ESCUELA -> "Formación RENASER";
            case DIPLOMADO -> "Diplomado RENASER";
            case CERTIFICACION -> "Certificación RENASER";
        };
    }

    /**
     * «Formación RENASER · 90 días», «Diplomado RENASER · 3 meses» o «Diplomado RENASER · 1 año». Sin duración (quien no tiene programa que mostrar) es solo el nombre:
     * no se inventa un plazo.
     */
    public static String etiqueta(Formacion formacion, Integer duracionDias) {
        if (duracionDias == null) {
            return nombre(formacion);
        }
        return nombre(formacion) + SEPARADOR + DuracionDelPrograma.plazoDelObjetivo(duracionDias, formacion);
    }
}
