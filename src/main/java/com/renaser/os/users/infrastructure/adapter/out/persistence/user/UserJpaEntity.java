package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla `usuarios` (docs/db/sql/BD_NUEVA_V1.sql). Solo mapea las columnas que el
 * dominio usa hoy — telefono/ciudad/pais/motivo_estado/creado_en/actualizado_en quedan
 * fuera a proposito: son nullable o tienen DEFAULT en Postgres, asi que Hibernate no
 * necesita conocerlas para insertar una fila valida. `baja_solicitada_en` SI se mapea
 * desde 2026-08-26 (gap #5, baja de cuenta autogestionada).
 *
 * @Data aca es seguro (a diferencia de en domain/): esta entidad no tiene relaciones
 * @ManyToOne/@OneToMany perezosas que @Data pueda romper (CLAUDE.MD §5.4.5) — mismo
 * patron que AccountJpaEntity en thombergs/buckpal.
 *
 * @JdbcTypeCode(SqlTypes.NAMED_ENUM) en los campos enum: sin esto, Hibernate manda el
 * valor como varchar plano y Postgres lo rechaza (las columnas son del tipo enum nativo
 * rol_usuario/estado_usuario, no varchar) — verificado con Testcontainers, no supuesto.
 *
 * <p><b>Por que {@link Persistable} (E-365).</b> Con un id ya puesto y sin {@code @Version}, Spring
 * Data decide que la entidad NO es nueva y hace {@code merge}: si hay una fila con ese id, la pisa. Para
 * un cambio de perfil eso es lo que se quiere; para un alta, no. {@link #nueva} deja que el alta pida un
 * {@code persist} (un INSERT, que falla si el id ya existe) sin cambiar lo que hace {@code save}.
 */
@Entity
@Table(name = "usuarios", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String email;

    private String nombreCompleto;

    /** `usuarios.avatar_url` — URL PERMANENTE del objeto publico, nunca una prefirmada (E-57). */
    private String avatarUrl;

    private String bio;

    private String departamento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RolUsuarioJpa rol;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoUsuarioJpa estado;

    private Instant ultimaActividadEn;

    /** `usuarios.baja_solicitada_en` - baja de cuenta autogestionada (soft-delete diferido,
     * D-XX cuenta con purga por cron). Nullable: la inmensa mayoria de las filas no la tiene. */
    private Instant bajaSolicitadaEn;

    /**
     * Solo en memoria, nunca en la base: {@code true} cuando esta fila es una cuenta que se esta
     * CREANDO ({@code SaveUserPort.registrarNueva}). Por defecto {@code false}, que es lo de siempre.
     */
    @Transient
    private boolean nueva;

    @Override
    public boolean isNew() {
        return nueva;
    }
}
