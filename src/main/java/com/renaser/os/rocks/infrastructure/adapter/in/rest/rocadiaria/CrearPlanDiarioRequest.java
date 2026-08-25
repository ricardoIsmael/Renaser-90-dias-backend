package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CrearPlanDiarioRequest(@NotNull LocalDate fecha, @NotEmpty List<@Valid ItemRocaDiariaRequest> rocas) {

    public record ItemRocaDiariaRequest(@NotBlank String eje, int posicion, @NotBlank String titulo, String descripcion,
                                         int puntajeImpacto, boolean esDelegable, LocalTime horaInicio,
                                         LocalTime horaFin) {
    }
}
