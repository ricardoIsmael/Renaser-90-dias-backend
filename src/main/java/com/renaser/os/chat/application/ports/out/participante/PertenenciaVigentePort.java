package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * La pertenencia REAL a un grupo, preguntada a quien es su dueño.
 *
 * <p>Existe porque {@code participantes_conversacion} es una proyección, y una proyección que
 * se queda vieja no se limita a mostrar de menos: <b>concede acceso de más</b>. Un mentor que
 * rotó el mes pasado conserva su fila y con ella la puerta abierta al chat de un grupo que ya
 * no acompaña, con todo lo que sus aprendices escriban ahí.
 *
 * <p>Por eso la proyección sigue sirviendo para LISTAR rápido, pero no para AUTORIZAR: cada
 * acción sobre una conversación de grupo revalida contra esta fuente (plan.md §6).
 */
public interface PertenenciaVigentePort {

    /** Si el usuario pertenece hoy al grupo, con cualquier función. */
    boolean perteneceAlGrupo(UUID celulaId, UserId usuarioId);

    /** Todos los que pertenecen hoy: la lista contra la cual se reconcilia. */
    List<UserId> integrantesDelGrupo(UUID celulaId);
}
