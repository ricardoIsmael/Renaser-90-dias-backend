package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin;

import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase.AudioterapiaActualizada;

public record AudioTherapyResponse(int week, String title, int durationDays) {

    public static AudioTherapyResponse from(AudioterapiaActualizada audioterapia) {
        return new AudioTherapyResponse(audioterapia.semana(), audioterapia.titulo(), audioterapia.duracionDias());
    }
}
