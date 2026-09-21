package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * La mitad que faltaba de {@link IncorporarUsuarioAlSoporteUseCase}: saca del circuito de soporte a
 * quien <b>dejo de ser</b> staff administrativo (D-136).
 *
 * <p><b>Por que existe (auditoria de seguridad).</b> El ascenso a ADMIN/ALCHEMIST escribe una fila
 * de participante en la conversacion de soporte de <i>cada</i> aprendiz, y esa fila es exactamente
 * lo que la regla de acceso del modulo aceptaba como prueba. La baja de rol no borraba ninguna: un
 * ex administrador degradado a MENTOR, MENTOR_LEAD o TRAINEE seguia con el chat privado de cada
 * aprendiz en la bandeja, leyendolo, escribiendo en el y recibiendolo en vivo por el WebSocket, sin
 * ninguna forma de sacarlo desde el producto.
 *
 * <p><b>Excepcion ACOTADA a CH-11, no su derogacion.</b> CH-11 ("nada reconcilia participantes,
 * nunca") se justifica en la regla 4: el staff se puede ir, y una sincronizacion
 * <i>"dejalo como deberia estar"</i> le desharia esa salida en el proximo evento. Eso sigue en pie
 * y esto no lo toca: aca no se mete a nadie, no se repone a quien se fue por su cuenta —al que se
 * fue ya no le queda fila que quitar— y no se recompone ninguna conversacion. Lo unico que hace es
 * revocar a quien dejo de cumplir la regla 1 (el aprendiz y los ADMIN/ALCHEMIST activos, nadie
 * mas). Sacar a quien ya no corresponde y reponer a quien se fue son dos operaciones distintas; esta
 * es solo la primera.
 *
 * <p><b>Idempotente, obligatorio.</b> Lo dispara un listener del outbox de Modulith, que entrega
 * al-menos-una-vez y <b>sin orden garantizado</b>: quitar una fila que ya no esta no es un error,
 * es el estado que se buscaba, y correrlo dos veces tiene que dar lo mismo que correrlo una.
 *
 * <p><b>Nunca borra mensajes.</b> Lo que esa persona escribio mientras era staff sigue siendo parte
 * de la historia de la conversacion, igual que en {@link SalirDeConversacionSoporteUseCase}.
 */
public interface RetirarDelSoporteUseCase {

    /**
     * Saca a {@code exStaffId} de toda conversacion de SOPORTE donde no sea el aprendiz dueño.
     *
     * <p>Comprueba el rol <b>vigente</b> antes de tocar nada: si a esta altura la persona vuelve a
     * ser ADMIN/ALCHEMIST, no se quita nada. Sin esa comprobacion, la reentrega tardia de un evento
     * viejo de baja —o su llegada despues de un ascenso posterior, que el outbox no ordena— le
     * borraria las filas a un administrador legitimo.
     */
    void retirarPorBajaDeStaff(UserId exStaffId);
}
