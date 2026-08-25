package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla {@code roles_permitidos_curso} (V1__baseline_renaser.sql:969-976) TAL
 * CUAL esta en el baseline: {@code rol_id smallint REFERENCES roles(id)}, NO
 * un enum nativo — decision explicita del dueño del proyecto (AC-05, ver
 * `docs/MODULO_ACADEMY.md` §5): el esquema de base de datos es inmutable en
 * esta fase, sin excepciones. La traduccion {@code rol_id} ↔
 * {@code users.api.UserRole} vive en {@link RolesCatalogo}, leyendo
 * {@code renaser.roles} (id ↔ clave) — nunca en el dominio.
 */
@Entity
@Table(name = "roles_permitidos_curso", schema = "renaser")
@IdClass(RolPermitidoCursoId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolPermitidoCursoJpaEntity {

    @Id
    private String cursoId;

    @Id
    private Short rolId;
}
