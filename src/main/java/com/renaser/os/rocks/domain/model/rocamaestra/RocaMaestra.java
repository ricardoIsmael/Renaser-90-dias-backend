package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Roca Maestra: el objetivo de un participante en un eje (Cuerpo/Trabajo/Relaciones).
 * Una por (participante, eje) — {@code UNIQUE (participante_id, eje)} en el baseline.
 *
 * <p><b>RK-1 (ver docs/MODULO_ROCKS.md):</b> este módulo NO crea RocaMaestra. En el
 * repo viejo se crean/actualizan dentro de {@code completeOnboarding()}
 * (`src/features/onboarding/repository.ts:402-434`), responsabilidad del futuro
 * módulo {@code onboarding} (Ola 5, no construido todavía). `rocks` solo LEE — igual
 * que `points` no puede operar de punta a punta sin el agregado `participante` de
 * `users` (docs/MODULO_POINTS.md §7), `rocks` no tiene forma real de sembrar Rocas
 * Maestras hasta que `onboarding` exista. Es un hecho inmutable una vez creado (sin
 * columna `actualizado_en` en el baseline): por eso es un {@code record}, no una
 * clase con setters con nombre de intención.
 */
public record RocaMaestra(RocaMaestraId id, UserId participanteId, EjeObjetivo eje, String objetivo,
                           Instant creadoEn) {

    public RocaMaestra {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(eje, "eje es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        if (objetivo == null || objetivo.isBlank()) {
            throw new IllegalArgumentException("objetivo es obligatorio");
        }
    }

    /** Solo para el adaptador de persistencia: reconstruye una roca maestra ya existente. */
    public static RocaMaestra rehydrate(RocaMaestraId id, UserId participanteId, EjeObjetivo eje, String objetivo,
                                         Instant creadoEn) {
        return new RocaMaestra(id, participanteId, eje, objetivo, creadoEn);
    }
}
