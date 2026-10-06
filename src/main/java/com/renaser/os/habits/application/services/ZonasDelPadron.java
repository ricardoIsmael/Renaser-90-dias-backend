package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarZonasDeParticipantesPort;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;

/**
 * La zona de cada participante para los barridos horarios (E-534, E-556, E-557): UNA consulta por lote, y de a uno solo
 * para quien no vino en el lote. Es la lectura que {@code ExpiracionDeRegistrosService} hacia por su cuenta, para que
 * los tres barridos que deciden por el dia local de cada persona la lean igual.
 */
@Service
class ZonasDelPadron {

    private static final Logger log = LoggerFactory.getLogger(ZonasDelPadron.class);

    private final ConsultarZonasDeParticipantesPort zonasPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;

    ZonasDelPadron(ConsultarZonasDeParticipantesPort zonasPort, ConsultarProgresoParticipanteHabitsPort progresoPort) {
        this.zonasPort = zonasPort;
        this.progresoPort = progresoPort;
    }

    /**
     * Las zonas de un lote en UNA consulta. Si falla —una zona corrupta revienta el lote entero al mapearse— devuelve
     * vacio y {@link #de} pregunta de a uno: asi falla solo quien la tiene rota, no el lote.
     */
    Map<UserId, ZoneId> leerLote(Collection<UserId> lote) {
        if (lote.isEmpty()) {
            return Map.of();
        }
        try {
            return zonasPort.deProgramasActivados(lote);
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudieron leer en lote las zonas de {} participante(s); se leen de a uno: {}",
                    lote.size(), ex.toString());
            return Map.of();
        }
    }

    /**
     * La zona de esta persona: la del lote si vino; si no (no activo su programa: la excepcion, no la regla), se lee
     * sola. Una zona rota lanza y la atrapa quien itera, por participante.
     */
    ZoneId de(UserId participante, Map<UserId, ZoneId> lote) {
        ZoneId delLote = lote.get(participante);
        if (delLote != null) {
            return delLote;
        }
        return progresoPort.deParticipante(participante)
                .map(progreso -> ZoneId.of(progreso.timezone()))
                .orElse(ExpiracionDeRegistrosService.ZONA_POR_DEFECTO);
    }
}
