package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

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
 * Arco exclusivo (`registro_habito_id` XOR `roca_diaria_id`, CHECK
 * `verdugo_un_destino` en el baseline): se mapean las DOS columnas de la
 * tabla directo, sin unificarlas — el mapper las traduce hacia/desde el
 * `DestinoVerdugo`+UUID único del dominio.
 */
@Entity
@Table(name = "eventos_verdugo", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoVerdugoJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private UUID registroHabitoId;

    private UUID rocaDiariaId;

    private Instant disparadoEn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ResultadoVerdugoJpa resultado;

    private Instant resueltoEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
