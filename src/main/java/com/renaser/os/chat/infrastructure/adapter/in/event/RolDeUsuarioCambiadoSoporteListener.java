package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RetirarDelSoporteUseCase;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Un cambio de rol mueve a la persona en los dos sentidos del circuito de soporte (D-136).
 *
 * <p>Quien <b>pasa</b> a ser administrador o alquimista entra a los chats de soporte que ya
 * existen: sin esto, un ascenso lo dejaba viendo solo las conversaciones de los aprendices que
 * entraran despues. Y quien <b>deja</b> de serlo sale de todas — la mitad que faltaba, y que este
 * listener no hacia.
 *
 * <p><b>El agujero que cierra (auditoria de seguridad).</b> Hasta ahora este metodo tiraba
 * {@code rolAnterior} y {@code rolNuevo} y llamaba solo a incorporar, que unicamente suma. Un ex
 * administrador degradado a MENTOR, MENTOR_LEAD o TRAINEE conservaba la fila de participante de la
 * conversacion de soporte de cada aprendiz, y esa fila era toda la prueba que la regla de acceso
 * del modulo pedia: seguia leyendo el chat privado entre cada aprendiz y la administracion,
 * escribiendo en el y recibiendolo en vivo. El javadoc de este listener decia que "nadie confirmo
 * que un cambio de rol deba expulsar a nadie"; la regla 1 —confirmada por el dueño del proyecto—
 * si lo dice: adentro van el aprendiz y los ADMIN/ALCHEMIST <b>activos</b>, nadie mas.
 *
 * <p><b>El listener sigue sin decidir la composicion</b>, que es del dominio: lo unico que lee del
 * evento es la <i>direccion</i> del cambio, que es el dato que el evento viaja justamente para esto
 * ({@code RolDeUsuarioCambiadoEvent}: "entrar al staff y salir del staff no piden lo mismo").
 * Quien corresponde sacar, y si a esta altura corresponde sacar a alguien, lo resuelve
 * {@link RetirarDelSoporteUseCase} contra el rol vigente.
 */
@Component
class RolDeUsuarioCambiadoSoporteListener {

    private final IncorporarUsuarioAlSoporteUseCase incorporarUseCase;
    private final RetirarDelSoporteUseCase retirarUseCase;

    RolDeUsuarioCambiadoSoporteListener(IncorporarUsuarioAlSoporteUseCase incorporarUseCase,
                                        RetirarDelSoporteUseCase retirarUseCase) {
        this.incorporarUseCase = incorporarUseCase;
        this.retirarUseCase = retirarUseCase;
    }

    @ApplicationModuleListener
    void on(RolDeUsuarioCambiadoEvent event) {
        incorporarUseCase.incorporar(event.usuarioId());
        // canManageRoles() ya es exactamente {ADMIN, ALCHEMIST}, el mismo conjunto que
        // ConversacionSoporteService.STAFF_ADMINISTRATIVO: no hace falta un predicado nuevo que
        // despues se desincronice de aquel.
        if (event.rolAnterior().canManageRoles() && !event.rolNuevo().canManageRoles()) {
            retirarUseCase.retirarPorBajaDeStaff(event.usuarioId());
        }
    }
}
