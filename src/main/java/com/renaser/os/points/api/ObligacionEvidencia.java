package com.renaser.os.points.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La unidad que se mide: una obligación de evidencia, no un archivo. Reenviar tres veces la
 * misma obligación sigue siendo una entrega (plan.md §8).
 *
 * @param obligacionId identidad de la obligación en su módulo de origen. Es lo que deduplica.
 * @param aprendizId   dueño de la obligación.
 * @param venceEn      cuándo dejó de ser exigible. Decide a qué ventana pertenece.
 * @param entregadaEn  primera entrega acreditable, o {@code null} si no hubo ninguna.
 *                     {@code null} significa "sin entrega", nunca cero ni "no cumplió" por
 *                     falta de datos: esa distinción la hace quien arma la lista.
 * @param verificada   revisada y aprobada. Se informa aparte y no altera el porcentaje (D-03).
 */
public record ObligacionEvidencia(UUID obligacionId, UUID aprendizId, Instant venceEn, Instant entregadaEn,
                                   boolean verificada) {

    public ObligacionEvidencia {
        Objects.requireNonNull(obligacionId, "obligacionId es obligatorio");
        Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        Objects.requireNonNull(venceEn, "venceEn es obligatorio");
    }

    public boolean entregada() {
        return entregadaEn != null;
    }
}
