package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Pone a un usuario donde le toca dentro del circuito de soporte (D-136). Una sola puerta, porque
 * desde afuera es un solo hecho —"apareci o cambie de rol"— y quien decide que significa eso es el
 * dominio, no el adaptador que avisa:
 *
 * <ul>
 *   <li><b>Aprendiz inscrito en el programa</b> -> se le crea SU conversacion de soporte, con todo
 *       el staff administrativo activo adentro.</li>
 *   <li><b>ADMIN / ALCHEMIST activo</b> -> se lo suma a las conversaciones de soporte que YA
 *       existen, para que no quede ciego a las de los aprendices anteriores a el.</li>
 *   <li><b>Cualquier otro caso</b> (mentor, lider de mentores, suspendido, aprendiz todavia sin
 *       fila de programa) -> no hace nada. No es un error: es la regla.</li>
 * </ul>
 *
 * <p><b>Idempotente.</b> Lo disparan listeners del outbox de Modulith, que entrega al-menos-una-vez
 * y sin orden garantizado: correr esto dos veces tiene que dar el mismo resultado que correrlo una.
 * La segunda creacion la corta el UNIQUE de {@code conversaciones.clave_directa} y el alta repetida
 * de un participante la corta la PK de {@code participantes_conversacion}.
 *
 * <p><b>Lo que NO hace, a proposito: no reconcilia.</b> Nunca vuelve a meter a un miembro del staff
 * que se fue de una conversacion. El dueño del proyecto decidio que el staff se puede ir; una
 * sincronizacion "dejalo como deberia estar" le desharia esa salida en el proximo evento.
 */
public interface IncorporarUsuarioAlSoporteUseCase {

    void incorporar(UserId usuarioId);
}
