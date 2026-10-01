package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarFichaDeMentorUseCase;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.UserStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * La ficha de un mentor (SDD 002, RL-06/RL-07/RL-08; D-241): lo mismo que su fila del padrón, su perfil
 * y lo último que el líder le dijo. Ningún aprendiz con nombre.
 */
@Service
public class FichaDeMentorService implements ConsultarFichaDeMentorUseCase {

    static final int ULTIMAS_OBSERVACIONES = 5;

    private final AccesoDeLiderazgo acceso;
    private final IndicadoresDeMentores indicadores;
    private final LeerObservacionesPort observaciones;
    private final Clock clock;

    FichaDeMentorService(AccesoDeLiderazgo acceso, IndicadoresDeMentores indicadores,
                         LeerObservacionesPort observaciones, Clock clock) {
        this.acceso = acceso;
        this.indicadores = indicadores;
        this.observaciones = observaciones;
        this.clock = clock;
    }

    @Override
    public FichaDeMentor ficha(UserId actorId, UserId mentorId) {
        acceso.requireLiderazgoActivo(actorId);
        FichaDeMentorFinder.FichaDeMentor mentor = acceso.requireMentor(mentorId);
        MesDelReporte mes = MesDelReporte.enCurso(MesDelReporte.ZONA_DEL_PROGRAMA, clock.now());
        IndicadoresDeMentores.Lectura lectura = indicadores.leer(List.of(mentor), mes);
        MedicionVigente medicion = lectura.medicion();
        return new FichaDeMentor(lectura.indicadores().getFirst(), mentor.estado() == UserStatus.ACTIVE,
                mentor.perfil(), mes.mes().toString(), mes.zona().getId(), mes.corte(),
                medicion == null ? null : medicion.desde(), medicion == null ? null : medicion.hasta(),
                observaciones.deMentor(mentorId, null, ULTIMAS_OBSERVACIONES));
    }
}
