package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CrearPlanDiarioRequest(@NotNull LocalDate fecha, @NotEmpty List<@Valid ItemRocaDiariaRequest> rocas) {

    /**
     * @param acciones con que se logra ese objetivo del dia: hasta tres, opcionales. Nuevo en la
     *                 V61 — antes estas acciones se escribian el domingo colgando de la semana.
     *                 Ausente o vacia deja el objetivo sin desglose, que es valido.
     */
    public record ItemRocaDiariaRequest(@NotBlank String eje, int posicion, @NotBlank String titulo, String descripcion,
                                         int puntajeImpacto, boolean esDelegable, LocalTime horaInicio,
                                         LocalTime horaFin, @Size(max = 3) List<String> acciones) {
    }
}
