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

    /** Spec 041 (V167): si la fila es un aviso del telefono; {@code false} = existe solo para el mensaje. */
    private boolean avisoEnApp = true;

    /** Spec 041 (V167): minutos antes del inicio de este aviso de mensaje, o {@code null}. */
    private Integer mensajeMinAntes;
}
