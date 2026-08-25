package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Dueña real de la tabla {@code evidencias} (a diferencia del INSERT nativo que
 * {@code rocks} usaba antes de que este módulo existiera — RK-2, cerrado). Los tres
 * campos de destino son nullable a propósito: el arco exclusivo (CHECK
 * {@code evidencia_un_destino}) se aplica en el dominio vía
 * {@code evidence.api.DestinoEvidencia} (sealed interface) y en la base vía el CHECK
 * — esta entidad solo espeja la forma física de la columna.
 */
@Entity
@Table(name = "evidencias", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenciaJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private UUID registroHabitoId;

    private UUID rocaDiariaId;

    private UUID registroEspirituId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoEvidenciaJpa tipo;

    private String bucket;

    private String rutaStorage;

    private String contenidoTexto;

    private Instant timestampExif;

    private Instant subidaEn;

    private Double gpsLat;

    private Double gpsLng;

    private boolean esPrincipal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoValidacionJpa estadoValidacion;

    private String notasValidacion;

    private Short intentosIa;

    private boolean penalizacionAplicada;

    private boolean publicadaEnMuro;

    private Instant creadoEn;
}
