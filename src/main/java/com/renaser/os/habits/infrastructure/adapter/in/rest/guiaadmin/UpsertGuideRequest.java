package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.domain.model.guia.ContenidoGuia;

/** Espejo de {@code UpsertGuideInput} (`habitsAdmin.ts`). */
public record UpsertGuideRequest(int startDay, Integer endDay, String whatToDo, String howToDo, String science,
                                  String renaser, String alchemy, String outcomes, String mantraTitle,
                                  String mantraLead, String mantraBody, String sourceRef, boolean closePrevious) {

    public ContenidoGuia toContenido() {
        return new ContenidoGuia(whatToDo, howToDo, science, renaser, alchemy, outcomes, mantraTitle, mantraLead,
                mantraBody, sourceRef);
    }
}
