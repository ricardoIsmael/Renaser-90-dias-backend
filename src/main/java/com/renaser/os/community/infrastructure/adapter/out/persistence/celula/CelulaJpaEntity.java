package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "celulas", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CelulaJpaEntity {

    @Id
    private UUID id;

    private String nombre;

    /** `usuario_id` de un `perfiles_mentor` (tabla de `users`) — se guarda como UUID
     * plano, nunca como relacion JPA hacia otro modulo (CLAUDE.MD sec. 5.1). */
    private UUID mentorId;

    private UUID cohorteId;

    private String urlVideollamada;

    private Instant proximaSesionEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
