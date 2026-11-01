package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.List;
import java.util.Objects;

/**
 * Una sección del diagnóstico. La {@code portada} (clave {@link #CLAVE_DE_LA_PORTADA}) no es un paso: su título y su descripción son la primera pantalla
 * («Tiempo estimado: 15 minutos / No buscamos respuestas perfectas…») y no lleva preguntas.
 */
public record SeccionDelDiagnostico(int id, String clave, String titulo, String descripcion, int orden,
                                    List<PreguntaDelDiagnostico> preguntas) {

    public static final String CLAVE_DE_LA_PORTADA = "portada";
    public static final int MAX_TITULO = 200;
    public static final int MAX_DESCRIPCION = 1000;

    public SeccionDelDiagnostico {
        Objects.requireNonNull(clave, "clave es obligatoria");
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título de la sección es obligatorio");
        }
        titulo = titulo.trim();
        if (titulo.length() > MAX_TITULO) {
            throw new IllegalArgumentException("El título de la sección no puede pasar de " + MAX_TITULO + " caracteres");
        }
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            throw new IllegalArgumentException("La descripción no puede pasar de " + MAX_DESCRIPCION + " caracteres");
        }
        preguntas = List.copyOf(preguntas == null ? List.of() : preguntas);
        if (esPortada(clave) && !preguntas.isEmpty()) {
            throw new IllegalArgumentException("La portada no lleva preguntas");
        }
    }

    public boolean esPortada() {
        return esPortada(clave);
    }

    private static boolean esPortada(String clave) {
        return CLAVE_DE_LA_PORTADA.equals(clave);
    }

    public SeccionDelDiagnostico conPreguntas(List<PreguntaDelDiagnostico> nuevas) {
        return new SeccionDelDiagnostico(id, clave, titulo, descripcion, orden, nuevas);
    }
}
