package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Crea (o completa) el chat de soporte cuando entra alguien nuevo — D-136.
 *
 * <p><b>Por que {@link UsuarioRegistradoEvent} y no otro.</b> El pedido es que el chat nazca
 * cuando el aprendiz <b>entra al programa</b>, no cuando se registra sin aprobar. Este evento es
 * exactamente ese momento y no otro:
 *
 * <ul>
 *   <li>Registrarse NO lo publica. Desde que la contraseña se captura en el formulario, la fila de
 *       `usuarios` nace en el alta, en estado INACTIVE, y ahi no se publica nada.</li>
 *   <li>{@code AccountRequestService.approve} lo publica <b>despues</b> de escribir
 *       {@code ParticipacionPrograma.inscribirTraineeAprobado(...)} en la MISMA transaccion. Como
 *       {@code @ApplicationModuleListener} corre despues del commit, cuando esto se ejecuta la fila
 *       de `participantes_programa` ya existe y se puede consultar.</li>
 *   <li>{@code UserAccountService.invite}/{@code inviteStaff} lo publican tambien, y por eso este
 *       mismo camino sirve para el ADMIN/ALCHEMIST recien invitado: se lo suma a las
 *       conversaciones de soporte que ya hay.</li>
 * </ul>
 *
 * <p>Se eligio no crear un evento nuevo ("AprendizIngresoAlPrograma") habiendo uno que ya marca
 * ese instante: dos eventos para el mismo hecho se desincronizan en cuanto alguien agrega un
 * tercer camino de alta y se acuerda de publicar solo uno.
 *
 * <p>Va aparte de {@code UsuarioRegistradoChatListener} (auto-join a GLOBAL) a proposito: son dos
 * reglas independientes, y el outbox de Modulith registra y reintenta cada suscripcion por
 * separado — si el soporte falla, el alta en GLOBAL no se rehace.
 */
@Component
class UsuarioRegistradoSoporteListener {

    private final IncorporarUsuarioAlSoporteUseCase incorporarUseCase;

    UsuarioRegistradoSoporteListener(IncorporarUsuarioAlSoporteUseCase incorporarUseCase) {
        this.incorporarUseCase = incorporarUseCase;
    }

    @ApplicationModuleListener
    void on(UsuarioRegistradoEvent event) {
        incorporarUseCase.incorporar(event.usuarioId());
    }
}
