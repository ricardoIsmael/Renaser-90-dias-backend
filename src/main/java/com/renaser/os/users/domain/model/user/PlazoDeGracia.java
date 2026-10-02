package com.renaser.os.users.domain.model.user;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuanto dura una cuenta cerrada antes de borrarse para siempre (D-243: 30 dias, decision del dueño;
 * {@code renaser.users.account-deletion.grace-period-days}). Se cuenta en instantes desde el cierre
 * —30 x 24 h—, no en dias calendario de ninguna zona: el borrado no depende de donde viva la persona.
 * Es el UNICO lugar donde se suma el plazo; todo «se borra el …» sale de aca.
 */
public record PlazoDeGracia(int dias) {

    public PlazoDeGracia {
        if (dias < 1) {
            throw new IllegalArgumentException("El plazo de gracia tiene que ser de al menos un dia");
        }
    }

    /** {@code null} si la cuenta no esta cerrada. */
    public Instant seBorraEl(Instant cerradaEn) {
        return cerradaEn == null ? null : cerradaEn.plus(Duration.ofDays(dias));
    }

    /** Las cuentas cerradas en o antes de este instante ya cumplieron la gracia. */
    public Instant corteParaBorrar(Instant ahora) {
        return ahora.minus(Duration.ofDays(dias));
    }

    public EstadoBajaCuenta estadoDe(Instant cerradaEn, Instant ahora) {
        return EstadoBajaCuenta.de(cerradaEn, ahora, dias);
    }
}
