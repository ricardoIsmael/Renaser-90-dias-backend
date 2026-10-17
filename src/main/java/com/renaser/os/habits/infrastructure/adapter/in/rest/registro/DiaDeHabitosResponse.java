package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.DiaDeHabitos;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fecha y cierre del mismo snapshot; no dependen de la zona del dispositivo ni de HTTP Date. */
public record DiaDeHabitosResponse(LocalDate fechaLocal, String zonaHoraria, Instant cierraEn,
                                    Instant ahoraServidor, List<RegistroHabitoConCatalogoResponse> tracks) {
    public static DiaDeHabitosResponse from(DiaDeHabitos dia) {
        return new DiaDeHabitosResponse(dia.fechaLocal(), dia.zonaHoraria().getId(), dia.cierraEn(),
                dia.ahoraServidor(), dia.tracks().stream().map(RegistroHabitoConCatalogoResponse::from).toList());
    }
}
