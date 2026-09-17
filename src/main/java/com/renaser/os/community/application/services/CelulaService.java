package com.renaser.os.community.application.services;

import com.renaser.os.community.api.CelulaCreadaEvent;
import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.community.api.CelulaFinder.CelulaParticipanteResumen;
import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarDashboardCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase;
import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.EstadoGrupo;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.EspecialidadMentor;
import com.renaser.os.users.api.PerfilMentorFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
/**
 * Alta, edicion y lecturas del grupo como ENTIDAD: nombre, cohorte, periodo, cupo y sesion.
 *
 * <p>Quien esta DENTRO del grupo ya no se decide aca: eso es
 * {@link ComposicionDeCelulaService}, porque agregar a alguien no es escribir un puntero sino
 * abrir un intervalo, mover el cupo y avisarle al chat. Separarlos deja esta clase en lo que su
 * nombre promete y evita que un cambio de composicion se cuele como un {@code save} mas.
 */
public class CelulaService implements CrearCelulaUseCase, ActualizarCelulaUseCase, ProgramarSesionCelulaUseCase,
        EliminarCelulaUseCase, ConsultarCelulasUseCase, ConsultarMiCelulaUseCase, ConsultarDashboardCelulasUseCase,
        ConsultarCandidatosCelulaUseCase, CelulaFinder {

    private final LoadCelulaPort loadCelulaPort;
    private final SaveCelulaPort saveCelulaPort;
    private final EliminarCelulaPort eliminarCelulaPort;
    private final LoadCohortePort loadCohortePort;
    private final ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort;
    private final ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final PerfilMentorFinder perfilMentorFinder;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public CelulaService(LoadCelulaPort loadCelulaPort, SaveCelulaPort saveCelulaPort,
                          EliminarCelulaPort eliminarCelulaPort, LoadCohortePort loadCohortePort,
                          ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort,
                          ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort,
                          ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort, UserSummaryFinder userSummaryFinder,
                          ParticipacionProgramaFinder participacionProgramaFinder,
                          PerfilMentorFinder perfilMentorFinder, LoadAsignacionesPort loadAsignacionesPort,
                          LoadPoliticaMentoriaPort loadPoliticaMentoriaPort, ApplicationEventPublisher events,
                          Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.saveCelulaPort = saveCelulaPort;
        this.eliminarCelulaPort = eliminarCelulaPort;
        this.loadCohortePort = loadCohortePort;
        this.consultarMiembrosCelulaPort = consultarMiembrosCelulaPort;
        this.consultarCelulaDeParticipantePort = consultarCelulaDeParticipantePort;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.perfilMentorFinder = perfilMentorFinder;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public CelulaDetalle crear(CrearCelulaCommand command) {
        requireAdmin(command.actorId());
        requireCohorte(command.cohorteId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Celula celula = Celula.crear(CelulaId.of(idGenerator.newId()), command.nombre(), command.cohorteId(),
                command.urlVideollamada(), command.periodo(), clock.now());
        celula.marcarComo(command.tipoEfectivo(), clock.now());
        if (command.capacidad() != null) {
            celula.cambiarCapacidad(command.capacidad(), clock.now());
        }
        Celula guardada = saveCelulaPort.save(celula);
        events.publishEvent(new CelulaCreadaEvent(guardada.id().value(), clock.now()));
        return aDetalle(guardada);
    }

    @Override
    @Transactional
    public CelulaDetalle actualizar(ActualizarCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        requireCohorteNoCompletada(celula.cohorteId());
        celula.actualizarDatos(command.nombre(), command.urlVideollamada(), command.tocaUrlVideollamada(),
                command.periodo(), command.tocaPeriodo(), clock.now());
        if (command.tocaCapacidad()) {
            celula.cambiarCapacidad(command.capacidad(), clock.now());
        }
        return aDetalle(saveCelulaPort.save(celula));
    }

    @Override
    @Transactional
    public CelulaDetalle programar(ProgramarSesionCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        celula.programarSesion(command.proximaSesionEn(), clock.now());
        return aDetalle(saveCelulaPort.save(celula));
    }

    @Override
    @Transactional
    public void eliminar(EliminarCelulaCommand command) {
        requireAdmin(command.actorId());
        requireCelula(command.celulaId());
        eliminarCelulaPort.eliminar(command.celulaId());
    }

    @Override
    public List<CelulaResumen> listarPorCohorte(UserId actorId, CohorteId cohorteId) {
        UserSummary actor = requireActorActivo(actorId);
        List<Celula> celulas;
        if (actor.role() == UserRole.MENTOR) {
            celulas = loadCelulaPort.porMentor(actorId)
                    .filter(c -> c.cohorteId().equals(cohorteId))
                    .map(List::of).orElseGet(List::of);
        } else {
            requireRolAdmin(actor);
            celulas = loadCelulaPort.porCohorte(cohorteId);
        }
        return celulas.stream().map(this::aResumen).toList();
    }

    @Override
    public CelulaDetalle obtener(UserId actorId, CelulaId celulaId) {
        UserSummary actor = requireActorActivo(actorId);
        Celula celula = requireCelula(celulaId);
        if (actor.role() == UserRole.MENTOR) {
            if (celula.mentorId() == null || !celula.mentorId().equals(actorId)) {
                throw new NotAuthorizedException("No lideras esta celula");
            }
        } else {
            requireRolAdmin(actor);
        }
        return aDetalle(celula);
    }

    /** Proyeccion completa de una celula — la misma que devuelve {@link #obtener}. Las
     * mutaciones la construyen DENTRO de su propia transaccion (CLAUDE.MD sec. 5.4.6): el
     * controller ya no encadena "muto y despues consulto", que caia en dos transacciones
     * distintas y podia responder un estado ya cambiado por otro. */
    private CelulaDetalle aDetalle(Celula celula) {
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        /* Los miembros salen del HISTORIAL de asignaciones, igual que el conteo de la linea de
           abajo, y no del puntero `participantes_programa.celula_id`.

           > **Corregido 2026-09-16 (D-139).** Salian del puntero. Como ese puntero nombra UN solo
           > grupo y desde D-139 se puede pertenecer a varios, un aprendiz sumado a un grupo
           > adicional quedaba CONTADO en `activeTraineesCount` --que ya leia el historial-- y a la
           > vez AUSENTE de `members`. El grupo decia "3 aprendices" y mostraba dos. Un conteo y una
           > lista que no se pueden contradecir tienen que salir de la misma fuente.

           Verificado antes de cambiarlo: con los datos de hoy las dos fuentes dan lo mismo en los
           tres grupos y no hay ningun puntero sin fila viva detras, asi que nadie desaparece. */
        List<PerfilBasico> miembros = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celula.id()))
                .aprendicesVigentesEn(celula.id(), clock.now()).stream()
                .map(this::perfilBasico).toList();
        return new CelulaDetalle(celula, mentor, miembros, celula.estadoEn(hoyDelPrograma()),
                aprendicesVigentes(celula), cupoMaximo(celula));
    }

    @Override
    public Optional<MiCelula> miCelula(UserId traineeId) {
        requireActorActivo(traineeId);
        CelulaId celulaId = consultarCelulaDeParticipantePort.celulaDeUsuario(traineeId).orElse(null);
        if (celulaId == null) {
            return Optional.empty();
        }
        Celula celula = requireCelula(celulaId);
        /* Un grupo cuyo periodo ya cerro deja de verse desde la app del alumno. Decision del
           dueno del proyecto (2026-09-11): el administrador arma el grupo del mes siguiente y
           mientras tanto el alumno no tiene grupo, en vez de quedarse mirando uno terminado.

           La fila de `asignaciones_celula` puede seguir VIVA: cerrar el periodo del grupo no
           cierra las asignaciones. Por eso el filtro va por el periodo del GRUPO y no por la
           vigencia de la asignacion, que responde otra pregunta.

           Solo el admin lo sigue viendo, por `/api/v1/admin/cells`, que no pasa por aca. */
        if (celula.vencidoEn(hoyDelPrograma())) {
            return Optional.empty();
        }
        Cohorte cohorte = requireCohorte(celula.cohorteId());
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        int cantidadMiembros = consultarMiembrosCelulaPort.contarMiembros(celulaId);
        int totalCelulas = loadCelulaPort.porCohorte(celula.cohorteId()).size();
        return Optional.of(new MiCelula(celula, cohorte, mentor, cantidadMiembros, totalCelulas));
    }

    @Override
    public List<PerfilBasico> misCompaneros(UserId traineeId) {
        requireActorActivo(traineeId);
        return consultarCelulaDeParticipantePort.celulaDeUsuario(traineeId)
                .map(celulaId -> consultarMiembrosCelulaPort.deCelula(celulaId).stream()
                        .map(this::perfilBasico).toList())
                .orElseGet(List::of);
    }

    /** #25 (docs/PLAN_INTEGRACION_FRONTEND.md sec. 5): dashboard cross-cohorte — a
     * diferencia de {@link #listarPorCohorte}, no exige elegir una cohorte primero.
     * Solo ADMIN/ALCHEMIST: un MENTOR sigue viendo la propia por {@code GET /me/cell}. */
    @Override
    public List<CelulaConCohorte> dashboard(UserId actorId) {
        requireAdmin(actorId);
        return loadCelulaPort.todas().stream().map(this::aCelulaConCohorte).toList();
    }

    private CelulaConCohorte aCelulaConCohorte(Celula celula) {
        int cantidad = consultarMiembrosCelulaPort.contarMiembros(celula.id());
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        Cohorte cohorte = requireCohorte(celula.cohorteId());
        return new CelulaConCohorte(celula, cantidad, mentor, cohorte);
    }

    /** #25: mentores ACTIVOS que hoy no lideran ninguna celula — candidatos del selector
     * "Asignar mentor". {@link LoadCelulaPort#todas()} resuelve quien ya lidera sin
     * tocar `perfiles_mentor` (tabla ajena, mismo criterio que {@link #asignar}). */
    @Override
    public List<MentorCandidato> mentoresDisponibles(UserId actorId) {
        requireAdmin(actorId);
        Set<UserId> yaLideran = mentoresQueYaLideran();
        List<UserId> disponibles = mentoresActivos().stream().filter(id -> !yaLideran.contains(id)).toList();
        Map<UserId, EspecialidadMentor> especialidades = especialidadesDe(disponibles);
        return disponibles.stream().map(id -> aMentorCandidato(id, List.of(), especialidades.get(id))).toList();
    }

    /** #25: TODOS los mentores ACTIVOS, marcando con {@code celulasActuales} los grupos que ya
     * lideran — el picker los muestra a todos en vez de ocultar a los ocupados
     * (mismo criterio que el frontend ya documenta para este picker). */
    @Override
    public List<MentorCandidato> mentores(UserId actorId) {
        requireAdmin(actorId);
        Map<UserId, List<CelulaId>> celulasPorMentor = celulasPorMentor();
        List<UserId> todos = mentoresActivos();
        Map<UserId, EspecialidadMentor> especialidades = especialidadesDe(todos);
        return todos.stream()
                .map(id -> aMentorCandidato(id, celulasPorMentor.getOrDefault(id, List.of()),
                        especialidades.get(id)))
                .toList();
    }

    /**
     * Los grupos de cada mentor, ordenados por nombre de grupo.
     *
     * <p><b>Corregido 2026-09-17 (D-141).</b> Acá había un {@code Map<UserId, CelulaId>} llenado
     * con {@code put} dentro del recorrido de {@code todas()}: con un mentor al frente de dos
     * grupos, el segundo {@code put} pisaba al primero y la respuesta nombraba uno cualquiera de
     * los dos —cuál, dependía del orden en que la base devolviera las filas—. El picker de
     * "Asignar mentor" compara ese id contra el grupo que está mirando para decidir entre "ya
     * lidera este grupo" y "ya lidera otro grupo", así que le mentía justo cuando el mentor sí
     * lideraba el grupo en pantalla pero no era el que había sobrevivido al pisón.
     *
     * <p>Se ordena por nombre para que {@code celulaActual} —el primero— sea siempre el mismo
     * entre dos llamadas seguidas. Un orden arbitrario haría parpadear la etiqueta del picker sin
     * que nada hubiera cambiado.
     */
    private Map<UserId, List<CelulaId>> celulasPorMentor() {
        return loadCelulaPort.todas().stream()
                .filter(celula -> celula.mentorId() != null)
                .sorted(Comparator.comparing(Celula::nombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(Celula::mentorId,
                        Collectors.mapping(Celula::id, Collectors.toList())));
    }

    /** #25: aprendices ACTIVOS e inscritos, CON grupo o sin el — alcance GLOBAL (ver javadoc de
     * {@link ConsultarCandidatosCelulaUseCase#aprendicesDisponibles}). A quienes se puede
     * ofrecer lo decide {@link #aprendicesQueSePuedenAgregarAUnGrupo()}; de que grupo sale cada
     * uno, {@link #celulaPorAprendiz()}. */
    @Override
    public List<AprendizCandidato> aprendicesDisponibles(UserId actorId) {
        requireAdmin(actorId);
        Map<UserId, CelulaId> grupoDeCadaUno = celulaPorAprendiz();
        List<UserId> disponibles = aprendicesQueSePuedenAgregarAUnGrupo();
        Map<UserId, UserSummary> resumenes = userSummaryFinder.findByIds(disponibles);
        return disponibles.stream()
                .map(id -> {
                    UserSummary resumen = resumenes.get(id);
                    return new AprendizCandidato(id, resumen != null ? resumen.fullName() : null,
                            resumen != null ? resumen.avatarUrl() : null, grupoDeCadaUno.get(id));
                })
                .toList();
    }

    /**
     * Los candidatos del selector: aprendices ACTIVOS, <b>inscritos en el programa</b>, tengan hoy
     * grupo o no.
     *
     * <p><b>Tener grupo dejo de excluir</b> (E-190, 2026-09-16). Antes esta lista se quedaba solo
     * con los que no eran miembros de ninguna celula, y por eso no habia forma de <b>mover</b> a
     * nadie de grupo desde la pantalla: el que ya estaba en uno no aparecia en ningun selector. El
     * dueno lo reporto como "no me deja agregar mas aprendices".
     *
     * <p><b>Mover ya estaba implementado; lo que faltaba era ofrecerlo.</b>
     * {@code ComposicionDeCelulaService.asignar} cierra la pertenencia vigente
     * ({@code cerrarPertenenciaVigente}) antes de abrir la nueva, y contempla incluso reasignar
     * dentro de un grupo lleno sin cobrar plaza de mas (el aprendiz ya ocupa una). Esconder al
     * candidato no protegia de nada: solo tapaba una operacion que el backend sabe hacer.
     *
     * <p><b>Que sigue excluyendo, y por que no se toca.</b> Rol TRAINEE, estado ACTIVO e
     * inscripcion en el programa. El ultimo es E-186: sin fila en {@code participantes_programa}
     * el alta responde <b>404</b>.
     *
     * <p><b>El filtro por inscripcion</b> (E-186, 2026-09-15). Antes se ofrecia a todo
     * aprendiz ACTIVO sin grupo, incluidos los que no tienen fila en {@code participantes_programa}.
     * A esos, {@code POST /api/v1/admin/cells/&#123;id&#125;/trainees} les responde <b>404</b>:
     * {@code ParticipacionProgramaService.sincronizarAcompanamiento} no encuentra la participacion
     * y lanza {@code NoSuchElementException}. El administrador tocaba un candidato de la lista que
     * el propio panel le ofrecia y le salia "No se pudo agregar". El repo frontend ya lo tenia
     * documentado, y lo esquivaba sembrando participaciones a mano
     * ({@code e2e/admin-alquimista/soporte/escenarios.sql}).
     *
     * <p><b>Por que se arregla del lado de la OFERTA y no del de la escritura.</b> Hacer que
     * agregar "funcione" significaria crear la fila de {@code participantes_programa} desde aca, y
     * esa fila necesita {@code fecha_inicio} y {@code programa_activado_en} — fechas que solo sabe
     * quien da de alta a la persona ({@code ApproveAccountRequestUseCase} /
     * {@code InviteAndCreateUserUseCase}). Inventarlas seria inventar una regla de negocio, y
     * ademas {@code community} no es dueno de esa tabla. Un aprendiz sin programa no es alguien a
     * quien falte agregar a un grupo: es alguien a quien falta <b>inscribir</b>, y eso se hace en
     * otra pantalla.
     *
     * <p><b>Sigue sin haber N+1</b> (D-43): {@code participantesInscritosActivos()} es UNA consulta
     * en lote, igual que {@code usuariosActivosConRol}. No se pregunta por participante. Tampoco
     * hizo falta un metodo nuevo en el puerto: {@code users.api.ParticipacionProgramaFinder} ya lo
     * exponia.
     */
    private List<UserId> aprendicesQueSePuedenAgregarAUnGrupo() {
        Set<UserId> inscritos = new HashSet<>(participacionProgramaFinder.participantesInscritosActivos());
        return participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.TRAINEE)).stream()
                .filter(inscritos::contains)
                .toList();
    }

    /**
     * En que grupo esta hoy cada aprendiz, para marcar el traslado en el selector.
     *
     * <p>Es el MISMO recorrido que antes armaba el conjunto de excluidos: una consulta por celula,
     * y las celulas son decenas — no crece con el padron, asi que no reabre el N+1 de D-43. Lo
     * unico que cambia es que en vez de tirar la pertenencia se guarda de que grupo era.
     *
     * <p>Un aprendiz esta en un grupo como mucho, porque el dato es una sola columna
     * ({@code participantes_programa.celula_id}); si dos celulas lo reclamaran, gana la ultima y
     * la pantalla mostraria una de las dos, que es exactamente el sintoma que habria que mirar.
     */
    private Map<UserId, CelulaId> celulaPorAprendiz() {
        Map<UserId, CelulaId> grupoDeCadaUno = new HashMap<>();
        for (Celula celula : loadCelulaPort.todas()) {
            for (UserId miembro : participacionProgramaFinder.miembrosDeCelula(celula.id().value())) {
                grupoDeCadaUno.put(miembro, celula.id());
            }
        }
        return grupoDeCadaUno;
    }

    private List<UserId> mentoresActivos() {
        return participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.MENTOR));
    }

    private Set<UserId> mentoresQueYaLideran() {
        Set<UserId> yaLideran = new HashSet<>();
        for (Celula celula : loadCelulaPort.todas()) {
            if (celula.mentorId() != null) {
                yaLideran.add(celula.mentorId());
            }
        }
        return yaLideran;
    }

    private MentorCandidato aMentorCandidato(UserId id, List<CelulaId> celulasActuales,
                                             EspecialidadMentor especialidad) {
        UserSummary resumen = userSummaryFinder.findById(id).orElse(null);
        CelulaId primera = celulasActuales.isEmpty() ? null : celulasActuales.get(0);
        return new MentorCandidato(id, resumen != null ? resumen.fullName() : null,
                resumen != null ? resumen.avatarUrl() : null, primera, celulasActuales, especialidad);
    }

    /**
     * Las especialidades en UNA consulta. El selector pide decenas de mentores de golpe y
     * preguntar de a uno seria el mismo N+1 que este panel ya se saco de encima en el resto de
     * los pickers. Un mentor sin perfil no aparece en el mapa y su especialidad queda null, que
     * es exactamente lo que hay que mostrar.
     */
    private Map<UserId, EspecialidadMentor> especialidadesDe(List<UserId> mentorIds) {
        Map<UserId, EspecialidadMentor> especialidades = new HashMap<>();
        perfilMentorFinder.porUsuarios(mentorIds)
                .forEach((id, perfil) -> especialidades.put(id, perfil.especialidad()));
        return especialidades;
    }

    private CelulaResumen aResumen(Celula celula) {
        int cantidad = consultarMiembrosCelulaPort.contarMiembros(celula.id());
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        return new CelulaResumen(celula, cantidad, mentor, celula.estadoEn(hoyDelPrograma()),
                aprendicesVigentes(celula), cupoMaximo(celula));
    }

    /**
     * Ocupacion real leida del HISTORIAL, no del contador de la proyeccion: el cupo se mide en
     * aprendices vigentes y mentor/soporte no ocupan lugar. {@code cantidadMiembros} sigue
     * saliendo del puntero porque responde otra pregunta —cuantos figuran asignados— y hay
     * pantallas que ya la usan.
     */
    private int aprendicesVigentes(Celula celula) {
        return ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celula.id()))
                .aprendicesVigentesEn(celula.id(), clock.now()).size();
    }

    /** {@code null} en recepcion: no tiene tope comercial (D-05). */
    private Integer cupoMaximo(Celula celula) {
        int capacidadPolitica = loadPoliticaMentoriaPort.porCohorte(celula.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(celula.cohorteId()))
                .capacidadCelula();
        return celula.cupo(capacidadPolitica).maximo().orElse(null);
    }

    private PerfilBasico perfilBasico(UserId usuarioId) {
        return consultarPerfilUsuarioPort.porId(usuarioId)
                .map(p -> new PerfilBasico(p.id(), p.nombreCompleto(), p.avatarUrl()))
                .orElse(new PerfilBasico(usuarioId, null, null));
    }

    private void requireCohorteNoCompletada(CohorteId cohorteId) {
        if (requireCohorte(cohorteId).estado() == EstadoCohorte.COMPLETADA) {
            throw new NotAuthorizedException("No se pueden modificar celulas de una cohorte completada");
        }
    }

    /**
     * Que dia es hoy para decidir si un grupo cerro.
     *
     * <p>Un grupo es del programa, no de una persona: no hay "la zona del participante" a la que
     * acudir para esta pregunta, y usar la del servidor haria que un despliegue en otra region
     * moviera la fecha de cierre de todos los grupos a la vez. Se fija la del programa, la misma
     * que {@code PoliticaMentoria.ZONA_POR_DEFECTO}.
     */
    private java.time.LocalDate hoyDelPrograma() {
        return clock.now().atZone(java.time.ZoneId.of(PoliticaMentoria.ZONA_POR_DEFECTO)).toLocalDate();
    }

    private Celula requireCelula(CelulaId id) {
        return loadCelulaPort.porId(id).orElseThrow(() -> new NoSuchElementException("Celula no encontrada: " + id));
    }

    private Cohorte requireCohorte(CohorteId id) {
        return loadCohortePort.porId(id).orElseThrow(() -> new NoSuchElementException("Cohorte no encontrada: " + id));
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }

    private void requireAdmin(UserId actorId) {
        requireRolAdmin(requireActorActivo(actorId));
    }

    private void requireRolAdmin(UserSummary actor) {
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran celulas");
        }
    }
    /** {@link CelulaFinder} — contrato publico consumido por `calendar` (D-41). */
    @Override
    public Optional<UserId> mentorDe(UUID celulaId) {
        return loadCelulaPort.porId(new CelulaId(celulaId)).map(Celula::mentorId);
    }

    /**
     * {@link CelulaFinder} — contrato publico consumido por el agregador de ranking de
     * `points` (gap #24). Deliberadamente sin las validaciones de {@link #miCelula} (actor
     * activo, etc.): esas son responsabilidad del modulo que llama, no de esta lectura.
     */
    @Override
    public Optional<CelulaParticipanteResumen> celulaDeParticipante(UserId participanteId) {
        return consultarCelulaDeParticipantePort.celulaDeUsuario(participanteId)
                .flatMap(loadCelulaPort::porId)
                .map(this::aCelulaParticipanteResumen);
    }

    private CelulaParticipanteResumen aCelulaParticipanteResumen(Celula celula) {
        String cohortName = loadCohortePort.porId(celula.cohorteId()).map(Cohorte::nombre).orElse(null);
        String mentorName = celula.mentorId() != null
                ? consultarPerfilUsuarioPort.porId(celula.mentorId()).map(PerfilUsuario::nombreCompleto).orElse(null)
                : null;
        int cantidadMiembros = consultarMiembrosCelulaPort.contarMiembros(celula.id());
        int totalCelulas = loadCelulaPort.porCohorte(celula.cohorteId()).size();
        return new CelulaParticipanteResumen(celula.id().value(), celula.nombre(), cohortName, mentorName,
                cantidadMiembros, totalCelulas);
    }

}
