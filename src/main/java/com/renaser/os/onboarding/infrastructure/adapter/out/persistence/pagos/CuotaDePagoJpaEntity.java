package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.pagos;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Una fila de {@code cuotas_de_pago} (V158, spec 032). */
@Entity
@Table(name = "cuotas_de_pago", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuotaDePagoJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    private LocalDate venceEl;

    private BigDecimal monto;

    /** {@code PENDIENTE} o {@code PAGADA}. */
    private String estado;

    private UUID estadoCambiadoPor;

    private Instant estadoCambiadoEn;

    private Instant creadaEn;
}
