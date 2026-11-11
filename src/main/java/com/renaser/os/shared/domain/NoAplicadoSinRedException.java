package com.renaser.os.shared.domain;

import java.util.Objects;

/**
 * Spec 048 (FR-019, D13, D14): un envío hecho sin red que, al llegar, NO se aplicó. No es un error de la persona ni del
 * servidor: es el resultado de la regla de choques («lo más nuevo gana») o de que lo que tocaba ya no existe. {@code
 * GlobalExceptionHandler} lo traduce a <b>409</b> con {@code codigo} y {@code message} (formato {@code ApiErrorConCodigo}).
 *
 * <p>Los casos de uso que la lanzan van con {@code @Transactional(noRollbackFor = NoAplicadoSinRedException.class)}: el envío
 * queda registrado como {@code NO_APLICADO} en {@code envios_sin_red} (V190) y un reintento devuelve el mismo resultado. Se
 * lanza siempre desde el propio caso de uso, antes de escribir nada, nunca desde otro bean transaccional (marcaría la
 * transacción para deshacer).
 */
public class NoAplicadoSinRedException extends RuntimeException {

    private final CodigoNoAplicado codigo;

    public NoAplicadoSinRedException(CodigoNoAplicado codigo) {
        this(codigo, codigo.mensaje());
    }

    public NoAplicadoSinRedException(CodigoNoAplicado codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoNoAplicado codigo() {
        return codigo;
    }
}
