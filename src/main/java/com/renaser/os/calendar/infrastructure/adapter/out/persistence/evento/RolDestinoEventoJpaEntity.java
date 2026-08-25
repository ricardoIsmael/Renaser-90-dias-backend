package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Tabla {@code roles_destino_evento} TAL CUAL esta en el baseline:
 * {@code rol_id smallint REFERENCES roles(id)}, NO un enum nativo — decision del dueño del
 * proyecto: el esquema de base de datos es inmutable en esta fase (ver CL-x,
 * docs/MODULO_CALENDAR.md §5), mismo criterio que ya aplico `academy` para
 * {@code roles_permitidos_curso} (AC-01). La traduccion {@code rol_id} <-> {@link
 * com.renaser.os.calendar.domain.model.evento.RolUsuario} vive en {@link RolesCatalogoCache}.
 */
@Entity
@Table(name = "roles_destino_evento", schema = "renaser")
@IdClass(RolDestinoEventoId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolDestinoEventoJpaEntity {

    @Id
    private UUID eventoId;

    @Id
    private Short rolId;
}
