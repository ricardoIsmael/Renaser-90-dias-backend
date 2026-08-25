package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** {@code dia_semana}: 0=domingo..6=sabado (convencion del baseline) — NO la convencion
 * ISO (1=lunes..7=domingo) que usa el dominio ({@link com.renaser.os.calendar.domain.model.evento.Recurrencia}).
 * La traduccion vive en {@code EventoPersistenceMapper}. */
@Entity
@Table(name = "dias_semana_recurrencia", schema = "renaser")
@IdClass(DiaSemanaRecurrenciaId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiaSemanaRecurrenciaJpaEntity {

    @Id
    private UUID eventoId;

    @Id
    private Short diaSemana;
}
