package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "historial_cambios_horario", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCambioHorarioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID participanteId;

    private UUID habitoId;

    private LocalDate cambiadoEl;

    private String accion;

    private LocalTime horaDisparo;

    private LocalTime horaLimite;

    private Instant creadoEn;
}
