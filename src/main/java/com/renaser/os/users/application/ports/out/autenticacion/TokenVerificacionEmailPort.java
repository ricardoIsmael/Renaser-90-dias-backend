package com.renaser.os.users.application.ports.out.autenticacion;

import java.time.Duration;
import java.util.Optional;

/**
 * Token opaco que certifica "este email ya se verifico" (2026-08-27) — se emite UNA vez que
 * {@link CodigoVerificacionEmailPort#verificarCodigo} da exito, y {@code SubmitAccountRequestUseCase}
 * lo exige y lo consume al mandar el alta. Separado del codigo de 6 digitos a proposito: el
 * codigo es para que una PERSONA lo tipee (corto, memorizable); este token es para que el
 * CLIENTE lo guarde y lo reenvie con el resto del formulario (alta entropia, no memorizable,
 * mismo criterio que {@link TokenResetContrasenaPort}).
 */
public interface TokenVerificacionEmailPort {

    /** Token opaco de alta entropia (256 bits, {@code SecureRandom}), nunca derivado del email. */
    String generar(String email, Duration vigencia);

    /**
     * Busca el token SIN borrarlo, solo para validar antes de hacer trabajo que puede fallar.
     *
     * <p><b>Por que existe (E-152).</b> El alta consumia el token al principio, y como Redis no
     * participa de la transaccion de Postgres, cuando el guardado fallaba la base se deshacia
     * <b>pero el token quedaba gastado</b>. La persona reintentaba con su codigo y le decia "el
     * codigo no es valido o ya vencio", sin haber hecho nada mal. Con esto se valida primero sin
     * efecto y se consume {@link #consumir} recien cuando la transaccion ya comiteo.
     *
     * @return el email que este token certifica verificado, o vacio si el token no es valido
     */
    Optional<String> emailDe(String token);

    /**
     * Busca el token y lo borra en la MISMA operacion atomica (GETDEL): un solo uso, igual que
     * {@link TokenResetContrasenaPort#consumir}. Vacio si no existe, ya vencio, o ya se
     * consumio antes.
     *
     * @return el email que este token certifica verificado, o vacio si el token no es valido
     */
    Optional<String> consumir(String token);
}
