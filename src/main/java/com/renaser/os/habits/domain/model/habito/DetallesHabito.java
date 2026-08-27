package com.renaser.os.habits.domain.model.habito;

/**
 * Datos editables de un habito de catalogo (panel admin, hueco #11) que NO son su
 * identidad ni su titulo: la categoria, la exigencia de evidencia y las dos banderas de
 * opcionalidad. Deliberadamente NO incluye {@code claveSistema} ni {@code tipo} — esos dos
 * campos son invariantes protegidos de {@link Habito} (ver su javadoc de
 * {@code actualizarDetalles}): cambiarlos post-creacion rompe la resolucion de politicas
 * ({@code SelectorHabito}) y el significado de los `registros_habito` ya generados.
 */
public record DetallesHabito(String descripcion, String categoriaClave, ExigenciaEvidencia exigenciaEvidencia,
                              boolean esOpcional, boolean obligatorioEnIntoxicacion) {

    public DetallesHabito {
        if (categoriaClave == null || categoriaClave.isBlank()) {
            throw new IllegalArgumentException("categoriaClave es obligatoria");
        }
        if (exigenciaEvidencia == null) {
            throw new IllegalArgumentException("exigenciaEvidencia es obligatoria");
        }
    }
}
