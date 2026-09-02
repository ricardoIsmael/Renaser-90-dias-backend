package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarSeccionesCursoUseCase;
import com.renaser.os.academy.application.ports.in.leccion.CompletarLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarMotivoBloqueoLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.DescompletarLeccionUseCase;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadRecursoLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.application.ports.out.progreso.LoadProgresoLeccionPort;
import com.renaser.os.academy.application.ports.out.progreso.SaveProgresoLeccionPort;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Catalogo de cursos/secciones/lecciones: acceso, arbol con progresion por
 * dia, y motivo de bloqueo. Espejo deliberado de un unico archivo del repo
 * viejo (`cursos/service.ts`) que agrupaba exactamente estos casos de uso —
 * se mantiene junto aca por la misma razon: comparten el mismo calculo de
 * "que ve este actor" y separarlos en clases distintas hubiera duplicado esa
 * logica o forzado un puerto intermedio artificial.
 */
@Service
public class CatalogoAcademyService implements ConsultarMisCursosUseCase, ConsultarCursoDetalleUseCase,
        ConsultarSeccionesCursoUseCase, ConsultarMotivoBloqueoCursoUseCase, ConsultarLeccionUseCase,
        ConsultarMotivoBloqueoLeccionUseCase, CompletarLeccionUseCase, ConsultarCursosBloqueadosUseCase,
        DescompletarLeccionUseCase {

    /** Mismo TTL que `firmarPortada`/`SIGNED_TTL` del repo viejo (repository.ts:49). */
    private static final Duration TTL_PORTADA = Duration.ofHours(1);

    private final LoadCursoPort loadCursoPort;
    private final LoadSeccionCursoPort loadSeccionCursoPort;
    private final LoadLeccionPort loadLeccionPort;
    private final LoadRecursoLeccionPort loadRecursoLeccionPort;
    private final LoadProgresoLeccionPort loadProgresoLeccionPort;
    private final SaveProgresoLeccionPort saveProgresoLeccionPort;
    private final ConsultarProgresoParticipanteAcademyPort progresoPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;

    public CatalogoAcademyService(LoadCursoPort loadCursoPort, LoadSeccionCursoPort loadSeccionCursoPort,
                                   LoadLeccionPort loadLeccionPort, LoadRecursoLeccionPort loadRecursoLeccionPort,
                                   LoadProgresoLeccionPort loadProgresoLeccionPort,
                                   SaveProgresoLeccionPort saveProgresoLeccionPort,
                                   ConsultarProgresoParticipanteAcademyPort progresoPort,
                                   AlmacenamientoPort almacenamientoPort, Clock clock) {
        this.loadCursoPort = loadCursoPort;
        this.loadSeccionCursoPort = loadSeccionCursoPort;
        this.loadLeccionPort = loadLeccionPort;
        this.loadRecursoLeccionPort = loadRecursoLeccionPort;
        this.loadProgresoLeccionPort = loadProgresoLeccionPort;
        this.saveProgresoLeccionPort = saveProgresoLeccionPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
    }

    @Override
    public List<CursoConProgreso> misCursos(UserId actorId) {
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);

        List<Curso> accesibles = loadCursoPort.listarTodos().stream()
                .filter(c -> c.visibleEnCatalogoPara(rol, programDay))
                .toList();

        Map<CursoId, Integer> totalPorCurso = loadLeccionPort.contarTotalPorCurso();
        Map<CursoId, Integer> completadasPorCurso = loadProgresoLeccionPort.completadasPorCurso(actorId);

        return accesibles.stream()
                .map(c -> new CursoConProgreso(c,
                        new ProgresoCurso(c.id(), totalPorCurso.getOrDefault(c.id(), 0),
                                completadasPorCurso.getOrDefault(c.id(), 0), null),
                        firmarPortada(c.portadaRuta())))
                .toList();
    }

    /**
     * AC-15: solo TRAINEE puede tener cursos "bloqueados por dia" — el resto
     * de roles no tiene dia de programa, asi que la lista es vacia (nunca un
     * error). El calculo de cada fila usa {@link Curso#bloqueadoPorDiaPara},
     * la inversa exacta de {@code visibleEnCatalogoPara} que nunca revela
     * bloqueo por rol/publicacion/acceso.
     */
    @Override
    public List<CursoBloqueado> cursosBloqueados(UserId actorId) {
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        if (rol != UserRole.TRAINEE) {
            return List.of();
        }
        Integer programDay = programDayDe(rol, progreso);
        int actual = programDay == null ? Curso.DIA_PROGRAMA_INICIAL : programDay;

        return loadCursoPort.listarTodos().stream()
                .filter(c -> c.bloqueadoPorDiaPara(rol, programDay))
                .map(c -> new CursoBloqueado(c, c.diaDesbloqueo(), actual, firmarPortada(c.portadaRuta())))
                .toList();
    }

    @Override
    public CursoDetalle detalle(UserId actorId, CursoId cursoId) {
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);
        Curso curso = requireCursoAccesible(cursoId, rol, programDay);
        ContenidoCurso contenido = construirContenido(cursoId, rol, programDay);
        return new CursoDetalle(curso, contenido, firmarPortada(curso.portadaRuta()));
    }

    @Override
    public List<SeccionConLecciones> secciones(UserId actorId, CursoId cursoId) {
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);
        requireCursoAccesible(cursoId, rol, programDay);
        return construirContenido(cursoId, rol, programDay).secciones();
    }

    @Override
    public MotivoBloqueoCurso motivo(UserId actorId, CursoId cursoId) {
        Optional<Curso> cursoOpt = loadCursoPort.byId(cursoId);
        if (cursoOpt.isEmpty()) {
            return new NoBloqueado();
        }
        Curso curso = cursoOpt.get();
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);

        if (curso.visibleEnCatalogoPara(rol, programDay)) {
            return new NoBloqueado();
        }
        if (!curso.rolesPermitidos().isEmpty() && !curso.rolesPermitidos().contains(rol)) {
            return new NoBloqueado();
        }
        if (curso.diaDesbloqueo() == null || rol != UserRole.TRAINEE) {
            return new NoBloqueado();
        }
        int actual = programDay == null ? Curso.DIA_PROGRAMA_INICIAL : programDay;
        return new BloqueadoPorDia(curso.titulo(), curso.diaDesbloqueo(), actual);
    }

    @Override
    public MotivoBloqueoCurso motivo(UserId actorId, LeccionId leccionId) {
        Optional<Leccion> leccionOpt = loadLeccionPort.byId(leccionId);
        if (leccionOpt.isEmpty()) {
            return new NoBloqueado();
        }
        Leccion leccion = leccionOpt.get();

        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);

        Optional<Curso> cursoOpt = loadCursoPort.byId(leccion.cursoId());
        if (cursoOpt.isEmpty() || !cursoOpt.get().visibleEnCatalogoPara(rol, programDay)) {
            return motivo(actorId, leccion.cursoId());
        }

        Integer diaSeccion = leccion.seccionId() == null ? null
                : loadSeccionCursoPort.byId(leccion.seccionId()).map(SeccionCurso::diaDesbloqueo).orElse(null);
        int actual = programDay == null ? Curso.DIA_PROGRAMA_INICIAL : programDay;
        if (diaSeccion != null && rol == UserRole.TRAINEE && actual < diaSeccion) {
            return new BloqueadoPorDia(leccion.titulo(), diaSeccion, actual);
        }
        return new NoBloqueado();
    }

    @Override
    public LeccionDetalle leccion(UserId actorId, LeccionId leccionId) {
        Leccion leccion = requireLeccion(leccionId);
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);
        requireCursoAccesible(leccion.cursoId(), rol, programDay);
        requireSeccionVisible(leccion, rol, programDay);

        List<RecursoLeccion> recursos = loadRecursoLeccionPort.porLeccion(leccionId);
        return new LeccionDetalle(leccion, recursos);
    }

    @Override
    @Transactional
    public ProgresoLeccion completar(UserId actorId, LeccionId leccionId) {
        Leccion leccion = requireLeccion(leccionId);
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);
        requireCursoAccesible(leccion.cursoId(), rol, programDay);
        requireSeccionVisible(leccion, rol, programDay);

        return saveProgresoLeccionPort.marcarCompletada(new ProgresoLeccion(actorId, leccionId, clock.now()));
    }

    /** AC-16: inverso simetrico de {@link #completar}, misma exigencia de acceso vigente. */
    @Override
    @Transactional
    public void descompletar(UserId actorId, LeccionId leccionId) {
        Leccion leccion = requireLeccion(leccionId);
        ProgresoParticipanteAcademy progreso = requireProgreso(actorId);
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = programDayDe(rol, progreso);
        requireCursoAccesible(leccion.cursoId(), rol, programDay);
        requireSeccionVisible(leccion, rol, programDay);

        saveProgresoLeccionPort.desmarcarCompletada(actorId, leccionId);
    }

    private ContenidoCurso construirContenido(CursoId cursoId, UserRole rol, Integer programDay) {
        List<SeccionCurso> secciones = loadSeccionCursoPort.porCurso(cursoId);
        List<Leccion> lecciones = loadLeccionPort.porCurso(cursoId);
        Map<LeccionId, Integer> recursosCount = loadRecursoLeccionPort
                .contarPorLecciones(lecciones.stream().map(Leccion::id).toList());

        Map<SeccionCursoId, List<Leccion>> porSeccion = lecciones.stream()
                .filter(l -> l.seccionId() != null)
                .collect(Collectors.groupingBy(Leccion::seccionId));
        List<Leccion> sueltas = lecciones.stream().filter(l -> l.seccionId() == null).toList();

        Integer actual = rol == UserRole.TRAINEE
                ? (programDay == null ? Curso.DIA_PROGRAMA_INICIAL : programDay)
                : null;

        List<LeccionConProgresion> sueltasDto = sueltas.stream()
                .map(l -> new LeccionConProgresion(l, recursosCount.getOrDefault(l.id(), 0), null, false, actual, 0))
                .toList();

        List<SeccionConLecciones> seccionesDto = secciones.stream()
                .map(s -> aSeccionConLecciones(s, porSeccion.getOrDefault(s.id(), List.of()), recursosCount, rol,
                        programDay, actual))
                .toList();

        return new ContenidoCurso(sueltasDto, seccionesDto);
    }

    private SeccionConLecciones aSeccionConLecciones(SeccionCurso seccion, List<Leccion> deLaSeccion,
                                                       Map<LeccionId, Integer> recursosCount, UserRole rol,
                                                       Integer programDay, Integer actual) {
        boolean bloqueada = !seccion.visibleEnCatalogoPara(rol, programDay);
        int diasFaltantes = bloqueada ? seccion.diaDesbloqueo() - actual : 0;
        List<LeccionConProgresion> leccionesDto = deLaSeccion.stream()
                .map(l -> new LeccionConProgresion(l, recursosCount.getOrDefault(l.id(), 0), seccion.diaDesbloqueo(),
                        bloqueada, actual, diasFaltantes))
                .toList();
        return new SeccionConLecciones(seccion, bloqueada, actual, diasFaltantes, leccionesDto);
    }

    private void requireSeccionVisible(Leccion leccion, UserRole rol, Integer programDay) {
        if (leccion.seccionId() == null) {
            return;
        }
        boolean visible = loadSeccionCursoPort.byId(leccion.seccionId())
                .map(s -> s.visibleEnCatalogoPara(rol, programDay))
                .orElse(true);
        if (!visible) {
            throw new NotAuthorizedException("Esta sesion todavia no esta disponible");
        }
    }

    private Curso requireCursoAccesible(CursoId cursoId, UserRole rol, Integer programDay) {
        Curso curso = loadCursoPort.byId(cursoId)
                .orElseThrow(() -> new NoSuchElementException("Curso no encontrado: " + cursoId));
        if (!curso.visibleEnCatalogoPara(rol, programDay)) {
            throw new NotAuthorizedException("No tienes acceso a este curso");
        }
        return curso;
    }

    private Leccion requireLeccion(LeccionId leccionId) {
        return loadLeccionPort.byId(leccionId)
                .orElseThrow(() -> new NoSuchElementException("Leccion no encontrada: " + leccionId));
    }

    /** `programDay` solo tiene sentido para TRAINEE — el resto de roles no tienen dia de programa (CLAUDE.MD sec. 5.3.2). */
    private static Integer programDayDe(UserRole rol, ProgresoParticipanteAcademy progreso) {
        return rol == UserRole.TRAINEE ? progreso.diaPrograma() : null;
    }

    private String firmarPortada(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        if (ruta.matches("(?i)^https?://.*")) {
            return ruta;
        }
        return almacenamientoPort.firmarLectura(ruta, TTL_PORTADA).toString();
    }

    /** SUSPENDIDO -> 403. Ausencia de fila de usuario -> 404 (no debería pasar con un actor autenticado). */
    private ProgresoParticipanteAcademy requireProgreso(UserId actorId) {
        ProgresoParticipanteAcademy progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }

    /** Espejo de `RolUsuarioJpa`/mapeos analogos en `rocks`/`phasecontracts`. */
    private static UserRole aUserRole(RolParticipante rol) {
        return switch (rol) {
            case ALCHEMIST -> UserRole.ALCHEMIST;
            case ADMIN -> UserRole.ADMIN;
            case MENTOR_LEAD -> UserRole.MENTOR_LEAD;
            case MENTOR -> UserRole.MENTOR;
            case TRAINEE -> UserRole.TRAINEE;
        };
    }
}
