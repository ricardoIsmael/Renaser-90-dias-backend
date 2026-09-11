package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiasConfigurados;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.PoliticaConfigurada;

import java.util.List;
import java.util.UUID;

public record MentoringPolicyResponse(UUID cohortId, int capacity, String rotation, String timezone,
                                       int transferDay, int inactivityDays, UUID receptionCellId, int version) {

    public static MentoringPolicyResponse from(PoliticaConfigurada politica) {
        return new MentoringPolicyResponse(politica.cohorteId(), politica.capacidadCelula(),
                politica.cadenciaRotacion(), politica.zonaHoraria(), politica.diaTraslado(),
                politica.diasSinActividadAlerta(), politica.celulaRecepcionId(), politica.version());
    }

    /** Los guías que quedaron designados tras el reemplazo. */
    public record ReceptionGuidesResponse(UUID cohortId, UUID receptionCellId, List<UUID> guides) {

        public static ReceptionGuidesResponse from(GuiasConfigurados guias) {
            return new ReceptionGuidesResponse(guias.cohorteId(), guias.celulaRecepcionId(), guias.guias());
        }
    }
}
