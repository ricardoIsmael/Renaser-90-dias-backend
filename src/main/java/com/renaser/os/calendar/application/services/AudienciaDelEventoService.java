package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.ReglasPorTipoEvento;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import com.renaser.os.calendar.domain.model.nivelmembresia.ProgresoNivel;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A quién está dirigido un evento: las personas que reciben sus avisos. {@code resolveRecipients()} del repo
 * viejo: primero la audiencia (barata, en lote), después la elegibilidad (cara, por persona — solo si el
 * TIPO de evento la exige).
 *
 * <p><b>Extraído de {@code RecordatorioService} el 2026-10-06 (D-256)</b>, sin cambiar una regla: la hoja
 * «Quién respondió» llama «Sin respuesta» a esta MISMA audiencia menos quienes respondieron, y tenía que ser
 * exactamente la de los recordatorios — una segunda copia se desincronizaría en cuanto cambie una de las dos.
 */
@Component
class AudienciaDelEventoService {

    private final LoadNivelMembresiaPort nivelPort;
    private final ConsultarProgresoParticipanteCalendarPort progresoPort;
    private final ResolverAudienciaMasivaPort audienciaMasivaPort;
    private final ConsultarMiembrosCelulaPort celulaPort;
    private final ResolverAudienciaCursoPort cursoPort;
    private final ConsultarElegibilidadEventoPort elegibilidadPort;

    AudienciaDelEventoService(LoadNivelMembresiaPort nivelPort, ConsultarProgresoParticipanteCalendarPort progresoPort,
                              ResolverAudienciaMasivaPort audienciaMasivaPort, ConsultarMiembrosCelulaPort celulaPort,
                              ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort) {
        this.nivelPort = nivelPort;
        this.progresoPort = progresoPort;
        this.audienciaMasivaPort = audienciaMasivaPort;
        this.celulaPort = celulaPort;
        this.cursoPort = cursoPort;
        this.elegibilidadPort = elegibilidadPort;
    }

    /** Todos los destinatarios del evento: audiencia y, si el tipo lo exige, elegibilidad. */
    List<UserId> destinatarios(Evento evento) {
        List<UserId> candidatos = audiencia(evento);
        if (candidatos.isEmpty() || !ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento())) {
            return candidatos;
        }
        List<UserId> elegibles = new ArrayList<>();
        for (UserId candidato : candidatos) {
            if (esElegible(candidato, evento)) {
                elegibles.add(candidato);
            }
        }
        return elegibles;
    }

    /**
     * Si UNA persona es destinataria, sin consultar la elegibilidad de toda la audiencia: lo pide cada toque
     * de «pasar lista», que no puede costar una consulta por aprendiz del programa.
     */
    boolean incluye(Evento evento, UserId persona) {
        if (!audiencia(evento).contains(persona)) {
            return false;
        }
        return !ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento()) || esElegible(persona, evento);
    }

    /** rol_privilegiado del repo viejo: ADMIN/ALCHEMIST/MENTOR siempre elegibles; solo TRAINEE se consulta. */
    private boolean esElegible(UserId candidato, Evento evento) {
        var progreso = progresoPort.deParticipante(candidato);
        if (progreso.isEmpty()) {
            return false;
        }
        return progreso.get().rol() != RolUsuario.TRAINEE || elegibilidadPort.esElegible(candidato, evento.tipoEvento());
    }

    private List<UserId> audiencia(Evento evento) {
        return switch (evento.tipoAudiencia()) {
            case TODOS -> audienciaMasivaPort.traineesActivos();
            case ROLES -> audienciaMasivaPort.activosConRoles(evento.rolesDestino());
            case CELULA -> evento.celulaDestinoId() == null ? List.of() : celulaPort.miembrosActivos(evento.celulaDestinoId());
            case NIVEL_MINIMO -> audienciaNivelMinimo(evento);
            case CURSO -> audienciaCurso(evento);
        };
    }

    private List<UserId> audienciaNivelMinimo(Evento evento) {
        if (evento.nivelMinimoId() == null) {
            return List.of();
        }
        List<NivelMembresia> niveles = nivelPort.listar();
        Integer minRango = niveles.stream().filter(n -> n.id() == evento.nivelMinimoId())
                .findFirst().map(NivelMembresia::rango).orElse(null);
        if (minRango == null) {
            return List.of();
        }
        return audienciaMasivaPort.traineesActivosConDiaPrograma().stream()
                .filter(c -> c.diaPrograma() != null
                        && ProgresoNivel.resolverRango(ProgresoNivel.porcentajeDeProgreso(c.diaPrograma()), niveles) >= minRango)
                .map(ResolverAudienciaMasivaPort.ParticipanteConDia::id)
                .toList();
    }

    private List<UserId> audienciaCurso(Evento evento) {
        if (evento.cursoId() == null) {
            return List.of();
        }
        Set<UserId> candidatos = audienciaMasivaPort.traineesActivosConDiaPrograma().stream()
                .map(ResolverAudienciaMasivaPort.ParticipanteConDia::id)
                .collect(Collectors.toSet());
        return List.copyOf(cursoPort.filtrarConAcceso(evento.cursoId(), candidatos));
    }
}
