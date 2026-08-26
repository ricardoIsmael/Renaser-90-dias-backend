package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "cambios_horario_pendientes", schema = "renaser")
@IdClass(PreferenciaHorarioPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioHorarioPendienteJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    private LocalTime horaDisparo;

    private LocalTime horaLimite;

    private Boolean recordatorioActivo;

    private Short minutosRecordatorio;

    private LocalDate fechaEfectiva;

    private Instant creadoEn;
}
