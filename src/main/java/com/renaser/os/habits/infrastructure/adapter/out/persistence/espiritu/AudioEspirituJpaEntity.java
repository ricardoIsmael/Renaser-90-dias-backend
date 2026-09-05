package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Clave natural pura: el dia de audio (1..90) es el PK, sin surrogate — igual que {@code audioterapias.semana}. */
@Entity
@Table(name = "audios_espiritu", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioEspirituJpaEntity {

    @Id
    private Integer dia;

    private String titulo;

    private String driveFileId;

    private String mime;

    private Integer tamanoBytes;

    /** V25 — hoy NULL en las 43 filas; ver el javadoc de la migracion. */
    private String rutaStorage;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
