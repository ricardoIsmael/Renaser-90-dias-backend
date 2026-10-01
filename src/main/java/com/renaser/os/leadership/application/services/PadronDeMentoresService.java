package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarPadronDeMentoresUseCase;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder;
import org.springframework.stereotype.Service;

/**
 * El padrón de mentores activos con sus indicadores del mes en curso (SDD 002, RL-04/RL-05; D-241).
 *
 * <p>Sin {@code @Transactional} a propósito: cada fuente abre la suya y una que falla no arrastra a las
 * demás (RL-21).
 */
@Service
public class PadronDeMentoresService implements ConsultarPadronDeMentoresUseCase {

    private final AccesoDeLiderazgo acceso;
    private final FichaDeMentorFinder fichaDeMentorFinder;
    private final IndicadoresDeMentores indicadores;
    private final Clock clock;

    PadronDeMentoresService(AccesoDeLiderazgo acceso, FichaDeMentorFinder fichaDeMentorFinder,
                            IndicadoresDeMentores indicadores, Clock clock) {
        this.acceso = acceso;
        this.fichaDeMentorFinder = fichaDeMentorFinder;
        this.indicadores = indicadores;
        this.clock = clock;
    }

    @Override
    public PadronDeMentores padron(UserId actorId) {
        acceso.requireLiderazgoActivo(actorId);
        MesDelReporte mes = MesDelReporte.enCurso(MesDelReporte.ZONA_DEL_PROGRAMA, clock.now());
        IndicadoresDeMentores.Lectura lectura = indicadores.leer(fichaDeMentorFinder.mentoresActivos(), mes);
        MedicionVigente medicion = lectura.medicion();
        return new PadronDeMentores(mes.mes().toString(), mes.zona().getId(), mes.corte(),
                medicion == null ? null : medicion.desde(), medicion == null ? null : medicion.hasta(),
                lectura.indicadores());
    }
}
