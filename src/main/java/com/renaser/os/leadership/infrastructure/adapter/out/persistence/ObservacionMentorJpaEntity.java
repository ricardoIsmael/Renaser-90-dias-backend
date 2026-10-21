package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tabla {@code observaciones_mentor} (V89). Append-only: el adaptador solo inserta. */
@Entity
@Table(name = "observaciones_mentor", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObservacionMentorJpaEntity {

    @Id
    private UUID id;

    private UUID mentorId;

    private UUID autorId;

    /** RECONOCIMIENTO / SUGERENCIA / ALERTA: texto con CHECK en la base, no un enum de Postgres. */
    private String tipo;

    private String texto;

    private boolean enviadaPorChat;

    private UUID mensajeId;

    private String claveOperacion;

    private Instant creadoEn;

    /** La persona de la que trata (V109, spec 005); NULL en las de antes. */
    private UUID personaId;

    /** El primer día de la semana que el líder miraba (V109); solo con {@code personaId}. */
    private LocalDate semanaDesde;
}
