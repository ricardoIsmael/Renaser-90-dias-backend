package com.renaser.os.calendar.infrastructure.adapter.out.persistence.recordatorio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla de alto volumen, PK bigint IDENTITY — mismo criterio que {@code notificaciones}. */
@Entity
@Table(name = "recordatorios_evento", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordatorioEventoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID eventoId;

    private Instant inicioOcurrencia;

    private UUID usuarioId;

    private Instant enviarEn;

    private Instant enviadoEn;

    private String motivoCancelacion;

    private Instant creadoEn;
}
