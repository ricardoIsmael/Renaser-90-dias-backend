package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.ReglasPorTipoEvento;
import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia;
import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia.EventoAudiencia;
import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia.VisorContexto;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import com.renaser.os.calendar.domain.model.nivelmembresia.ProgresoNivel;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.FormacionDelContenido;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.QuienConvoca;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Logica de acceso COMPARTIDA entre {@code EventoService} y {@code ConfirmacionService}
 * (resolver progreso del actor, construir su contexto de visor y decidir si puede ver un
 * evento) — evita duplicar {@code checkAccess}/{@code buildViewerContext} del repo viejo
 * (service.ts) en dos servicios distintos.
 */
@Component
class AccesoEventoService {

    private final ConsultarProgresoParticipanteCalendarPort progresoPort;
    private final LoadNivelMembresiaPort nivelPort;
    private final ResolverAudienciaCursoPort cursoPort;
    private final ConsultarElegibilidadEventoPort elegibilidadPort;
    private final ConsultarPertenenciaAGrupoPort pertenenciaAGrupoPort;
    /** Spec 024: un evento es de una formación y solo lo ven quienes la ven; es el punto único de la 022. */
    private final AlcanceDeFormacionFinder formaciones;

    /** Spec 044: la audiencia elegida guardada y las reglas de quién está dentro. {@code null} en las pruebas que no la usan. */
    private final AudienciaElegidaPort audienciaPort;
    private final AudienciaElegidaService audienciaElegida;

    AccesoEventoService(ConsultarProgresoParticipanteCalendarPort progresoPort, LoadNivelMembresiaPort nivelPort,
                         ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort,
                         ConsultarPertenenciaAGrupoPort pertenenciaAGrupoPort, AlcanceDeFormacionFinder formaciones) {
        this(progresoPort, nivelPort, cursoPort, elegibilidadPort, pertenenciaAGrupoPort, formaciones, null, null);
    }

    @Autowired
    AccesoEventoService(ConsultarProgresoParticipanteCalendarPort progresoPort, LoadNivelMembresiaPort nivelPort,
                         ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort,
                         ConsultarPertenenciaAGrupoPort pertenenciaAGrupoPort, AlcanceDeFormacionFinder formaciones,
                         AudienciaElegidaPort audienciaPort, AudienciaElegidaService audienciaElegida) {
        this.audienciaPort = audienciaPort;
        this.audienciaElegida = audienciaElegida;
        this.formaciones = formaciones;
        this.progresoPort = progresoPort;
        this.nivelPort = nivelPort;
        this.cursoPort = cursoPort;
        this.elegibilidadPort = elegibilidadPort;
        this.pertenenciaAGrupoPort = pertenenciaAGrupoPort;
    }

    /** SUSPENDIDO -> 403. No exige ningun rol especifico: el calendario lo consultan todos los roles. */
    ProgresoParticipanteCalendar requireProgreso(UserId actorId) {
        ProgresoParticipanteCalendar progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }

    /**
     * Administrar el calendario —y lo que cuelga de él, como la cuenta de Google que crea las salas de Meet (spec 040, FR-015)— es solo de ADMIN y
     * ALCHEMIST (D-186): ni TRAINEE, ni MENTOR, ni MENTOR_LEAD. La misma regla que {@code EventoService}.
     */
    void requireAdministraElCalendario(UserId actorId) {
        if (!administraElCalendario(actorId)) {
            throw new NotAuthorizedException("No tienes permiso para administrar el calendario");
        }
    }

    /** Spec 044 (FR-001): crear eventos ya no es solo de Administración; el aprendiz sigue sin poder. */
    void requirePuedeCrearEventos(UserId actorId) {
        if (!QuienConvoca.puedeCrear(requireProgreso(actorId).rol())) {
            throw new NotAuthorizedException("No tienes permiso para crear eventos");
        }
    }

    /** Lo mismo sin lanzar: para decidir qué mostrarle a quien ya está autorizado a ver el evento. */
    boolean administraElCalendario(UserId actorId) {
        RolUsuario rol = requireProgreso(actorId).rol();
        return rol == RolUsuario.ADMIN || rol == RolUsuario.ALCHEMIST;
    }

    /** Las formaciones cuyos eventos lee {@code actorId}; {@code pedida} (Administración) acota a una. */
    AlcanceDeFormacion alcanceDe(UserId actorId, Formacion pedida) {
        return FormacionDelContenido.paraLeer(formaciones.de(actorId), pedida);
    }

    /**
     * FR-004: el evento de una formación que {@code actorId} no ve responde EXACTAMENTE lo mismo que uno que no existe (el mismo 404 y el mismo
     * texto), antes de preguntar por su audiencia.
     */
    void requireEnSuFormacion(UserId actorId, Evento evento) {
        if (!formaciones.de(actorId).incluye(evento.formacion())) {
            throw new NoSuchElementException("Evento no encontrado: " + evento.id());
        }
    }

    VisorContexto buildVisor(ProgresoParticipanteCalendar progreso) {
        int rango = 0;
        if (progreso.rol() == RolUsuario.TRAINEE) {
            List<NivelMembresia> niveles = nivelPort.listar();
            rango = ProgresoNivel.resolverRango(ProgresoNivel.porcentajeDeProgreso(progreso.diaPrograma(), progreso.duracionDias()), niveles);
        }
        return new VisorContexto(progreso.rol(), rango, progreso.celulaId());
    }

    /**
     * checkAccess() del repo viejo: audiencia + (si el tipo lo exige) elegibilidad. Primero la audiencia, que es
     * barata; la elegibilidad de un aprendiz es una lectura del semaforo (D-293) y solo se paga si la audiencia
     * ya lo deja pasar.
     */
    boolean puedeAcceder(UserId actorId, ProgresoParticipanteCalendar progreso, VisorContexto visor, Evento evento) {
        return puedeAcceder(actorId, progreso, visor, evento, Map.of());
    }

    /**
     * Lo mismo con la audiencia elegida ya leída de varios eventos ({@link #precargar}): listar no puede costar una consulta por evento.
     */
    boolean puedeAcceder(UserId actorId, ProgresoParticipanteCalendar progreso, VisorContexto visor, Evento evento,
                         Map<EventoId, AudienciaElegida> precargadas) {
        EventoAudiencia audiencia = proyectarAudiencia(evento);
        boolean pertenece = evento.tipoAudiencia() == TipoAudiencia.SELECCION
                ? estaEnLaAudienciaElegida(actorId, visor, evento, precargadas)
                : perteneceAlDestino(actorId, visor, evento);
        if (!ResolverAudiencia.puedeVer(visor, audiencia, pertenece)) {
            return false;
        }
        // rol_privilegiado del repo viejo: ADMIN/ALCHEMIST/MENTOR siempre elegibles; solo TRAINEE se consulta.
        return visor.rol() != RolUsuario.TRAINEE || !ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento())
                || elegibilidadPort.esElegible(actorId, evento.tipoEvento());
    }

    /**
     * Si el visor pertenece al destino concreto del evento: el curso (audiencia CURSO) o el grupo (audiencia
     * CELULA). Se pregunta solo cuando hace falta: ADMIN/ALCHEMIST ven todo sin consultar nada, y en un evento
     * de grupo el puntero del visor ({@code participantes_programa.celula_id}) ya alcanza si coincide.
     *
     * <p><b>El grupo sale de las asignaciones vigentes (E-363, 2026-09-27).</b> Antes solo contaba ese puntero,
     * que nombra UN grupo y que un mentor no tiene: el mentor del grupo recibia el aviso del evento pero le
     * daba 403 al abrirlo, y los integrantes adicionales (D-139, D-141) tampoco tenian acceso.
     */
    /**
     * Spec 044 (FR-013): un evento con audiencia elegida lo ven la audiencia, quien lo creó y el líder si lo creó uno de sus mentores (P7). ADMIN y
     * ALCHEMIST ya pasaron por {@link ResolverAudiencia#puedeVer}.
     */
    private boolean estaEnLaAudienciaElegida(UserId actorId, VisorContexto visor, Evento evento, Map<EventoId, AudienciaElegida> precargadas) {
        if (audienciaElegida == null || visor.rol() == RolUsuario.ADMIN || visor.rol() == RolUsuario.ALCHEMIST) {
            return false;
        }
        if (esLiderDeQuienLoCreo(actorId, visor.rol(), evento)) {
            return true;
        }
        AudienciaElegida elegida = precargadas.containsKey(evento.id()) ? precargadas.get(evento.id()) : audienciaPort.de(evento.id());
        return audienciaElegida.incluye(evento, elegida, actorId, visor.rol());
    }

    /** La audiencia elegida de los eventos {@code SELECCION} de la lista, en una sola lectura. */
    Map<EventoId, AudienciaElegida> precargar(List<Evento> eventos) {
        if (audienciaPort == null) {
            return Map.of();
        }
        List<EventoId> ids = eventos.stream().filter(e -> e.tipoAudiencia() == TipoAudiencia.SELECCION).map(Evento::id).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<EventoId, AudienciaElegida> leidas = new HashMap<>(audienciaPort.deEventos(ids));
        ids.forEach(id -> leidas.putIfAbsent(id, AudienciaElegida.vacia()));
        return leidas;
    }

    /**
     * P7 del dueño: el líder de mentores ve, edita y cancela los eventos que crearon sus mentores, es decir los de un mentor o una guía (rol de
     * HOY de quien lo creó) de una formación que el líder ve.
     */
    boolean esLiderDeQuienLoCreo(UserId actorId, RolUsuario rolActor, Evento evento) {
        if (rolActor != RolUsuario.MENTOR_LEAD || evento.creadoPor() == null || evento.creadoPor().equals(actorId)) {
            return false;
        }
        if (!formaciones.de(actorId).incluye(evento.formacion())) {
            return false;
        }
        return progresoPort.deParticipante(evento.creadoPor()).map(p -> p.rol() == RolUsuario.MENTOR).orElse(false);
    }

    /**
     * FR-006: editan, cancelan y borran un evento quien lo creó, ADMIN y ALCHEMIST, y el líder si lo creó uno de sus mentores. Para los eventos de
     * siempre (sin audiencia elegida, creados por Administración) es lo de hoy: ADMIN y ALCHEMIST.
     */
    boolean gestiona(UserId actorId, Evento evento) {
        RolUsuario rol = requireProgreso(actorId).rol();
        if (QuienConvoca.administra(rol)) {
            return true;
        }
        if (!QuienConvoca.puedeCrear(rol)) {
            return false;
        }
        return actorId.equals(evento.creadoPor()) || esLiderDeQuienLoCreo(actorId, rol, evento);
    }

    void requireGestiona(UserId actorId, Evento evento) {
        if (!gestiona(actorId, evento)) {
            throw new NotAuthorizedException("No tienes permiso para cambiar este evento");
        }
    }

    /** El rol de hoy de una persona, si existe. */
    Optional<RolUsuario> rolDe(UserId persona) {
        return progresoPort.deParticipante(persona).map(ProgresoParticipanteCalendar::rol);
    }

    /** La audiencia elegida guardada de un evento (vacía si no tiene). */
    Optional<AudienciaElegida> audienciaElegidaDe(Evento evento) {
        if (audienciaPort == null || evento.tipoAudiencia() != TipoAudiencia.SELECCION) {
            return Optional.empty();
        }
        return Optional.of(audienciaPort.de(evento.id()));
    }

    private boolean perteneceAlDestino(UserId actorId, VisorContexto visor, Evento evento) {
        if (visor.rol() == RolUsuario.ADMIN || visor.rol() == RolUsuario.ALCHEMIST) {
            return false;
        }
        return switch (evento.tipoAudiencia()) {
            case CURSO -> evento.cursoId() != null && cursoPort.tieneAcceso(actorId, evento.cursoId());
            case CELULA -> evento.celulaDestinoId() != null && !evento.celulaDestinoId().equals(visor.celulaId())
                    && pertenenciaAGrupoPort.perteneceHoy(evento.celulaDestinoId(), actorId);
            case TODOS, NIVEL_MINIMO, ROLES, SELECCION -> false;
        };
    }

    private EventoAudiencia proyectarAudiencia(Evento evento) {
        Integer rangoNivel = null;
        if (evento.nivelMinimoId() != null) {
            rangoNivel = nivelPort.listar().stream()
                    .filter(n -> n.id() == evento.nivelMinimoId())
                    .findFirst().map(NivelMembresia::rango).orElse(null);
        }
        return new EventoAudiencia(evento.tipoAudiencia(), rangoNivel, evento.cursoId(), evento.rolesDestino(),
                evento.celulaDestinoId());
    }
}
