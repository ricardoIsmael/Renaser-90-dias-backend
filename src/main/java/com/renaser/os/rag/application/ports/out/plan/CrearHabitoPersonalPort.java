package com.renaser.os.rag.application.ports.out.plan;

import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para dar de alta un habito PROPIO de la persona (D-229). Lo llama
 * SOLO {@code CrearHabitoPersonalConfirmable}, cuando la persona toca "Confirmar" en la tarjeta:
 * el modelo no tiene ningun camino hasta aca.
 *
 * <p>El adaptador delega en {@code habits.api.HabitosPersonalesPort}, que corre el mismo caso de
 * uso que {@code POST /api/v1/habits}. Lanza las excepciones de ese caso de uso
 * ({@code NoSuchElementException}, {@code NotAuthorizedException}, {@code IllegalArgumentException});
 * traducirlas es de quien llama.
 */
public interface CrearHabitoPersonalPort {

    /** @return el id del habito creado */
    UUID crear(UserId actorId, HabitoPersonalPedido pedido);

    /** La hora mas tarde a la que puede arrancar un habito; la regla es de {@code habits} (D-122). */
    LocalTime ultimaHoraDeInicio();
}
