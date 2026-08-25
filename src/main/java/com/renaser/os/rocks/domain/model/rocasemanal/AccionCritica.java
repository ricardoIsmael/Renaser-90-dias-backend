package com.renaser.os.rocks.domain.model.rocasemanal;

/**
 * Una de las 3 acciones críticas de una Roca Semanal (`orden` 1..3). Antes
 * columnas `critical_action_1/2/3` en `weekly_rocks` — restaurada a 1FN en la
 * tabla `acciones_criticas` (baseline, comentario P-10).
 */
public record AccionCritica(int orden, String descripcion) {

    private static final int MAX_LEN = 500;

    public AccionCritica {
        if (orden < 1 || orden > 3) {
            throw new IllegalArgumentException("orden debe estar entre 1 y 3: " + orden);
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("descripcion es obligatoria (accion critica " + orden + ")");
        }
        if (descripcion.length() > MAX_LEN) {
            throw new IllegalArgumentException("descripcion supera " + MAX_LEN + " caracteres (accion critica "
                    + orden + ")");
        }
    }
}
