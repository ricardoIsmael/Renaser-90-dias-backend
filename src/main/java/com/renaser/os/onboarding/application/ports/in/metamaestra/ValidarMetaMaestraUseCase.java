package com.renaser.os.onboarding.application.ports.in.metamaestra;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Recibe la Meta Maestra escrita (Diseno de Destino) y la acepta.
 *
 * <p><b>Ya no hay veredicto de IA (decision del dueno, 2026-09-03).</b> Este paso era el
 * "filtro de las 6 Ps": mandaba el texto a un modelo y devolvia aprobada o rechazada con la
 * lista de lo que faltaba. Se saco junto con las demas validaciones automaticas sobre el
 * trabajo de una persona, hasta que exista una via de aprendizaje automatico que las
 * sostenga. El texto pasa: nadie lo juzga.
 *
 * <p>Queda entonces un unico control, y no es de IA: que el actor exista y no este
 * suspendido. El limite de 3000 caracteres y el no-vacio siguen siendo del comando, que se
 * valida solo — mismo contrato que el backend viejo ({@code ValidateSmartTextInput} en
 * RenaserBack/src/features/onboarding/schema.ts).
 *
 * <p>El endpoint se conserva, y su forma de respuesta tambien, porque hay un cliente que ya
 * la consume ({@code SixPsValidation}). Desde afuera se ve igual: siempre aceptada, sin Ps
 * faltantes y sin revision pendiente.
 */
public interface ValidarMetaMaestraUseCase {

    /**
     * Acepta la meta. No devuelve nada porque no hay nada que dictaminar: si el actor esta
     * habilitado y el texto cumple el largo, el paso esta cumplido.
     */
    void aceptar(ValidarMetaMaestraCommand command);

    record ValidarMetaMaestraCommand(@NotNull UserId actorId, @NotBlank @Size(max = 3000) String texto) {

        public ValidarMetaMaestraCommand {
            SelfValidating.validateConstructorArgs(ValidarMetaMaestraCommand.class, actorId, texto);
        }
    }
}
