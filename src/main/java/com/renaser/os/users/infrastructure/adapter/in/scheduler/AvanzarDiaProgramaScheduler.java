package com.renaser.os.users.infrastructure.adapter.in.scheduler;

import com.renaser.os.users.application.ports.in.participante.AvanzarDiaProgramaUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido del reloj del programa de 90 dias (D-67; el cron que faltaba, señalado como
 * bloqueante en docs/MODULO_PHASECONTRACTS.md §0.2). {@code @EnableScheduling} ya esta
 * declarado globalmente por `points` (D-P4, `PointsSchedulingConfig`) — no hace falta
 * repetirlo aca.
 *
 * <p><b>Cada hora, no una vez por noche (V20, BITACORA E-91).</b> La version original
 * corria a las 04:50 UTC, elegida para caer entre los otros crons nocturnos. El problema
 * es que la medianoche local no existe a una hora UTC fija: para America/Lima (UTC-5 — el
 * default de la columna `timezone` y la zona de todo el padron) las 04:50 UTC son las
 * 23:50 del dia ANTERIOR, diez minutos antes del dia que habia que contar. Efecto real:
 * un aprendiz veia "dia 0" durante todo su Dia 1, y de ahi en mas el reloj quedaba
 * corrido un dia entero para siempre. No es un caso de borde de una zona rara: es toda
 * America.
 *
 * <p>Correr cada hora es la unica forma de alcanzar la medianoche local de cualquier zona
 * sin mantener una tabla de offsets. Es barato: el dominio devuelve {@code false} cuando
 * no hay nada que cambiar, asi que se escribe como mucho una fila por participante por
 * dia; las otras 23 corridas leen y no guardan nada.
 *
 * <p><b>Ya no hay dependencia de orden con los otros crons.</b> La version vieja tenia que
 * correr ANTES de {@code habits.ExpirarRegistrosScheduler} (05:00) y
 * {@code habits.GenerarTracksDelDiaScheduler} (05:02) porque ambos leen `dia_programa` y
 * un avance perdido los desincronizaba. Con el dia DERIVADO de las fechas
 * ({@code ParticipacionPrograma.diaProgramaDerivado}) el valor es correcto lo hayan
 * corrido o no: quien lea tarde lee bien igual.
 *
 * <p><b>{@code @SchedulerLock} (C-5).</b> Se conserva, aunque el modelo derivado ya hace
 * inofensiva la doble ejecucion (dos instancias calculando el mismo dia escriben el mismo
 * valor — el check-then-act de C-2/C-12 dejo de aplicar aca). Sigue puesto para no
 * desperdiciar N barridos identicos del padron completo cada hora, y porque
 * docs/informes/auditoria-fixes/C-5.md lo exige para todo {@code @Scheduled}.
 */
@Component
public class AvanzarDiaProgramaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvanzarDiaProgramaScheduler.class);

    private final AvanzarDiaProgramaUseCase avanzarDiaProgramaUseCase;

    public AvanzarDiaProgramaScheduler(AvanzarDiaProgramaUseCase avanzarDiaProgramaUseCase) {
        this.avanzarDiaProgramaUseCase = avanzarDiaProgramaUseCase;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    @SchedulerLock(name = "users-avanzar-dia-programa",
            lockAtMostFor = "${renaser.scheduling.shedlock.users-avanzar-dia-programa.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.users-avanzar-dia-programa.lock-at-least-for:PT30S}")
    public void avanzarDiaDeParticipantesActivos() {
        var resultado = avanzarDiaProgramaUseCase.avanzarParticipantesActivos();
        log.debug("[users.AvanzarDiaProgramaScheduler] {} participante(s) evaluado(s), {} sincronizado(s)",
                resultado.evaluados(), resultado.avanzados());
    }
}
