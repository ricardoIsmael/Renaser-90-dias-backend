package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import com.renaser.os.shared.domain.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hace regir los cambios de horario que quedaron programados (§12.1, "no se improvisa el dia").
 * Adaptador propio y no una linea mas dentro de {@code ExpirarRegistrosScheduler}: cerrar el dia
 * anterior y abrir la configuracion del dia nuevo son dos responsabilidades distintas, con dos
 * casos de uso distintos (SRP, CLAUDE.MD §5.4.8) — mezclarlas obligaria a que un fallo de una
 * arrastre a la otra.
 *
 * <p><b>Por que 04:40 UTC:</b> el barrido nocturno de {@code habits} corre a las 05:00 UTC
 * (~00:00 Lima, hora heredada del cron viejo). La promocion tiene que correr ANTES: el orden
 * conceptual del amanecer es "primero queda vigente el horario nuevo, despues se cierra lo
 * vencido y se arma el dia". Dos {@code @Scheduled} con el mismo cron no garantizan orden
 * alguno — el pool de Spring puede ejecutarlos en paralelo — asi que la separacion tiene que
 * ser explicita en la hora. 04:40 deja 20 minutos de margen y no pisa las 04:30 que ya usa la
 * purga de {@code notifications}.
 *
 * <p>Contrapartida asumida y acotada: {@code fecha_efectiva} se calcula en la zona del
 * participante, pero el barrido compara contra la fecha UTC del reloj. Para Lima (UTC-5) eso
 * adelanta el cambio unos 20 minutos (23:40 hora local del dia anterior) — una franja en la que
 * ninguna ventana de habito esta viva. Un participante en una zona por delante de UTC recibe el
 * cambio dentro del mismo dia efectivo, nunca antes de su vispera.
 *
 * <p><b>C-5 (docs/informes/auditoria-fixes/C-5.md): {@code @SchedulerLock}, no opcional.</b>
 * {@code PromocionCambioHorarioService.promoverLosQueRigenEn} lee los pendientes
 * ({@code queYaRigenEn}) SIN {@code FOR UPDATE}, y {@code aplicarAhora}/{@code registrar}
 * (historial) son INSERT puros, sin guarda de dominio contra re-aplicar el mismo pendiente
 * — a diferencia de {@code RegistroHabito.expirar()}/{@code RachaSinCelular.expirar()} (que
 * son no-op o lanzan si ya no corresponde), acá dos instancias que lean el mismo pendiente
 * antes de que cualquiera lo borre APLICAN el cambio dos veces y, peor, INSERTAN dos filas en
 * {@code historial_cambios_horario} — la tabla que cobra el cupo semanal de cambios de
 * horario (ver javadoc de la clase): un aprendiz pierde cupo que nunca gastó.
 */
@Component
class PromoverCambiosHorarioScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromoverCambiosHorarioScheduler.class);

    private final PromoverCambiosHorarioProgramadosUseCase promoverUseCase;
    private final Clock clock;

    PromoverCambiosHorarioScheduler(PromoverCambiosHorarioProgramadosUseCase promoverUseCase, Clock clock) {
        this.promoverUseCase = promoverUseCase;
        this.clock = clock;
    }

    /**
     * {@code lockAtMostFor} por defecto 15 minutos: cada pendiente se promueve en su propia
     * transacción corta (solo escrituras a Postgres, sin IA ni llamada externa), así que aun
     * con miles de cambios pendientes el barrido real tarda segundos-a-pocos-minutos: 15 min
     * es margen generoso sin dejar el lock preso casi un día completo (el próximo intento
     * de este cron es recién mañana a la misma hora) si el proceso muere a mitad de barrido.
     */
    @Scheduled(cron = "0 40 4 * * *", zone = "UTC")
    @SchedulerLock(name = "habits-promover-cambios-horario",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-promover-cambios-horario.lock-at-most-for:PT15M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-promover-cambios-horario.lock-at-least-for:PT10S}")
    public void ejecutar() {
        int promovidos = promoverUseCase.promoverLosQueRigenEn(clock.today());
        log.info("[habits.PromoverCambiosHorarioScheduler] {} cambio(s) de horario programados pasaron a regir",
                promovidos);
    }
}
