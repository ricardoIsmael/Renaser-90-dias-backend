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
@Table(name = "preferencias_horario", schema = "renaser")
@IdClass(PreferenciaHorarioPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciaHorarioJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    private LocalTime horaDisparo;

    private LocalTime horaLimite;

    private boolean recordatorioActivo;

    private Short minutosRecordatorio;

    /** V81 (D-217): {@code smallint[]}; {@code null} = no se conocen. La traduce el mapper. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "antelaciones_recordatorio")
    private Short[] antelacionesRecordatorio;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** V112 (spec 009): como suena la alarma del habito; {@code null} en un campo = lo de hoy. */
    private Boolean alarmaSonido;

    private Boolean alarmaVibracion;

    private Boolean alarmaVoz;

    private Boolean alarmaRepetir;
}
