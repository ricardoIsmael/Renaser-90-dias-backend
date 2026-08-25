package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comentarios_muro", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComentarioJpaEntity {

    @Id
    private UUID id;

    private UUID publicacionId;

    private UUID autorId;

    private String texto;

    private boolean oculto;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
