package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;

/**
 * Publicado cuando un administrador le cambia el ESTADO a una cuenta
 * ({@code StaffAdminService.updateStatus}). Solo se publica si el estado REALMENTE cambio:
 * reasignar el mismo estado no es una novedad y no tiene por que despertar a ningun modulo
 * — mismo criterio que {@link RolDeUsuarioCambiadoEvent}.
 *
 * <p><b>Por que existe.</b> Suspender es la revocacion fuerte del sistema
 * ({@code UpdateUserStatusUseCase}: "revoca TODAS las sesiones del usuario en el acto"), pero
 * hasta ahora `users` no publicaba nada al cambiar de estado, asi que ningun otro modulo podia
 * enterarse: la revocacion alcanzaba solo a lo que vive dentro de `users` (las sesiones de Spring
 * Session) y cualquier credencial de entrega guardada en otro modulo sobrevivia intacta. El
 * primer consumidor es {@code notifications}, que con esto borra los tokens push de la cuenta
 * suspendida igual que {@code GestionSesionesService} borra sus sesiones.
 *
 * <p>Viaja el estado anterior ademas del nuevo por la misma razon que el evento de rol: quien
 * reacciona casi siempre necesita saber en que direccion se movio — suspender y reactivar no
 * piden lo mismo.
 */
public record EstadoDeCuentaCambiadoEvent(UserId usuarioId, UserStatus estadoAnterior, UserStatus estadoNuevo,
                                           Instant occurredAt) implements DomainEvent {
}
