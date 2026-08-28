package com.renaser.os.habits.infrastructure.adapter.out.persistence.audioterapia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audioterapias", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioterapiaJpaEntity {

    @Id
    private Integer semana;

    private String titulo;

    private String rutaStorage;

    private String mime;

    private Integer tamanoBytes;

    private Short duracionDias;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
