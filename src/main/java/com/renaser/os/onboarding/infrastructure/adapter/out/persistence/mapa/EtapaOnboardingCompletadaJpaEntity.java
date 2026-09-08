package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** `etapas_onboarding_completadas` (V41): una fila por (usuario, flujo). */
@Entity
@Table(name = "etapas_onboarding_completadas", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtapaOnboardingCompletadaJpaEntity {

    @EmbeddedId
    private Clave clave;

    private Instant completadoEn;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Clave implements Serializable {
        private UUID usuarioId;
        private String flujo;
    }
}
