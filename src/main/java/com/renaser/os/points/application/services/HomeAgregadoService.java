package com.renaser.os.points.application.services;

import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.HabitosDelDiaFinder;
import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.points.api.RocaDelDiaResumen;
import com.renaser.os.points.api.RocasDelDiaFinder;
import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implementa {@link ConsultarResumenHomeUseCase}. Reutiliza
 * {@link ConsultarPuntajeUseCase#consultar} pidiendo el propio puntaje del actor
 * (actorId == participanteId) — misma validacion de "cuenta activa" que ya tiene esa
 * consulta, sin duplicarla aca. Esa llamada corre PRIMERO: si el actor esta suspendido o no
 * existe en `users`, este metodo nunca llega a invocar los finders de los demas modulos.
 *
 * <p><b>Fallas parciales, decision por finder</b> (ver javadoc de {@link ConsultarResumenHomeUseCase}):
 * <ul>
 *   <li>{@link #habitosHoyDe}: {@code HabitosDelDiaFinder.deHoy} reusa puertas adentro
 *       {@code ConsultarTracksDelDiaUseCase}, que exige {@code ProgresoParticipanteHabits} —
 *       un actor sin fila ahi (ADMIN/MENTOR mirando su propio Inicio, o un TRAINEE cuyo
 *       progreso de habitos todavia no se genero) hace que el finder lance
 *       {@link NoSuchElementException}; una suspension detectada solo del lado de `habits`
 *       (no sincronizada todavia con `users`) lanza {@link NotAuthorizedException}. Ninguna
 *       de las dos es un error real de la request — el widget de habitos de hoy
 *       simplemente no aplica para este actor, se degrada a {@code null}.</li>
 *   <li>{@link #rocasHoyDe}: {@code RocasDelDiaFinder.deHoy} NO lanza estas excepciones (su
 *       propio javadoc dice "nunca null", lista vacia si no aplica) — no hace falta try/catch.</li>
 *   <li>{@link #proximoEventoDe} y {@link #notificacionesNoLeidasDe}: mismos dos tipos de
 *       excepcion documentados en sus finders respectivos, mismo criterio de degradar a
 *       {@code null} en vez de tumbar toda la request.</li>
 * </ul>
 *
 * <p>{@code participacionProgramaFinder.deParticipante} es la unica llamada que NO se trata
 * como falla parcial: su contrato dice que {@code Optional.empty()} ocurre solo cuando el
 * {@code UserId} no existe en absoluto en `usuarios` — y para cuando se llega hasta aca ya se
 * confirmo que existe (via {@code ConsultarPuntajeUseCase}, que ya resolvio su estado). Que
 * venga vacio en ese punto es una inconsistencia real de datos, no un estado de negocio
 * legitimo — se deja propagar como {@link NoSuchElementException} en vez de esconderse.
 */
@Service
public class HomeAgregadoService implements ConsultarResumenHomeUseCase {

    private static final Logger log = LoggerFactory.getLogger(HomeAgregadoService.class);

    /**
     * Unico bloqueo que sigue sin dato real detras (ver javadoc de
     * {@link ConsultarResumenHomeUseCase}): la clasificacion de 9 categorias por dia
     * ({@code weekStatus}/{@code todayStatus}/{@code avatarState}) es una regla de negocio
     * nueva, no un finder de lectura — no se inventa (CLAUDE.MD sec. 0.6).
     */
    static final List<String> BLOQUEOS = List.of(
            "weekStatus/todayStatus/avatarState: ningun modulo calcula la clasificacion de 9 "
                    + "categorias por dia que espera HomeSummaryResponse — no es un finder de "
                    + "lectura existente, es una regla de negocio nueva (gap #21, "
                    + "docs/PLAN_INTEGRACION_FRONTEND.md)");

    private final ConsultarPuntajeUseCase consultarPuntajeUseCase;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final HabitosDelDiaFinder habitosDelDiaFinder;
    private final RocasDelDiaFinder rocasDelDiaFinder;
    private final ProximoEventoFinder proximoEventoFinder;
    private final NotificacionesNoLeidasFinder notificacionesNoLeidasFinder;
    private final Clock clock;

    public HomeAgregadoService(ConsultarPuntajeUseCase consultarPuntajeUseCase,
                                ParticipacionProgramaFinder participacionProgramaFinder,
                                HabitosDelDiaFinder habitosDelDiaFinder,
                                RocasDelDiaFinder rocasDelDiaFinder,
                                ProximoEventoFinder proximoEventoFinder,
                                NotificacionesNoLeidasFinder notificacionesNoLeidasFinder,
                                Clock clock) {
        this.consultarPuntajeUseCase = consultarPuntajeUseCase;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.habitosDelDiaFinder = habitosDelDiaFinder;
        this.rocasDelDiaFinder = rocasDelDiaFinder;
        this.proximoEventoFinder = proximoEventoFinder;
        this.notificacionesNoLeidasFinder = notificacionesNoLeidasFinder;
        this.clock = clock;
    }

    @Override
    public ResumenHome consultar(UserId actorId) {
        PuntajeParticipante puntaje = consultarPuntajeUseCase.consultar(actorId, actorId);
        ParticipacionPrograma participacion = requireParticipacion(actorId);

        return new ResumenHome(puntaje.puntosLiga(), puntaje.coherencia(), puntaje.rachaActual(),
                puntaje.rachaMaxima(), participacion.diaPrograma(), participacion.inscrito(),
                participacion.fase(), habitosHoyDe(actorId, participacion.zona()), rocasHoyDe(actorId),
                proximoEventoDe(actorId), notificacionesNoLeidasDe(actorId), BLOQUEOS);
    }

    private ParticipacionPrograma requireParticipacion(UserId actorId) {
        return participacionProgramaFinder.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
    }

    private ResumenHome.HabitosHoyResumen habitosHoyDe(UserId actorId, ZoneId zonaDelParticipante) {
        try {
            LocalDate hoy = LocalDate.ofInstant(clock.now(), zonaDelParticipante);
            List<HabitoDelDiaResumen> tracks = habitosDelDiaFinder.deHoy(actorId, hoy);
            long completados = tracks.stream().filter(t -> "COMPLETADO".equals(t.estado())).count();
            return new ResumenHome.HabitosHoyResumen((int) completados, tracks.size());
        } catch (NoSuchElementException | NotAuthorizedException e) {
            logWidgetDegradado("habitosHoy", e);
            return null;
        }
    }

    private ResumenHome.RocasHoyResumen rocasHoyDe(UserId actorId) {
        List<RocaDelDiaResumen> rocas = rocasDelDiaFinder.deHoy(actorId);
        long completadas = rocas.stream().filter(RocaDelDiaResumen::completada).count();
        return new ResumenHome.RocasHoyResumen((int) completadas, rocas.size());
    }

    private ResumenHome.ProximoEventoResumen proximoEventoDe(UserId actorId) {
        try {
            return proximoEventoFinder.proximoEventoDe(actorId)
                    .map(e -> new ResumenHome.ProximoEventoResumen(e.eventoId(), e.titulo(), e.iniciaEn()))
                    .orElse(null);
        } catch (NoSuchElementException | NotAuthorizedException e) {
            logWidgetDegradado("proximoEvento", e);
            return null;
        }
    }

    private Long notificacionesNoLeidasDe(UserId actorId) {
        try {
            return notificacionesNoLeidasFinder.contarNoLeidas(actorId);
        } catch (NoSuchElementException | NotAuthorizedException e) {
            logWidgetDegradado("notificacionesNoLeidas", e);
            return null;
        }
    }

    private void logWidgetDegradado(String widget, RuntimeException causa) {
        log.warn("[points.HomeAgregadoService] widget {} no aplica para este actor, degradado a null. causa={}",
                widget, causa.getClass().getSimpleName());
    }
}
