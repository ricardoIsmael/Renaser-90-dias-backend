package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Alta y baja MANUAL de integrantes de un grupo — lo que hace el administrador cuando arma un
 * grupo a mano (SDD 003, ARF-06).
 *
 * <p><b>Por que es una clase aparte y no cuatro metodos en {@code CelulaService}.</b> Hasta esta
 * revision, asignar un aprendiz escribia UN dato: {@code participantes_programa.celula_id}, el
 * puntero al presente. No abria intervalo en {@code asignaciones_celula}, no miraba el cupo, no
 * avisaba al chat. El resultado era que el seguimiento semanal, la evaluacion del mentor y la
 * lista de participantes del chat —que leen el HISTORIAL— no se enteraban de una asignacion hecha
 * desde el panel. El grupo se veia armado en administracion y vacio en todo lo demas.
 *
 * <p>Lo que aca se hace es UNA operacion de composicion con cinco efectos que van juntos o no van:
 * <ol>
 *   <li>cerrar el intervalo anterior, si lo habia, conservandolo;</li>
 *   <li>abrir el intervalo nuevo en {@code asignaciones_celula};</li>
 *   <li>sincronizar los punteros de {@code users} —del aprendiz y de su mentor vigente—;</li>
 *   <li>publicar {@link ComposicionDeCelulaCambiadaEvent} de CADA grupo tocado, que es lo que
 *       reconcilia el chat;</li>
 *   <li>respetar cupo y solapamientos, que ademas la base sostiene con indices.</li>
 * </ol>
 *
 * <p>No copia {@code TrasladoService} ni {@code RotacionService}: comparte con ellos el dominio
 * ({@link ConjuntoAsignaciones}, {@link AsignacionCelula}) y el puerto de punteros, que es donde
 * viven las reglas. Lo que no comparte es la decision de A QUE grupo va cada persona — ahi esta la
 * diferencia entera entre este alcance y el modelo automatico anterior: aca lo decide una persona.
 */
@Service
public class ComposicionDeCelulaService implements AsignarMentorCelulaUseCase, QuitarMentorCelulaUseCase,
        AsignarAprendizCelulaUseCase, QuitarAprendizCelulaUseCase {

    private final LoadCelulaPort loadCelulaPort;
    private final SaveCelulaPort saveCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ExistePerfilMentorPort existePerfilMentorPort;
    private final UserSummaryFinder userSummaryFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ConsultarCelulasUseCase consultarCelulas;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ComposicionDeCelulaService(LoadCelulaPort loadCelulaPort, SaveCelulaPort saveCelulaPort,
                                       LoadAsignacionesPort loadAsignacionesPort,
                                       SaveAsignacionPort saveAsignacionPort,
                                       LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                                       ExistePerfilMentorPort existePerfilMentorPort,
                                       UserSummaryFinder userSummaryFinder,
                                       AsignacionCelulaPort asignacionCelulaPort,
                                       ConsultarCelulasUseCase consultarCelulas,
                                       ApplicationEventPublisher eventos, Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.saveCelulaPort = saveCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.existePerfilMentorPort = existePerfilMentorPort;
        this.userSummaryFinder = userSummaryFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.consultarCelulas = consultarCelulas;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    // ── Aprendices ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CelulaDetalle asignar(AsignarAprendizCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula destino = requireCelula(command.celulaId());
        requireAprendizElegible(command.traineeId());

        Instant ahora = clock.now();
        String clave = claveDeAlta(command.traineeId(), destino.id());
        ConjuntoAsignaciones delAprendiz = ConjuntoAsignaciones.de(loadAsignacionesPort.porUsuario(command.traineeId()));
        if (delAprendiz.yaAplicada(clave).isPresent()) {
            // Reintento del mismo comando: la membresia ya esta, no se abre otra (ARF-06).
            return detalle(command.actorId(), destino.id());
        }

        CelulaId origen = grupoVigenteDe(delAprendiz, ahora).orElse(null);
        requireCupoDisponible(destino, ahora);

        cerrarPertenenciaVigente(delAprendiz, ahora, MotivoAsignacion.ADMINISTRATIVO);
        abrir(destino.id(), command.traineeId(), FuncionAcompanamiento.APRENDIZ, ahora,
                MotivoAsignacion.ADMINISTRATIVO, command.actorId(), clave);
        sincronizarAprendiz(command.traineeId(), destino.id(), ahora);

        avisarComposicion(ahora, destino.id(), origen);
        return detalle(command.actorId(), destino.id());
    }

    @Override
    @Transactional
    public void quitar(QuitarAprendizCelulaCommand command) {
        requireAdmin(command.actorId());
        Instant ahora = clock.now();

        List<AsignacionCelula> vigentes = loadAsignacionesPort.porUsuario(command.traineeId()).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(ahora))
                .toList();

        /* Retirar valida el grupo de ORIGEN y no solo el id del aprendiz. Sin esto, pedir la baja
           desde el grupo equivocado —una pantalla vieja, dos administradores a la vez— le borraria
           la pertenencia real al aprendiz en el grupo donde si estaba (ARF-06, V13). */
        AsignacionCelula enEseGrupo = vigentes.stream()
                .filter(a -> a.celulaId().equals(command.celulaId()))
                .findFirst()
                .orElseThrow(() -> new AsignacionInvalidaException(
                        "Ese aprendiz no pertenece hoy al grupo " + command.celulaId().value()));

        enEseGrupo.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
        saveAsignacionPort.save(enEseGrupo);
        asignacionCelulaPort.quitarCelula(command.actorId(), command.traineeId());
        avisarComposicion(ahora, command.celulaId(), null);
    }

    // ── Mentor ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CelulaDetalle asignar(AsignarMentorCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        requireMentorElegible(command.mentorId());

        Instant ahora = clock.now();
        String clave = claveDeMentor(command.mentorId(), celula.id());
        ConjuntoAsignaciones delGrupo = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celula.id()));
        if (delGrupo.yaAplicada(clave).isPresent()) {
            return detalle(command.actorId(), celula.id());
        }

        /* El saliente pierde el acceso DE VERDAD: se cierra su intervalo, y de ahi salen tanto el
           403 del seguimiento semanal como su baja del chat. Cambiar solo `celulas.mentor_id`
           dejaba a un exmentor leyendo a los alumnos del grupo que ya no acompaña. */
        cerrarMentorVigente(celula.id(), ahora);
        cerrarMentorEnOtrosGrupos(command.mentorId(), ahora);

        abrir(celula.id(), command.mentorId(), FuncionAcompanamiento.MENTOR, ahora,
                MotivoAsignacion.ADMINISTRATIVO, command.actorId(), clave);
        celula.asignarMentor(command.mentorId(), ahora);
        saveCelulaPort.save(celula);
        sincronizarAprendicesDe(celula.id(), ahora);

        avisarComposicion(ahora, celula.id(), null);
        return detalle(command.actorId(), celula.id());
    }

    @Override
    @Transactional
    public CelulaDetalle quitar(QuitarMentorCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        Instant ahora = clock.now();

        cerrarMentorVigente(celula.id(), ahora);
        celula.quitarMentor(ahora);
        saveCelulaPort.save(celula);
        // El puntero de los aprendices queda en null, no en el mentor anterior: seguir apuntandolo
        // seria mentira y es quien decide a quien se le autoriza su evidencia.
        sincronizarAprendicesDe(celula.id(), ahora);

        avisarComposicion(ahora, celula.id(), null);
        return detalle(command.actorId(), celula.id());
    }

    // ── Piezas compartidas ──────────────────────────────────────────────────

    /**
     * La clave se deriva del PAR persona-grupo, no del instante: repetir el comando encuentra la
     * asignacion ya creada en vez de abrir otro intervalo identico. Con una clave por instante,
     * dos clics del administrador dejarian dos membresias del mismo aprendiz.
     */
    private static String claveDeAlta(UserId aprendizId, CelulaId celulaId) {
        return "alta-manual|" + aprendizId.value() + "|" + celulaId.value();
    }

    private static String claveDeMentor(UserId mentorId, CelulaId celulaId) {
        return "mentor-manual|" + mentorId.value() + "|" + celulaId.value();
    }

    private void abrir(CelulaId celulaId, UserId usuarioId, FuncionAcompanamiento funcion, Instant ahora,
                        MotivoAsignacion motivo, UserId actorId, String clave) {
        AsignacionCelula nueva = AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), celulaId, usuarioId,
                funcion, ahora, motivo, actorId, clave);
        // Se vuelve a leer el grupo DESPUES de los cierres: verificar contra la foto anterior
        // rechazaria una alta que el propio comando acaba de dejar valida.
        ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId)).verificarPuedeAbrir(nueva);
        saveAsignacionPort.save(nueva);
    }

    private void cerrarPertenenciaVigente(ConjuntoAsignaciones delAprendiz, Instant ahora, MotivoAsignacion motivo) {
        delAprendiz.todas().stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(ahora))
                .forEach(anterior -> {
                    anterior.cerrar(ahora, motivo);
                    saveAsignacionPort.save(anterior);
                });
    }

    private void cerrarMentorVigente(CelulaId celulaId, Instant ahora) {
        loadAsignacionesPort.porCelula(celulaId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.vigenteEn(ahora))
                .forEach(saliente -> {
                    saliente.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
                    saveAsignacionPort.save(saliente);
                });
    }

    /** Un mentor lidera a lo sumo un grupo: si venia de otro, ese otro queda sin mentor. */
    private void cerrarMentorEnOtrosGrupos(UserId mentorId, Instant ahora) {
        loadAsignacionesPort.porUsuario(mentorId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.vigenteEn(ahora))
                .forEach(otra -> {
                    otra.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
                    saveAsignacionPort.save(otra);
                    loadCelulaPort.porId(otra.celulaId()).ifPresent(grupo -> {
                        grupo.quitarMentor(ahora);
                        saveCelulaPort.save(grupo);
                    });
                    sincronizarAprendicesDe(otra.celulaId(), ahora);
                    avisarComposicion(ahora, otra.celulaId(), null);
                });
    }

    private void sincronizarAprendiz(UserId aprendizId, CelulaId celulaId, Instant ahora) {
        UserId mentorVigente = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId))
                .mentorVigenteEn(celulaId, ahora).orElse(null);
        asignacionCelulaPort.sincronizarAcompanamiento(aprendizId, celulaId.value(), mentorVigente);
    }

    private void sincronizarAprendicesDe(CelulaId celulaId, Instant ahora) {
        ConjuntoAsignaciones composicion = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId));
        UserId mentorVigente = composicion.mentorVigenteEn(celulaId, ahora).orElse(null);
        for (UserId aprendiz : composicion.aprendicesVigentesEn(celulaId, ahora)) {
            asignacionCelulaPort.sincronizarAcompanamiento(aprendiz, celulaId.value(), mentorVigente);
        }
    }

    /** Los DOS grupos cambian cuando alguien se mueve: sin el aviso del origen, el chat anterior
     * lo seguiria mostrando como integrante. */
    private void avisarComposicion(Instant ahora, CelulaId destino, CelulaId origen) {
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.value(), ahora));
        if (origen != null && !origen.equals(destino)) {
            eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(origen.value(), ahora));
        }
    }

    private Optional<CelulaId> grupoVigenteDe(ConjuntoAsignaciones delAprendiz, Instant ahora) {
        return delAprendiz.todas().stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(ahora))
                .map(AsignacionCelula::celulaId)
                .findFirst();
    }

    /**
     * El cupo se mide en aprendices vigentes del historial, no en el contador de la proyeccion:
     * mentor y soporte no ocupan lugar (D-01). La base repite la regla; aca se rechaza antes para
     * que el error salga explicable y no como violacion de constraint.
     */
    private void requireCupoDisponible(Celula destino, Instant ahora) {
        int capacidadPolitica = loadPoliticaMentoriaPort.porCohorte(destino.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(destino.cohorteId()))
                .capacidadCelula();
        List<FuncionAcompanamiento> ocupantes = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(destino.id()))
                .ocupantesVigentesEn(destino.id(), ahora);
        if (!destino.cupo(capacidadPolitica).admiteOtroAprendiz(ocupantes)) {
            throw new AsignacionInvalidaException("El grupo " + destino.nombre() + " no tiene cupo disponible");
        }
    }

    private void requireAprendizElegible(UserId traineeId) {
        UserSummary trainee = userSummaryFinder.findById(traineeId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + traineeId));
        if (trainee.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("El aprendiz seleccionado no esta activo");
        }
        if (trainee.role() != UserRole.TRAINEE) {
            throw new IllegalArgumentException("Solo se puede asignar celula a un aprendiz");
        }
    }

    private void requireMentorElegible(UserId mentorId) {
        UserSummary lider = userSummaryFinder.findById(mentorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + mentorId));
        if (lider.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("El usuario seleccionado no esta activo");
        }
        if (lider.role() != UserRole.MENTOR && lider.role() != UserRole.ADMIN && lider.role() != UserRole.ALCHEMIST) {
            throw new IllegalArgumentException("El usuario seleccionado no puede liderar una celula");
        }
        if (!existePerfilMentorPort.existe(mentorId)) {
            throw new IllegalStateException(
                    "El usuario todavia no tiene un perfil de mentor (perfiles_mentor) — debe crearse desde "
                            + "el modulo de usuarios antes de poder liderar una celula");
        }
    }

    private CelulaDetalle detalle(UserId actorId, CelulaId celulaId) {
        return consultarCelulas.obtener(actorId, celulaId);
    }

    private Celula requireCelula(CelulaId id) {
        return loadCelulaPort.porId(id).orElseThrow(() -> new NoSuchElementException("Celula no encontrada: " + id));
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran celulas");
        }
    }
}
