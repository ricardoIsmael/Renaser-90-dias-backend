package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Paso 2 del avatar generico (gap #4): confirma una subida ya hecha a la URL de
 * {@link SolicitarUrlAvatarUseCase} y actualiza {@code usuarios.avatar_url}.
 *
 * <p>Limitacion conocida, documentada a proposito (no es un gap silencioso): a diferencia
 * de `calendar`/`rocks` (que guardan solo la ruta y resuelven una URL de lectura firmada EN
 * CADA respuesta), {@code avatar_url} es un string plano que TODOS los demas modulos
 * (`community`, `chat`, `mentor`, ...) ya consumen directo como URL servible via
 * {@code UserSummary} - cambiar ese contrato para que necesite resolucion rompe esos
 * consumidores, fuera del alcance de este encargo. Por eso esta confirmacion resuelve la
 * URL UNA VEZ, con la validez mas larga razonable, y la persiste tal cual. Igual que el
 * resto del sistema hoy (AlmacenamientoPort solo tiene el adaptador NoOp, D-34: sin
 * credenciales AWS S3 reales todavia), esto es un placeholder consistente con el estado
 * actual - cuando se defina el adaptador real, la via correcta a largo plazo es un bucket
 * de avatares publico (URL permanente) en vez de una URL firmada con vencimiento.
 */
public interface ConfirmarAvatarUseCase {

    void confirmar(ConfirmarAvatarCommand command);

    record ConfirmarAvatarCommand(@NotNull UserId actorId, @NotBlank String bucket, @NotBlank String ruta) {
        public ConfirmarAvatarCommand {
            SelfValidating.validateConstructorArgs(ConfirmarAvatarCommand.class, actorId, bucket, ruta);
        }
    }
}
