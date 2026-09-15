package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultaDeAcompanante;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultarCodigoRenaserDeAlumnoUseCase;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultarHabitosDeAlumnoUseCase;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.VistaHabitosDeAprendiz;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Lo que un acompañante (mentor o guia) puede leer de UN alumno suyo: su configuracion de habitos
 * y su Codigo Renaser. Complementa a {@code mentoring.SeguimientoService}, que ya servia el
 * CUMPLIMIENTO semanal — el mentor veia que cumplio y que no, pero no que habitos tiene ni a que
 * hora, que es lo primero que se mira antes de escribirle.
 *
 * <p>Vive en {@code habits} y no en {@code mentoring} porque los dos datos son de este modulo: asi
 * las dos puertas (la de admin y esta) devuelven la MISMA vista armada por el mismo codigo, sin
 * tener que exponer media docena de records nuevos en {@code habits.api} que despues se
 * desincronizan. {@code habits} ya dependia de {@code community.api} (ver {@code RegistroService}),
 * asi que esto no agrega ninguna arista entre modulos.
 */
@Service
public class AcompanamientoDeAlumnoService
        implements ConsultarHabitosDeAlumnoUseCase, ConsultarCodigoRenaserDeAlumnoUseCase {

    private final AcompanamientoFinder acompanamientoFinder;
    private final HabitosDeAprendizAdminService habitosDeAprendiz;
    private final LoadRegistroRadarPort loadRadarPort;
    private final Clock clock;

    AcompanamientoDeAlumnoService(AcompanamientoFinder acompanamientoFinder,
                                   HabitosDeAprendizAdminService habitosDeAprendiz,
                                   LoadRegistroRadarPort loadRadarPort, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.habitosDeAprendiz = habitosDeAprendiz;
        this.loadRadarPort = loadRadarPort;
        this.clock = clock;
    }

    @Override
    public VistaHabitosDeAprendiz habitosDeAlumno(ConsultaDeAcompanante consulta) {
        requireAcompananteVigente(consulta);
        ProgresoParticipanteHabits progreso = habitosDeAprendiz.requireAprendiz(consulta.alumnoId());
        return habitosDeAprendiz.vistaDe(consulta.alumnoId(), progreso);
    }

    @Override
    public HistorialRadarPage codigoRenaserDeAlumno(ConsultaDeAcompanante consulta, Instant cursor,
                                                     int tamanoPagina) {
        requireAcompananteVigente(consulta);
        List<RegistroRadar> pagina = loadRadarPort.historialDeParticipante(consulta.alumnoId(), cursor, tamanoPagina);
        return new HistorialRadarPage(pagina, RadarService.siguienteCursor(pagina, tamanoPagina));
    }

    /**
     * Las dos comprobaciones, copiadas a proposito de {@code mentoring.SeguimientoService}.
     *
     * <p>La segunda es la que no es obvia: sin ella un mentor legitimo podria leer a CUALQUIER
     * participante pasando el id de su propio grupo. Y {@code acompanaVigente} es vigente, no
     * "alguna vez": un exmentor con el token todavia valido recibe 403.
     *
     * <p>Copiadas y no compartidas: un guard que sirve a dos autorizaciones distintas es como se
     * abren los agujeros que despues nadie encuentra (ARF-15). Son seis lineas; la alternativa es
     * un punto unico que alguien parametriza el dia que necesite una tercera variante.
     */
    private void requireAcompananteVigente(ConsultaDeAcompanante consulta) {
        Instant ahora = clock.now();
        if (!acompanamientoFinder.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        if (!acompanamientoFinder.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompanas");
        }
    }
}
