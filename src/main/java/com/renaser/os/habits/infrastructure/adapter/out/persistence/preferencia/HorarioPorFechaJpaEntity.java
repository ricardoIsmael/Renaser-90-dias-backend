package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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

    private boolean recordatorioActivo;

    private Short minutosRecordatorio;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
