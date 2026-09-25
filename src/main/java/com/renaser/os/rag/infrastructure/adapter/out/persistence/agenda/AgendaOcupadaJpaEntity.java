package com.renaser.os.rag.infrastructure.adapter.out.persistence.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Una fila de {@code agenda_ocupada} (V64): un tramo ocupado de un dia de la semana. */
@Entity
@Table(name = "agenda_ocupada", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgendaOcupadaJpaEntity {

    @Id
    private UUID id;

    @Column(name = "participante_id")
    private UUID participanteId;

    /** ISO-8601: 1 = lunes ... 7 = domingo. */
    @Column(name = "dia_semana")
    private short diaSemana;

    @Column(name = "desde_minuto")
    private short desdeMinuto;

    @Column(name = "hasta_minuto")
    private short hastaMinuto;
}
