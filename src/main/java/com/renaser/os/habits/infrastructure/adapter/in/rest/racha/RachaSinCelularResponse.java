package com.renaser.os.habits.infrastructure.adapter.in.rest.racha;

import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;

import java.time.Instant;

public record RachaSinCelularResponse(String id, Instant iniciadaEn, int horasObjetivo, String estado,
                                       int minutosTranscurridos, int hitoAlcanzado, Instant plazoCierre) {

    public static RachaSinCelularResponse from(RachaSinCelular r, Instant ahora, int extensionHoras) {
        int minutos = r.minutosTranscurridos(ahora);
        return new RachaSinCelularResponse(r.id().toString(), r.iniciadaEn(), r.horasObjetivo(), r.estado().name(),
                minutos, RachaSinCelular.hitoAlcanzado(minutos), r.plazoCierre(extensionHoras));
    }
}
