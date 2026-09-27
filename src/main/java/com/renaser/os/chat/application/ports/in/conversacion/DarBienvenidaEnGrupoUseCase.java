package com.renaser.os.chat.application.ports.in.conversacion;

import java.util.UUID;

/**
 * La bienvenida del mentor en el chat del grupo estable a cada aprendiz que se integra (OPE-01-01,
 * D-191), con el texto {@code grupo} de {@code bienvenida/mensajes.yaml}.
 *
 * <p>Idempotente por pertenencia ({@code asignaciones_celula.bienvenida_enviada_en}): se puede llamar
 * con cada cambio del grupo y cada reentrega del outbox. Ni la recepción ni un grupo sin mentor
 * reciben nada. Si un envío falla por otra cosa, LANZA al final, para que el outbox reintente.
 *
 * @return cuántas bienvenidas mandó esta llamada
 */
public interface DarBienvenidaEnGrupoUseCase {

    int darBienvenidas(UUID celulaId);
}
