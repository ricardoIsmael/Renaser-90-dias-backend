package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.leccion.CompletarLeccionUseCase;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CompletarClaseDiariaHabitoCommand;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.RegistroCompletado;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resuelve y completa la Clase Diaria del dia real del aprendiz. La resolucion es espejo de
 * `resolveAvailableClass`/`findClaseDiaria` (RenaserBack `clase-diaria/service.ts` +
 * `repository.ts:168-233`); completarla ademas cierra el habito diario y otorga puntos —
 * ver javadoc de {@link CompletarClaseDiariaUseCase} y `docs/MODULO_ACADEMY.md` §6.
 */
@Service
public class ClaseDiariaService implements ConsultarClaseDiariaUseCase, CompletarClaseDiariaUseCase {

    /** Espejo de `ULTIMO_DIA_CON_CLASE` (clase-diaria/repository.ts:118). */
    private static final int ULTIMO_DIA_CON_CLASE = 90;
    /** Espejo de `esLeccionClase` (clase-diaria/repository.ts:150-152). */
    private static final Pattern PATRON_LECCION_CLASE = Pattern.compile("\\bclase\\b", Pattern.CASE_INSENSITIVE);

    private final LoadCursoPort loadCursoPort;
    private final LoadSeccionCursoPort loadSeccionCursoPort;
    private final LoadLeccionPort loadLeccionPort;
    private final ConsultarProgresoParticipanteAcademyPort progresoPort;
    private final CompletarClaseDiariaHabitoUseCase completarHabitoUseCase;
    private final CompletarLeccionUseCase completarLeccionUseCase;

    public ClaseDiariaService(LoadCursoPort loadCursoPort, LoadSeccionCursoPort loadSeccionCursoPort,
                               LoadLeccionPort loadLeccionPort, ConsultarProgresoParticipanteAcademyPort progresoPort,
                               CompletarClaseDiariaHabitoUseCase completarHabitoUseCase,
                               CompletarLeccionUseCase completarLeccionUseCase) {
        this.loadCursoPort = loadCursoPort;
        this.loadSeccionCursoPort = loadSeccionCursoPort;
        this.loadLeccionPort = loadLeccionPort;
        this.progresoPort = progresoPort;
        this.completarHabitoUseCase = completarHabitoUseCase;
        this.completarLeccionUseCase = completarLeccionUseCase;
    }

    @Override
    public ClaseDiariaResolution claseDeHoy(UserId actorId) {
        ProgresoParticipanteAcademy progreso = requireProgresoTrainee(actorId);
        int diaActual = progreso.diaPrograma() == null ? Curso.DIA_PROGRAMA_INICIAL : progreso.diaPrograma();
        if (diaActual == 0) {
            return new NoIniciado();
        }

        Optional<ClaseEncontrada> clase = buscarClaseDiaria(diaActual);
        if (clase.isEmpty()) {
            return new Proximamente(diaActual);
        }

        Curso curso = loadCursoPort.byId(clase.get().cursoId())
                .orElseThrow(() -> new NoSuchElementException("Curso no encontrado: " + clase.get().cursoId()));
        if (!curso.visibleEnCatalogoPara(UserRole.TRAINEE, diaActual)) {
            throw new NotAuthorizedException("La clase diaria no esta disponible para tu cuenta");
        }

        ClaseEncontrada c = clase.get();
        return new Disponible(diaActual, c.cursoId(), c.cursoTitulo(), c.leccionId(), c.leccionTitulo());
    }

    /**
     * Espejo de {@code completeClaseDiaria} (clase-diaria/service.ts:60-90): resuelve la
     * Clase Diaria de HOY en servidor (nunca confia en el {@code programDay} ni en la
     * leccion que mande el cliente salvo para el chequeo de coincidencia), cierra el habito
     * {@code DAILY_CLASS} en `habits` y recien despues marca la leccion como vista — mismo
     * orden que el repo viejo. Ambos pasos son ademas individualmente idempotentes
     * (`EstadoRegistro.COMPLETADO` es terminal en el lado de habits, `marcarCompletada` es
     * upsert-ignore en el lado de academy), asi que envolver todo en una transaccion local
     * (CLAUDE.MD §9.1) no cambia el comportamiento ante un reintento — solo lo hace atomico.
     */
    @Override
    @Transactional
    public ClaseDiariaCompletada completar(CompletarClaseDiariaCommand command) {
        UserId actorId = command.actorId();
        ClaseDiariaResolution resolucion = claseDeHoy(actorId);
        Disponible disponible = requireDisponible(resolucion);
        if (!disponible.leccionId().equals(command.leccionId())) {
            throw new NotAuthorizedException("Esta no es la clase diaria de hoy");
        }

        RegistroCompletado registro = completarHabitoUseCase
                .completarDeHoy(new CompletarClaseDiariaHabitoCommand(actorId, command.resumen()));
        completarLeccionUseCase.completar(actorId, disponible.leccionId());

        return new ClaseDiariaCompletada(disponible.leccionId(), registro.registroId(), registro.puntosOtorgados());
    }

    private Disponible requireDisponible(ClaseDiariaResolution resolucion) {
        if (resolucion instanceof Disponible disponible) {
            return disponible;
        }
        throw new IllegalStateException("No hay una clase diaria disponible para completar hoy");
    }

    /**
     * La Clase Diaria sale de la seccion publicada ya desbloqueada mas
     * reciente — mismo criterio "lte + la mas nueva gana" que
     * `clase-diaria/repository.ts:154-233`. A partir del dia 15 las
     * secciones representan RANGOS ("CICLO 2 (DIA 17-25)"), por eso el
     * match es por umbral y no por dia exacto.
     */
    private Optional<ClaseEncontrada> buscarClaseDiaria(int programDay) {
        if (programDay < 1 || programDay > ULTIMO_DIA_CON_CLASE) {
            return Optional.empty();
        }

        List<Curso> cursosPublicados = loadCursoPort.listarTodos().stream()
                .filter(c -> c.publicado() && c.diaDesbloqueo() != null && c.diaDesbloqueo() <= programDay)
                .toList();
        if (cursosPublicados.isEmpty()) {
            return Optional.empty();
        }

        record SeccionCandidata(SeccionCurso seccion, Curso curso) {
        }
        List<SeccionCandidata> candidatas = new ArrayList<>();
        for (Curso curso : cursosPublicados) {
            for (SeccionCurso seccion : loadSeccionCursoPort.porCurso(curso.id())) {
                if (seccion.diaDesbloqueo() != null && seccion.diaDesbloqueo() <= programDay) {
                    candidatas.add(new SeccionCandidata(seccion, curso));
                }
            }
        }
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }

        SeccionCandidata elegida = candidatas.stream()
                .max(Comparator.<SeccionCandidata>comparingInt(sc -> sc.seccion().diaDesbloqueo())
                        .thenComparingInt(sc -> sc.curso().diaDesbloqueo()))
                .orElseThrow();

        List<Leccion> leccionesDeLaSeccion = loadLeccionPort.porCurso(elegida.curso().id()).stream()
                .filter(l -> elegida.seccion().id().equals(l.seccionId()))
                .sorted(Comparator.comparingInt(Leccion::orden))
                .toList();
        if (leccionesDeLaSeccion.isEmpty()) {
            return Optional.empty();
        }

        Leccion leccion = leccionesDeLaSeccion.stream()
                .filter(l -> PATRON_LECCION_CLASE.matcher(l.titulo()).find())
                .findFirst()
                .orElse(leccionesDeLaSeccion.get(0));

        return Optional.of(new ClaseEncontrada(elegida.curso().id(), elegida.curso().titulo(), leccion.id(),
                leccion.titulo()));
    }

    private record ClaseEncontrada(CursoId cursoId, String cursoTitulo, LeccionId leccionId, String leccionTitulo) {
    }

    private ProgresoParticipanteAcademy requireProgresoTrainee(UserId actorId) {
        ProgresoParticipanteAcademy progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz tiene clase diaria");
        }
        return progreso;
    }
}
