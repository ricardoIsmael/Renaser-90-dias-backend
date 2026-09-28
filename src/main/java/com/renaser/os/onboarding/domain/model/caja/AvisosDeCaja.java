package com.renaser.os.onboarding.domain.model.caja;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Qué avisos le toca dar al barrido horario sobre una caja (spec §5), y cuáles ya se dieron. Cada aviso
 * dado deja su marca ({@code AVISO_*}) en el envío: correr el barrido dos veces no avisa dos veces.
 *
 * <p>Los plazos cuentan desde el instante en que se marcó enviada (72 y 120 horas), no por días del
 * calendario: no dependen de la zona de nadie.
 */
public final class AvisosDeCaja {

    public static final Duration RECORDATORIO_AL_APRENDIZ = Duration.ofDays(3);
    public static final Duration AVISO_AL_ADMIN = Duration.ofDays(5);

    private AvisosDeCaja() {
    }

    /** Las marcas de aviso que faltan dar HOY, en el orden en que se dan. */
    public static List<TipoPasoCaja> debidos(CajaRenaser caja) {
        List<TipoPasoCaja> debidos = new ArrayList<>();
        if (caja.estado() == EstadoCaja.POR_REVISAR) {
            siFalta(caja, TipoPasoCaja.AVISO_EN_REVISION, debidos);
        }
        if (caja.estado() == EstadoCaja.ENVIADA) {
            Instant enviada = caja.ultimo(TipoPasoCaja.ENVIADA).orElseThrow().en();
            if (!caja.ahora().isBefore(enviada.plus(RECORDATORIO_AL_APRENDIZ))) {
                siFalta(caja, TipoPasoCaja.AVISO_RECORDATORIO, debidos);
            }
            if (!caja.ahora().isBefore(enviada.plus(AVISO_AL_ADMIN))) {
                siFalta(caja, TipoPasoCaja.AVISO_SIN_CONFIRMAR, debidos);
            }
        }
        return debidos;
    }

    /** La marca de ese aviso en el envío actual, lista para guardar. */
    public static PasoDeCaja marca(CajaRenaser caja, TipoPasoCaja aviso) {
        return new PasoDeCaja(caja.aprendizId(), caja.envioActual(), aviso, caja.ahora(), null, null);
    }

    private static void siFalta(CajaRenaser caja, TipoPasoCaja aviso, List<TipoPasoCaja> debidos) {
        if (caja.ultimo(aviso).isEmpty()) {
            debidos.add(aviso);
        }
    }
}
