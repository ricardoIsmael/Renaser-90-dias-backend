package com.renaser.os.community.infrastructure.adapter.out.persistence.testimonio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "testimonios", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestimonioJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    private UUID publicacionMuroId;

    private String nombre;

    private String rolTexto;

    private String avatarUrl;

    private String fotoEventoRuta;

    private String texto;

    private short estrellas;

    private boolean destacado;

    private Instant creadoEn;
}
