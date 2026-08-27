package com.renaser.os.points.application.ports.in.home;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/home} — agregador del resumen del dia para la pantalla de Inicio
 * (gap #21, docs/PLAN_INTEGRACION_FRONTEND.md §5). {@code points} compone lo que HOY tiene
 * contrato publico, usando los finders ya expuestos en {@code @NamedInterface} de cada
 * modulo dueno (CLAUDE.MD sec. 4.3/5.1):
 *
 * <ul>
 *   <li>{@code puntosLiga}/{@code coherencia}/{@code rachaActual}/{@code rachaMaxima}: dominio
 *       propio de {@code points} ({@link com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase}).</li>
 *   <li>{@code diaPrograma}/{@code inscrito}/{@code fase}: {@code users.api.ParticipacionProgramaFinder}.</li>
 *   <li>{@code habitosHoy}: {@code points.api.HabitosDelDiaFinder} (D-50), implementado por `habits` — DIP,
 *       ver javadoc de esa interfaz (`habits`/`rocks`/`calendar`/`notifications` ya dependen de `points`
 *       para otorgar puntos/leer al actor; `points` no puede depender de ellos en la otra direccion sin
 *       crear un ciclo que Spring Modulith rechaza).</li>
 *   <li>{@code rocasHoy}: {@code points.api.RocasDelDiaFinder}, implementado por `rocks`.</li>
 *   <li>{@code proximoEvento}: {@code points.api.ProximoEventoFinder}, implementado por `calendar`.</li>
 *   <li>{@code notificacionesNoLeidas}: {@code points.api.NotificacionesNoLeidasFinder}, implementado por
 *       `notifications`.</li>
 * </ul>
 *
 * <p><b>Que sigue en {@link ResumenHome#bloqueos()} y por que:</b> el {@code HomeSummaryResponse}
 * que espera hoy la app movil (`C:\renaserPlayStore\src\types\home.ts`) tiene ademas
 * {@code weekStatus}/{@code todayStatus}/{@code avatarState} — una clasificacion de CADA dia de
 * la semana en una de 9 categorias (perfect/excellent/good/ok/bad/critical/empty/rest/future) mas
 * el "mood" de avatar derivado de eso. Ningun modulo calcula ese algoritmo hoy (no es un simple
 * finder de lectura, es una regla de negocio nueva sobre el historial semanal) — inventarlo violaria
 * CLAUDE.MD sec. 0.6. Sigue documentado como bloqueo explicito en vez de fabricarse.
 *
 * <p><b>Nombres de campo:</b> deliberadamente en español, seudo por continuidad con
 * {@code puntosLiga}/{@code coherencia}/{@code rachaActual}/{@code rachaMaxima} (ya publicados).
 * El remapeo 1:1 a los nombres ingleses que espera {@code HomeSummaryResponse} hoy
 * ({@code programDay}, {@code currentPhase}, {@code habitsToday: {completed,total}}, etc.) es
 * trabajo de Fase 1 (remapeo de contrato) — el propio gap #21 documenta que ese agregador final
 * "probablemente no deberia vivir en points", decision no tomada todavia.
 *
 * <p><b>Fallas parciales:</b> {@code habitosHoy}/{@code proximoEvento}/{@code notificacionesNoLeidas}
 * pueden venir {@code null} si el finder correspondiente determina que el dato no aplica para este
 * actor (ej.: no es un TRAINEE con progreso de habitos todavia, o no tiene eventos visibles) — mismo
 * criterio de "falla parcial, no error total" que ya documenta el contrato movil
 * ({@code HomeSummaryResponse}: "the four widgets are independent and come back null on partial
 * failure"). Ver javadoc de {@link com.renaser.os.points.application.services.HomeAgregadoService}
 * para el detalle de que excepcion de cada finder se trata como falla parcial.
 */
public interface ConsultarResumenHomeUseCase {

    ResumenHome consultar(UserId actorId);

    record ResumenHome(int puntosLiga, BigDecimal coherencia, int rachaActual, int rachaMaxima,
                        int diaPrograma, boolean inscrito, FasePrograma fase,
                        HabitosHoyResumen habitosHoy, RocasHoyResumen rocasHoy,
                        ProximoEventoResumen proximoEvento, Long notificacionesNoLeidas,
                        List<String> bloqueos) {

        /** Espejo de {@code TodayCounts} del contrato movil ({completed,total}). */
        public record HabitosHoyResumen(int completados, int total) {
        }

        /** Espejo de {@code TodayCounts} del contrato movil ({completed,total}). */
        public record RocasHoyResumen(int completadas, int total) {
        }

        public record ProximoEventoResumen(UUID eventoId, String titulo, Instant iniciaEn) {
        }
    }
}
