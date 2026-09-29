package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Le da su chat de soporte a los aprendices que ya estaban cuando la funcion no existia (D-136), y
 * a cualquiera que haya entrado por un camino que no lo creo (D-224).
 *
 * <p><b>Corregido 2026-09-29 (D-224).</b> Este javadoc decía: «Por que un endpoint de administracion
 * y no un barrido al arrancar. Una corrida masiva en el arranque es justo lo que nadie puede deshacer
 * [...] Un endpoint explicito lo dispara una persona, una vez, cuando decide». El endpoint nunca tuvo
 * un botón en el panel y no hay constancia de que se haya llamado en producción: el 29-09 el dueño
 * reportó que los aprendices anteriores no tenían su chat de soporte ni su chat de dos. Desde D-224 el mismo relleno corre solo
 * ({@link #rellenarPendientes}, desde {@code CompletarChatsDeAprendicesUseCase}): "todo aprendiz
 * inscrito y activo tiene su soporte" es una regla derivable del padrón (regla 02 §2), y el barrido
 * solo crea lo que la regla ya exige, sin avisar a nadie. El endpoint se conserva.
 *
 * <p><b>Idempotente.</b> Correrlo dos veces crea cero la segunda vez: cada aprendiz cuya
 * conversacion ya existe se cuenta en {@code yaExistian} y no se toca.
 *
 * <p><b>No reconcilia participantes.</b> Una conversacion que ya existe se deja como esta, aunque
 * le falte un miembro del staff: el staff se puede ir (regla del dueño del proyecto) y volver a
 * meterlo seria desobedecer esa salida. Al staff nuevo lo suma su propio camino
 * ({@link IncorporarUsuarioAlSoporteUseCase}), no este.
 *
 * <p><b>Sin bienvenida.</b> Ninguno de los tres métodos publica {@code SoporteDeAprendizNacioEvent}:
 * la bienvenida es para quien acaba de entrar, no para quien ya lleva días en el programa.
 */
public interface RellenarConversacionesDeSoporteUseCase {

    /** El relleno pedido desde el panel: exige un ADMIN/ALCHEMIST activo. */
    ResultadoRelleno rellenar(UserId actorId);

    /** El mismo relleno, disparado por el sistema (D-224): sin actor, mismas reglas. */
    ResultadoRelleno rellenarPendientes();

    /**
     * El soporte de UNA persona, si es aprendiz inscrito con la cuenta activa y todavía no lo tiene
     * (D-224: al reactivarse una cuenta).
     *
     * @return {@code true} si lo creó
     */
    boolean rellenarDe(UserId aprendizId);

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
