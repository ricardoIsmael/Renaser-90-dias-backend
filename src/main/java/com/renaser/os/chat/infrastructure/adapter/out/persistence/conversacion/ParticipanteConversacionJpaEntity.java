package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participantes_conversacion", schema = "renaser")
@IdClass(ParticipanteConversacionId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipanteConversacionJpaEntity {

    @Id
    private UUID conversacionId;

    @Id
    private UUID usuarioId;

    private Instant ultimoLeidoEn;

    private Instant creadoEn;
}
