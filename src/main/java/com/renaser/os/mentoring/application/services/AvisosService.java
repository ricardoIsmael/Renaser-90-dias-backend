package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.api.AvisoDeAcompanamientoEvent;
import com.renaser.os.mentoring.application.ports.in.DetectarAvisosUseCase;
import com.renaser.os.mentoring.domain.model.aviso.AvisoDeAcompanamiento;
import com.renaser.os.mentoring.domain.model.aviso.ReglasDeAviso;
import com.renaser.os.mentoring.domain.model.aviso.ReglasDeAviso.ObligacionEvaluada;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Barrido que busca condiciones que el mentor debería mirar y publica un evento por cada una.
 *
 * <p>No consulta si el aviso ya existe: la clave de deduplicación es determinista y
 * {@code notificaciones.origen_evento_id} tiene índice único, así que un aviso repetido se
 * descarta en la inserción. Preguntar antes sería un check-then-insert que además pierde la
 * carrera contra otra instancia.
 *
 * <p>Publica, no notifica. Quién termina viéndolo lo decide {@code notifications} con las
 * preferencias del usuario: negar el push no debe apagar el aviso en la app.
 */
@Service
public class AvisosService implements DetectarAvisosUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisosService.class);

    /**
     * Ventana que se mira hacia atrás. Cubre de sobra un umbral de ausencia de hasta 30 días y
     * las evidencias vencidas recientes; no tiene sentido reprochar algo de hace dos meses como
     * si fuera novedad.
     */
    private static final int DIAS_DE_VENTANA = 45;

    private final AcompanamientoFinder acompanamientoFinder;
    private final ObligacionesHistoricasFinder obligacionesFinder;
    private final EntregasPorRegistroFinder entregasFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public AvisosService(AcompanamientoFinder acompanamientoFinder,
                          ObligacionesHistoricasFinder obligacionesFinder,
                          EntregasPorRegistroFinder entregasFinder, UserSummaryFinder userSummaryFinder,
                          ApplicationEventPublisher eventos, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.obligacionesFinder = obligacionesFinder;
        this.entregasFinder = entregasFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Override
    public int detectar() {
        Instant ahora = clock.now();
        int publicados = 0;
        for (GrupoAcompanado grupo : acompanamientoFinder.gruposConMentorVigente(ahora)) {
            try {
                publicados += revisarGrupo(grupo, ahora);
            } catch (RuntimeException e) {
                // Aislar el grupo: que uno falle no puede dejar sin avisar a los demás (V26).
                log.error("[mentoring.AvisosService] fallo el grupo {}; sigue el barrido", grupo.grupoId(), e);
            }
        }
        return publicados;
    }

    /** Una transacción por grupo, no una por barrido. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revisarGrupo(GrupoAcompanado grupo, Instant ahora) {
        List<UserId> alumnos = acompanamientoFinder.aprendicesVigentes(grupo.grupoId(), ahora);
        if (alumnos.isEmpty()) {
            return 0;
        }

        ZoneId zona = ZoneId.of(grupo.zonaHoraria());
        LocalDate hoyLocal = ahora.atZone(zona).toLocalDate();
        LocalDate desde = hoyLocal.minusDays(DIAS_DE_VENTANA);

        // Dos consultas para todo el grupo, no dos por alumno.
        List<ObligacionHabito> obligaciones =
                obligacionesFinder.porParticipantesEntre(alumnos, desde, hoyLocal);
        Map<UUID, EntregaDeEvidencia> entregas =
                entregasFinder.porRegistros(obligaciones.stream().map(ObligacionHabito::registroId).toList());
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(alumnos);

        int publicados = 0;
        for (UserId alumno : alumnos) {
            if (alumno.equals(grupo.mentorId())) {
                // Un mentor que además cursa no se autoavisa: su programa personal es
                // independiente de su grupo (plan.md §3).
                continue;
            }
            List<ObligacionEvaluada> suyas = obligaciones.stream()
                    .filter(o -> o.participanteId().equals(alumno))
                    .map(o -> aEvaluada(o, entregas))
                    .toList();

            for (AvisoDeAcompanamiento aviso : ReglasDeAviso.evaluar(grupo.mentorId(), alumno, grupo.grupoId(),
                    suyas, hoyLocal, grupo.diasSinActividadAlerta())) {
                eventos.publishEvent(new AvisoDeAcompanamientoEvent(aviso.claveDeDeduplicacion(),
                        grupo.mentorId().value(), alumno.value(), grupo.grupoId(), aviso.motivo().name(),
                        aviso.magnitud(), nombreDe(perfiles, alumno), ahora));
                publicados++;
            }
        }
        return publicados;
    }

    private static ObligacionEvaluada aEvaluada(ObligacionHabito obligacion,
                                                 Map<UUID, EntregaDeEvidencia> entregas) {
        return new ObligacionEvaluada(obligacion.fecha(), obligacion.requiereEvidencia(), obligacion.exigible(),
                obligacion.estado().cumplido(), entregas.containsKey(obligacion.registroId()));
    }

    private static String nombreDe(Map<UserId, UserSummary> perfiles, UserId alumno) {
        UserSummary perfil = perfiles.get(alumno);
        return perfil != null && perfil.fullName() != null ? perfil.fullName() : "Un aprendiz de tu grupo";
    }
}
