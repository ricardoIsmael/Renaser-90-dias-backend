package com.renaser.os.rocks.domain.model.verdugo;

/**
 * A qué le dispara el Verdugo — arco exclusivo con la tabla `eventos_verdugo`
 * (`registro_habito_id` XOR `roca_diaria_id`). {@code REGISTRO_HABITO} apunta
 * a una tabla de `habits` (otro módulo); se referencia solo por UUID crudo,
 * nunca con un tipo de dominio de `habits` — `eventos_verdugo` es una tabla
 * compartida entre los dos módulos (ver docs/MODULO_ROCKS.md, RK-6).
 */
public enum DestinoVerdugo {
    ROCA_DIARIA,
    REGISTRO_HABITO
}
