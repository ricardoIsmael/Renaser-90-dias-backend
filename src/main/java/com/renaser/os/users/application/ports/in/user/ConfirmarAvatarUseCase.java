package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Paso 2 del avatar generico (gap #4): confirma una subida ya hecha a la URL de
 * {@link SolicitarUrlAvatarUseCase} y actualiza {@code usuarios.avatar_url}.
 *
 * <p>Guarda la URL PERMANENTE del objeto (D-55: el prefijo `avatares/` del bucket es de lectura
 * publica), no una prefirmada. Hasta el 2026-08-31 guardaba una URL de lectura firmada por 7
 * dias: a la semana del ultimo cambio de foto caducaba y no la volvia a firmar nadie, en el
 * perfil y en todas las pantallas que muestran el avatar — muro, comentarios, chat, miembros de
 * celula, testimonios y panel admin, todas via {@code users.api.UserSummary}. Ver E-57 en
 * docs/BITACORA_ERRORES.md.
 *
 * <p>El {@code bucket} y la {@code ruta} viajan en el body por simetria con el resto del patron,
 * pero la ruta que se publica la recalcula el servicio desde el actor: nadie puede confirmar
 * como avatar propio un objeto ajeno.
 */
public interface ConfirmarAvatarUseCase {

    void confirmar(ConfirmarAvatarCommand command);

    record ConfirmarAvatarCommand(@NotNull UserId actorId, @NotBlank String bucket, @NotBlank String ruta) {
        public ConfirmarAvatarCommand {
            SelfValidating.validateConstructorArgs(ConfirmarAvatarCommand.class, actorId, bucket, ruta);
        }
    }
}
