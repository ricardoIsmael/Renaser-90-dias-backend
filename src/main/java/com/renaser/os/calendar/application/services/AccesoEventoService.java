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
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

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

    AccesoEventoService(ConsultarProgresoParticipanteCalendarPort progresoPort, LoadNivelMembresiaPort nivelPort,
                         ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort,
                         ConsultarPertenenciaAGrupoPort pertenenciaAGrupoPort) {
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

    VisorContexto buildVisor(ProgresoParticipanteCalendar progreso) {
        int rango = 0;
        if (progreso.rol() == RolUsuario.TRAINEE) {
            List<NivelMembresia> niveles = nivelPort.listar();
            rango = ProgresoNivel.resolverRango(ProgresoNivel.porcentajeDeProgreso(progreso.diaPrograma()), niveles);
        }
        return new VisorContexto(progreso.rol(), rango, progreso.celulaId());
    }

    /** checkAccess() del repo viejo: audiencia + (si el tipo lo exige) elegibilidad. */
    boolean puedeAcceder(UserId actorId, ProgresoParticipanteCalendar progreso, VisorContexto visor, Evento evento) {
        if (visor.rol() != RolUsuario.ALCHEMIST && visor.rol() != RolUsuario.ADMIN
                && ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento())) {
            // rol_privilegiado del repo viejo: ADMIN/ALCHEMIST/MENTOR siempre elegibles; solo TRAINEE se consulta.
            if (visor.rol() == RolUsuario.TRAINEE && !elegibilidadPort.esElegible(actorId, evento.tipoEvento())) {
                return false;
            }
        }

        EventoAudiencia audiencia = proyectarAudiencia(evento);
        return ResolverAudiencia.puedeVer(visor, audiencia, perteneceAlDestino(actorId, visor, evento));
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
    private boolean perteneceAlDestino(UserId actorId, VisorContexto visor, Evento evento) {
        if (visor.rol() == RolUsuario.ADMIN || visor.rol() == RolUsuario.ALCHEMIST) {
            return false;
        }
        return switch (evento.tipoAudiencia()) {
            case CURSO -> evento.cursoId() != null && cursoPort.tieneAcceso(actorId, evento.cursoId());
            case CELULA -> evento.celulaDestinoId() != null && !evento.celulaDestinoId().equals(visor.celulaId())
                    && pertenenciaAGrupoPort.perteneceHoy(evento.celulaDestinoId(), actorId);
            case TODOS, NIVEL_MINIMO, ROLES -> false;
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
