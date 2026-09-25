package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code propuestas_acompanante} (V63). El dominio es {@code PropuestaAccion}. */
@Entity
@Table(name = "propuestas_acompanante", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropuestaAccionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "participante_id")
    private UUID participanteId;

    @Column(name = "herramienta")
    private String herramienta;

    /** JUSTIFICADO jsonb: argumentos de herramienta, forma distinta por herramienta y opaca para SQL (V63). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "argumentos", columnDefinition = "jsonb")
    private String argumentos;

    @Column(name = "argumentos_hash")
    private String argumentosHash;

    @Column(name = "resumen")
    private String resumen;

    @Column(name = "estado")
    private String estado;

    @Column(name = "creada_en")
    private Instant creadaEn;

    @Column(name = "vence_en")
    private Instant venceEn;

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    @Column(name = "resultado")
    private String resultado;

    /**
     * Bloqueo optimista. {@code Long} y no {@code long} a proposito: Spring Data decide si la fila
     * es nueva mirando si la version es {@code null}, y el id lo asigna la aplicacion.
     */
    @Version
    @Column(name = "version")
    private Long version;
}
