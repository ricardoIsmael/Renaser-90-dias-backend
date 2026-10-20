package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "horario_semanal_habito", schema = "renaser")
@IdClass(HorarioSemanalPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioSemanalJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    /** ISO-8601: 1 = lunes ... 7 = domingo. Ver V39. */
    @Id
    private Short diaSemana;

    private LocalTime horaDisparo;

    /** V40: false = ese dia el habito NO va, ninguna semana. */
    private boolean activo;

    private LocalTime horaLimite;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** V111 (spec 008): {@code null} = este dia hereda el aviso general; false = sin aviso; true = avisa. */
    private Boolean recordatorioActivo;

    /** V111: {@code smallint[]}, solo con {@code recordatorioActivo = true}. La traduce el adaptador. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "antelaciones_recordatorio")
    private Short[] antelacionesRecordatorio;
}
