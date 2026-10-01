package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder.PerfilDeMentor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * La ficha de UN mentor (SDD 002, RL-06; D-241): sus indicadores, su perfil y lo último que el líder
 * le dijo. Un id que no es de un mentor responde 404, no una ficha vacía (RL-08).
 */
public interface ConsultarFichaDeMentorUseCase {

    FichaDeMentor ficha(UserId actorId, UserId mentorId);

    /**
     * @param cuentaActiva false si la cuenta del mentor está suspendida o inactiva
     * @param perfil       null si todavía no tiene perfil de mentor
     */
    record FichaDeMentor(IndicadoresDeMentor indicadores, boolean cuentaActiva, PerfilDeMentor perfil, String mes,
                         String zona, Instant corteEn, LocalDate semaforoDesde, LocalDate semaforoHasta,
                         List<ObservacionDeMentor> ultimasObservaciones) {
    }
}
