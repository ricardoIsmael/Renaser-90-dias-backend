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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "horarios_habito_por_fecha", schema = "renaser")
@IdClass(HorarioPorFechaPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioPorFechaJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    @Id
    private LocalDate fecha;

    private LocalTime horaDisparo;

    /** V38: false = ese dia el habito NO va. Ver `HorarioPorFecha`. */
    private boolean activo;

    private LocalTime horaLimite;

    /** V131 (spec 028): {@code null} = esa fecha hereda el aviso; false = sin aviso; true = avisa. Las filas de antes nunca son null. */
    private Boolean recordatorioActivo;

    private Short minutosRecordatorio;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** V130: {@code smallint[]}, solo con {@code recordatorioActivo = true}. Null en una fila de antes: vale {@code minutosRecordatorio}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "antelaciones_recordatorio")
    private Short[] antelacionesRecordatorio;

    /** V130 (spec 009 por fecha): como suena la alarma esa fecha; {@code null} en un campo = como el dia de la semana o la general. */
    private Boolean alarmaSonido;

    private Boolean alarmaVibracion;

    private Boolean alarmaVoz;

    private Boolean alarmaRepetir;

    /** La forma de antes de V130: aviso propio con un solo numero de minutos y sin alarma. */
    public HorarioPorFechaJpaEntity(UUID participanteId, UUID habitoId, LocalDate fecha, LocalTime horaDisparo, boolean activo,
                                    LocalTime horaLimite, boolean recordatorioActivo, Short minutosRecordatorio,
                                    Instant creadoEn, Instant actualizadoEn) {
        this(participanteId, habitoId, fecha, horaDisparo, activo, horaLimite, recordatorioActivo, minutosRecordatorio,
                creadoEn, actualizadoEn, null, null, null, null, null);
    }
}
