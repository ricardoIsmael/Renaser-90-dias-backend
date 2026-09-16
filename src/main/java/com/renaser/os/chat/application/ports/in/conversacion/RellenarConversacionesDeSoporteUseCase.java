package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Le da su chat de soporte a los aprendices que ya estaban cuando la funcion no existia (D-136).
 *
 * <p><b>Por que un endpoint de administracion y no un barrido al arrancar.</b> Una corrida masiva
 * en el arranque es justo lo que nadie puede deshacer: crea N conversaciones en todo entorno que
 * levante —incluido el de un desarrollador o un test— y si estuviera mal, para cuando alguien lo
 * nota ya paso. Un endpoint explicito lo dispara una persona, una vez, cuando decide, y la
 * respuesta dice cuantas creo y cuantas ya estaban.
 *
 * <p><b>Idempotente.</b> Correrlo dos veces crea cero la segunda vez: cada aprendiz cuya
 * conversacion ya existe se cuenta en {@code yaExistian} y no se toca.
 *
 * <p><b>No reconcilia participantes.</b> Una conversacion que ya existe se deja como esta, aunque
 * le falte un miembro del staff: el staff se puede ir (regla del dueño del proyecto) y volver a
 * meterlo seria desobedecer esa salida. Al staff nuevo lo suma su propio camino
 * ({@link IncorporarUsuarioAlSoporteUseCase}), no este.
 */
public interface RellenarConversacionesDeSoporteUseCase {

    ResultadoRelleno rellenar(UserId actorId);

    /**
     * @param aprendicesRevisados el padron de aprendices activos que se miro
     * @param creadas             conversaciones de soporte que no existian y ahora si
     * @param yaExistian          aprendices que ya tenian la suya (no se tocaron)
     * @param fallidas            aprendices que fallaron; el barrido sigue con el resto y el
     *                            detalle queda en el log. Nunca se traga el numero: si esto no es
     *                            cero, la corrida quedo incompleta y hay que volver a llamarla.
     */
    record ResultadoRelleno(int aprendicesRevisados, int creadas, int yaExistian, int fallidas) {
    }
}
