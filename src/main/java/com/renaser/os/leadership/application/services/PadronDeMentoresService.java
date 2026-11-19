package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarPadronDeMentoresUseCase;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * El padrón de mentores con sus indicadores del mes en curso (SDD 002, RL-04/RL-05; D-241).
 *
 * <p>Spec 056 FR-004: los de cuenta suspendida van al final y marcados en {@code suspendidos}, en vez de desaparecer: la app los muestra
 * apagados. Sus indicadores se leen igual que los de un activo (es la misma lectura que ya hace su ficha); el reporte del mes, «Por resolver» y
 * los documentos siguen solo con los activos.
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
        List<FichaDeMentor> activos = acceso.mentoresDelAlcance(actorId);
        List<FichaDeMentor> suspendidos = acceso.mentoresSuspendidosDelAlcance(actorId);
        List<FichaDeMentor> todos = Stream.concat(activos.stream(), suspendidos.stream()).toList();
        IndicadoresDeMentores.Lectura lectura = indicadores.leer(todos, mes, acceso.alcanceDe(actorId));
        MedicionVigente medicion = lectura.medicion();
        Set<UserId> idsSuspendidos = suspendidos.stream().map(FichaDeMentor::id).collect(Collectors.toUnmodifiableSet());
        return new PadronDeMentores(mes.mes().toString(), mes.zona().getId(), mes.corte(),
                medicion == null ? null : medicion.desde(), medicion == null ? null : medicion.hasta(),
                lectura.indicadores(), idsSuspendidos);
    }
}
