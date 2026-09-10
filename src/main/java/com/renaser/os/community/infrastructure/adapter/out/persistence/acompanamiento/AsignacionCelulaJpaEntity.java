package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
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
 * Fila de `asignaciones_celula` (V45). Los enums viajan por nombre y espejan los tipos de
 * Postgres, como el resto del proyecto.
 */
@Entity
@Table(name = "asignaciones_celula", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionCelulaJpaEntity {

    @Id
    private UUID id;

    private UUID celulaId;

    /** `usuarios.id` plano: `community` no importa el agregado de `users` (CLAUDE.MD sec. 5.1). */
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private FuncionAcompanamiento funcion;

    private Instant inicio;

    /** NULL = vigente. El intervalo es semiabierto [inicio, fin). */
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MotivoAsignacion motivo;

    /** NULL = lo ejecuto un job; no se inventa un usuario tecnico. */
    private UUID actorId;

    private String claveOperacion;

    private Instant creadoEn;
}
