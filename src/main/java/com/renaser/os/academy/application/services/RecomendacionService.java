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

    /**
     * <b>C-1 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b> este
     * método YA NO es {@code @Transactional}. Antes envolvía en una sola transacción la
     * llamada a {@link RecomendarClasePort} (IA) y el guardado de la recomendación,
     * reteniendo una conexión de Hikari mientras esperaba a la IA — con varios aprendices
     * pidiendo su recomendación diaria a la vez (el disparador más directo de los cuatro:
     * este método lo llama un hilo de request HTTP, no un scheduler ni un @Async acotado),
     * agotaba el pool para toda la API. Los puertos de lectura/escritura ya abren su propia
     * transacción corta vía Spring Data JPA; la llamada a la IA queda afuera de cualquiera.
     */
    @Override
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
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("Solo un aprendiz recibe recomendaciones de Academia Adaptativa");
        }
        return progreso;
    }
}
