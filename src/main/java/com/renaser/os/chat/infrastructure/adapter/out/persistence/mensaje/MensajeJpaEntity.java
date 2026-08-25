package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import jakarta.persistence.Column;
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

@Entity
@Table(name = "mensajes", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeJpaEntity {

    @Id
    private UUID id;

    private UUID conversacionId;

    private UUID emisorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoMensajeJpa tipo;

    private String texto;

    private String mediaBucket;

    private String mediaRuta;

    private String mediaMime;

    private Integer mediaBytes;

    /** Hibernate no separa un unico caracter mayuscula al final de un identificador
     * ({@code CamelCaseToUnderscoresNamingStrategy} excluye el ultimo indice) — sin este
     * {@code @Column} explicito mapea a {@code media_duracions} en vez de
     * {@code media_duracion_s} (E-35, {@code docs/BITACORA_ERRORES.md}). */
    @Column(name = "media_duracion_s")
    private Short mediaDuracionS;

    private boolean oculto;

    private Instant eliminadoEn;

    /** Mismo motivo que {@code mediaDuracionS}: mayusculas consecutivas ("AId") no se
     * separan por el algoritmo implicito de Hibernate — sin esto mapea a {@code respuestaaid}
     * en vez de {@code respuesta_a_id} (E-35). */
    @Column(name = "respuesta_a_id")
    private UUID respuestaAId;

    private Instant creadoEn;
}
