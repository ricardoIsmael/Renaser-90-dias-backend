package com.renaser.os.chat.application.ports.in.conversacion;

import java.util.UUID;

/**
 * La bienvenida en el chat del grupo estable a cada aprendiz que se integra (OPE-01-01, D-191), con
 * el texto {@code grupo} de {@code bienvenida/mensajes.yaml}, firmada por el programa (D-204).
 *
 * <p>Idempotente por pertenencia ({@code asignaciones_celula.bienvenida_enviada_en}): se puede llamar
 * con cada cambio del grupo y cada reentrega del outbox. Ni la recepción ni un grupo sin mentor
 * reciben nada. Si un envío falla por otra cosa, LANZA al final, para que el outbox reintente.
 *
 * <p>Apagada salvo {@code BIENVENIDA_ACTIVA=true} (D-204): apagada no consulta ni marca. Encendida,
 * solo la recibe quien entró al grupo hace menos de 48 h: sin bienvenidas atrasadas.
 *
 * @return cuántas bienvenidas mandó esta llamada
 */
public interface DarBienvenidaEnGrupoUseCase {

    int darBienvenidas(UUID celulaId);
}
