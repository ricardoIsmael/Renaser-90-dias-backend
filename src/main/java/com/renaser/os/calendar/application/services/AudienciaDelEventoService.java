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
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import com.renaser.os.calendar.domain.model.nivelmembresia.ProgresoNivel;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
 *
 * <p><b>Corregido 2026-10-30 (D-293).</b> La elegibilidad ya no es «cara, por persona»: para un aprendiz es el
 * semáforo (la Mentoría del Alquimista, solo con verde) y se lee en lote, una vez para toda la audiencia. El resto
 * de esta clase no cambia.
 */
@Component
class AudienciaDelEventoService {

    private final LoadNivelMembresiaPort nivelPort;
    private final ConsultarProgresoParticipanteCalendarPort progresoPort;
    private final ResolverAudienciaMasivaPort audienciaMasivaPort;
    private final ConsultarMiembrosCelulaPort celulaPort;
    private final ResolverAudienciaCursoPort cursoPort;
    private final ConsultarElegibilidadEventoPort elegibilidadPort;
    /** Spec 024: la audiencia de un evento se resuelve DENTRO de su formación («todos» = todos los de la formación). */
    private final AlcanceDeFormacionFinder formaciones;

    AudienciaDelEventoService(LoadNivelMembresiaPort nivelPort, ConsultarProgresoParticipanteCalendarPort progresoPort,
                              ResolverAudienciaMasivaPort audienciaMasivaPort, ConsultarMiembrosCelulaPort celulaPort,
                              ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort,
                              AlcanceDeFormacionFinder formaciones) {
        this.formaciones = formaciones;
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
        Set<UserId> elegibles = quienesPasan(candidatos, evento.tipoEvento());
        return candidatos.stream().filter(elegibles::contains).toList();
    }

    /**
     * Si UNA persona es destinataria, sin consultar la elegibilidad de toda la audiencia: lo pide cada toque
     * de «pasar lista», que no puede costar una consulta por aprendiz del programa.
     */
    boolean incluye(Evento evento, UserId persona) {
        if (!audiencia(evento).contains(persona)) {
            return false;
        }
        return !ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento())
                || quienesPasan(List.of(persona), evento.tipoEvento()).contains(persona);
    }

    /**
     * De estas personas, las que el tipo de evento deja pasar. rol_privilegiado del repo viejo: el personal
     * (guia, mentor, lider, admin, alquimista, Soporte TI) siempre; solo un aprendiz se consulta, y se consulta de
     * a todos juntos (D-293: el criterio es el semaforo, que se lee en lote). Quien no existe no pasa.
     *
     * <p>Lo usan la generacion de avisos, «Quien respondio» y el despacho, que vuelve a preguntarlo al enviar
     * ({@link ElegibilidadDeLosAvisos}).
     */
    Set<UserId> quienesPasan(Collection<UserId> personas, TipoEvento tipo) {
        Set<UserId> pasan = new HashSet<>();
        List<UserId> aprendices = new ArrayList<>();
        for (UserId persona : personas) {
            progresoPort.deParticipante(persona).ifPresent(progreso -> {
                if (progreso.rol() == RolUsuario.TRAINEE) {
                    aprendices.add(persona);
                } else {
                    pasan.add(persona);
                }
            });
        }
        if (!aprendices.isEmpty()) {
            pasan.addAll(elegibilidadPort.elegiblesEntre(aprendices, tipo));
        }
        return pasan;
    }

    private List<UserId> audiencia(Evento evento) {
        return switch (evento.tipoAudiencia()) {
            case TODOS -> dentroDeLaFormacion(audienciaMasivaPort.traineesActivos(), evento);
            case ROLES -> audienciaPorRoles(evento);
            case CELULA -> dentroDeLaFormacion(evento.celulaDestinoId() == null ? List.<UserId>of()
                    : celulaPort.miembrosActivos(evento.celulaDestinoId()), evento);
            case NIVEL_MINIMO -> dentroDeLaFormacion(audienciaNivelMinimo(evento), evento);
            case CURSO -> dentroDeLaFormacion(audienciaCurso(evento), evento);
        };
    }

    /**
     * Spec 024 (FR-016, FR-018): una persona recibe los avisos de un evento solo si PERTENECE a su formación. Es el filtro de todas las audiencias en un
     * solo lugar, una consulta para todos (sin N+1): «todos» son los aprendices de la formación, el grupo y el curso se resuelven dentro de ella, y
     * un aprendiz que cambió de formación deja de recibir lo de la anterior.
     *
     * <p>Lo único que no pertenece a una formación es Administración y Alquimista (ven las tres): si el evento los nombra por rol, se conservan.
     */
    private List<UserId> dentroDeLaFormacion(List<UserId> candidatos, Evento evento) {
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        Set<UserId> pertenecen = formaciones.dentroDelAlcance(candidatos, AlcanceDeFormacion.de(evento.formacion()));
        return candidatos.stream().filter(pertenecen::contains).toList();
    }

    private List<UserId> audienciaPorRoles(Evento evento) {
        Set<RolUsuario> queVenTodas = evento.rolesDestino().stream()
                .filter(rol -> rol == RolUsuario.ADMIN || rol == RolUsuario.ALCHEMIST)
                .collect(Collectors.toSet());
        Set<RolUsuario> deUnaFormacion = evento.rolesDestino().stream()
                .filter(rol -> !queVenTodas.contains(rol))
                .collect(Collectors.toSet());
        List<UserId> resultado = new ArrayList<>();
        if (!deUnaFormacion.isEmpty()) {
            resultado.addAll(dentroDeLaFormacion(audienciaMasivaPort.activosConRoles(deUnaFormacion), evento));
        }
        if (!queVenTodas.isEmpty()) {
            resultado.addAll(audienciaMasivaPort.activosConRoles(queVenTodas));
        }
        return resultado;
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
