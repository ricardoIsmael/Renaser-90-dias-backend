package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.application.ports.out.recomendacion.LoadRecomendacionPort;
import com.renaser.os.academy.application.ports.out.recomendacion.RecomendarClasePort;
import com.renaser.os.academy.application.ports.out.recomendacion.RecomendarClasePort.ClaseRecomendada;
import com.renaser.os.academy.application.ports.out.recomendacion.SaveRecomendacionPort;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Academia Adaptativa — recomendacion diaria (cache-first). Espejo PARCIAL de
 * `getClassRecommendation` (RenaserBack `academia-adaptativa/service.ts`): la
 * generacion real via Gemini (radar de energia/animo + lecciones disponibles)
 * es Ola 5, ver {@link RecomendarClasePort}. Con el adapter NoOp de hoy, una
 * recomendacion sin cache previo siempre resuelve {@code NoDisponible}.
 */
@Service
public class RecomendacionService implements ConsultarRecomendacionDiariaUseCase {

    /** Espejo del default de columna `participantes_programa.timezone` (V1__baseline_renaser.sql:272). */
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final LoadRecomendacionPort loadRecomendacionPort;
    private final SaveRecomendacionPort saveRecomendacionPort;
    private final RecomendarClasePort recomendarClasePort;
    private final LoadLeccionPort loadLeccionPort;
    private final LoadCursoPort loadCursoPort;
    private final ConsultarProgresoParticipanteAcademyPort progresoPort;
    private final Clock clock;

    public RecomendacionService(LoadRecomendacionPort loadRecomendacionPort,
                                 SaveRecomendacionPort saveRecomendacionPort, RecomendarClasePort recomendarClasePort,
                                 LoadLeccionPort loadLeccionPort, LoadCursoPort loadCursoPort,
                                 ConsultarProgresoParticipanteAcademyPort progresoPort, Clock clock) {
        this.loadRecomendacionPort = loadRecomendacionPort;
        this.saveRecomendacionPort = saveRecomendacionPort;
        this.recomendarClasePort = recomendarClasePort;
        this.loadLeccionPort = loadLeccionPort;
        this.loadCursoPort = loadCursoPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RecomendacionDiaria recomendacion(UserId actorId) {
        ProgresoParticipanteAcademy progreso = requireProgresoTrainee(actorId);
        ZoneId zona = progreso.zona() == null ? ZONA_POR_DEFECTO : progreso.zona();
        LocalDate hoy = clock.now().atZone(zona).toLocalDate();

        Optional<RecomendacionAcademia> cache = loadRecomendacionPort.delDia(actorId, hoy);
        if (cache.isPresent()) {
            return aDisponible(cache.get());
        }

        Optional<ClaseRecomendada> generada = recomendarClasePort.recomendar(actorId);
        if (generada.isEmpty()) {
            return new NoDisponible("sin_recomendacion_disponible");
        }

        RecomendacionAcademia guardada = saveRecomendacionPort.guardar(
                new RecomendacionAcademia(actorId, hoy, generada.get().leccionId(), generada.get().motivo(),
                        clock.now()));
        return aDisponible(guardada);
    }

    private RecomendacionDiaria aDisponible(RecomendacionAcademia recomendacion) {
        Leccion leccion = loadLeccionPort.byId(recomendacion.leccionId())
                .orElseThrow(() -> new NoSuchElementException("Leccion no encontrada: " + recomendacion.leccionId()));
        Curso curso = loadCursoPort.byId(leccion.cursoId())
                .orElseThrow(() -> new NoSuchElementException("Curso no encontrado: " + leccion.cursoId()));
        return new Disponible(leccion.id(), leccion.titulo(), curso.id(), curso.titulo(), recomendacion.motivo());
    }

    private ProgresoParticipanteAcademy requireProgresoTrainee(UserId actorId) {
        ProgresoParticipanteAcademy progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz recibe recomendaciones de Academia Adaptativa");
        }
        return progreso;
    }
}
