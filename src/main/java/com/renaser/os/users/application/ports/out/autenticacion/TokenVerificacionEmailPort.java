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
     * Busca el token y lo borra en la MISMA operacion atomica (GETDEL): un solo uso, igual que
     * {@link TokenResetContrasenaPort#consumir}. Vacio si no existe, ya vencio, o ya se
     * consumio antes.
     *
     * @return el email que este token certifica verificado, o vacio si el token no es valido
     */
    Optional<String> consumir(String token);
}
