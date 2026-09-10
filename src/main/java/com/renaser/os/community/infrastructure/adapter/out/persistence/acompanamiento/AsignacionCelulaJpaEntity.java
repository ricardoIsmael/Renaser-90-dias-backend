package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
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

    /**
     * La escribe la BASE, no Java: {@code creado_en timestamptz NOT NULL DEFAULT now()} (V45).
     *
     * <p>{@code insertable = false} no es cosmetico. Sin el, Hibernate incluye la columna en el
     * INSERT con el {@code null} que trae el mapper, y un DEFAULT <b>no</b> se aplica cuando la
     * sentencia manda NULL explicito: la fila choca contra el NOT NULL y falla TODA escritura por
     * este adaptador. Rotacion, traslado y asignacion administrativa incluidas.
     *
     * <p>Se lo comio la suite entera porque las pruebas de esos tres casos de uso corren contra
     * {@code AcompanamientoEnMemoria}, un doble que nunca toca Postgres; y las asignaciones que
     * existian en la base las habia puesto el backfill de V45 en SQL, sin pasar por aca. El camino
     * de escritura de Java no lo ejercitaba nada hasta {@code AsignacionCelulaConcurrenciaIT}.
     */
    @Column(insertable = false, updatable = false)
    private Instant creadoEn;
}
