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

    /** V112 (spec 009): como suena la alarma ese dia; {@code null} en un campo = como la general. */
    private Boolean alarmaSonido;

    private Boolean alarmaVibracion;

    private Boolean alarmaVoz;

    private Boolean alarmaRepetir;

    /** Una fila con hora y apagado y sin aviso ni alarma propios: la forma de antes de V111. */
    public HorarioSemanalJpaEntity(UUID participanteId, UUID habitoId, Short diaSemana, LocalTime horaDisparo,
                                   boolean activo, LocalTime horaLimite, Instant creadoEn, Instant actualizadoEn) {
        this(participanteId, habitoId, diaSemana, horaDisparo, activo, horaLimite, creadoEn, actualizadoEn,
                null, null, null, null, null, null);
    }
}
