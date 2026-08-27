package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.application.ports.in.guiaadmin.GuiaConAdjuntos;

import java.util.List;

/** Espejo de {@code HabitGuide} (`habitsAdmin.ts`) — adjuntos anidados, ver su comentario "Vienen anidados". */
public record HabitGuideResponse(String id, int startDay, Integer endDay, String whatToDo, String howToDo,
                                  String science, String renaser, String alchemy, String outcomes,
                                  String mantraTitle, String mantraLead, String mantraBody, String sourceRef,
                                  List<HabitGuideAttachmentResponse> attachments) {

    public static HabitGuideResponse from(GuiaConAdjuntos g) {
        var guia = g.guia();
        return new HabitGuideResponse(guia.id().value().toString(), guia.diaInicio(), guia.diaFin(),
                guia.queHacer(), guia.comoHacerlo(), guia.ciencia(), guia.renaser(), guia.alquimia(),
                guia.resultados(), guia.mantraTitulo(), guia.mantraIntro(), guia.mantraCuerpo(),
                guia.referenciaFuente(), g.adjuntos().stream().map(HabitGuideAttachmentResponse::from).toList());
    }
}
