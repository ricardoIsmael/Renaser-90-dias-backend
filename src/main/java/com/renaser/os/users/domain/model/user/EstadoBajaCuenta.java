package com.renaser.os.users.domain.model.user;

import java.time.Duration;
import java.time.Instant;

/**
 * Estado de la baja de cuenta solicitada por el propio usuario - logica pura del plazo
 * de gracia, portada 1:1 de features/account-deletion/plazo.ts (backend viejo, Next.js).
 *
 * <p>Google Play (2024) y Apple (guia 5.1.1(v)) exigen que toda app con registro permita
 * borrar la cuenta desde dentro, y que el borrado sea real (no un simple
 * status=INACTIVE). No se borra al confirmar: se marca {@code User.bajaSolicitadaEn} y un
 * cron purga (hard delete) a los {@code diasDeGracia} dias. La gracia protege de dos cosas
 * reales - el arrepentimiento, y que un telefono desbloqueado sin supervision destruya el
 * trabajo de 90 dias -; las dos tiendas aceptan el diferido siempre que el plazo se le
 * informe al usuario.
 *
 * <p>Sin Spring, sin JPA, sin {@code new Date()}/{@code Instant.now()} interno: el instante
 * entra ya resuelto (mismo criterio que {@code shared.domain.Clock} en el resto del
 * dominio), asi que se testea con un simple {@code new} sin levantar nada.
 *
 * @param bajaPendiente  true si hay una solicitud de baja activa (no purgada ni cancelada)
 * @param solicitadaEn   instante en que se pidio la baja, null si no hay solicitud
 * @param purgaEl        instante en que el cron purgara la cuenta, null si no hay solicitud
 * @param diasRestantes  dias que faltan para la purga, redondeado HACIA ARRIBA (a quien le
 *                       quedan 30 minutos hay que decirle "1 dia", no "0 dias", que se lee
 *                       como "ya paso" y es justo el momento en que mas importa que entienda
 *                       que aun puede cancelar). {@code null} si no hay solicitud o si la
 *                       gracia ya vencio (el cron la purgara en su proxima pasada)
 * @param diasDeGracia   dias de gracia configurados (`renaser.users.account-deletion.grace-period-days`)
 */
public record EstadoBajaCuenta(boolean bajaPendiente, Instant solicitadaEn, Instant purgaEl, Long diasRestantes,
                                int diasDeGracia) {

    public static EstadoBajaCuenta sinSolicitud(int diasDeGracia) {
        return new EstadoBajaCuenta(false, null, null, null, diasDeGracia);
    }

    /** {@code solicitadaEn} null delega en {@link #sinSolicitud(int)}. */
    public static EstadoBajaCuenta de(Instant solicitadaEn, Instant ahora, int diasDeGracia) {
        if (solicitadaEn == null) {
            return sinSolicitud(diasDeGracia);
        }
        Instant purgaEl = solicitadaEn.plus(Duration.ofDays(diasDeGracia));
        long restanteMs = Duration.between(ahora, purgaEl).toMillis();
        Long diasRestantes = restanteMs <= 0 ? null : diasHaciaArriba(restanteMs);
        return new EstadoBajaCuenta(true, solicitadaEn, purgaEl, diasRestantes, diasDeGracia);
    }

    /** Equivalente entero de {@code Math.ceil(restanteMs / MS_POR_DIA)} sin pasar por double. */
    private static long diasHaciaArriba(long restanteMs) {
        long msPorDia = Duration.ofDays(1).toMillis();
        return (restanteMs + msPorDia - 1) / msPorDia;
    }
}
