package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Segundo paso: comprueba el codigo de 6 digitos y, si coincide, emite el
 * {@code verificationToken} opaco que {@code SubmitAccountRequestUseCase} va a exigir. El
 * cliente guarda ese token (no el codigo) hasta mandar el resto del formulario.
 */
public interface ConfirmarCodigoVerificacionEmailUseCase {

    /**
     * @throws com.renaser.os.shared.domain.CodigoVerificacionInvalidoException si el codigo no
     * coincide, ya vencio, o se agotaron los intentos permitidos
     */
    ResultadoVerificacion confirmar(ConfirmarCodigoVerificacionEmailCommand command);

    /** El codigo siempre son 6 digitos — la validacion de forma falla rapido con un 400 antes
     * de gastar un intento contra el codigo real guardado en Redis. */
    record ConfirmarCodigoVerificacionEmailCommand(@NotBlank @Email String email,
                                                     @NotBlank @Pattern(regexp = "\\d{6}") String codigo) {
        public ConfirmarCodigoVerificacionEmailCommand {
            SelfValidating.validateConstructorArgs(ConfirmarCodigoVerificacionEmailCommand.class, email, codigo);
        }
    }

    record ResultadoVerificacion(String verificationToken) {
    }
}
