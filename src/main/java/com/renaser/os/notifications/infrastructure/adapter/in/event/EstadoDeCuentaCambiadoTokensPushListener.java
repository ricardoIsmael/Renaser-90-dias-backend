package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.tokenpush.RevocarTokensPushUseCase;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Cuando una cuenta deja de dar acceso, se le caen tambien los destinos de push.
 *
 * <p>Es el equivalente de {@code GestionSesionesService.cerrarTodas} para el otro sustrato:
 * suspender ya borraba las sesiones de Redis en el acto, pero las filas de {@code tokens_push}
 * quedaban intactas —solo se iban por CASCADE al eliminar la cuenta— y el telefono seguia siendo
 * un destino valido. Para un mentor suspendido eso significaba recibir el nombre y el
 * incumplimiento de sus aprendices, incluidos los que entraron al grupo despues.
 *
 * <p><b>Esto es el cinturon, no el control.</b> El evento llega despues del commit y de forma
 * asincrona (outbox de Modulith), asi que entre la suspension y este borrado hay una ventana: lo
 * que impide la entrega en el acto es {@code NotificacionService.intentarPush}, que consulta el
 * estado del destinatario antes de mandar. Lo que agrega este listener es que la credencial deje
 * de existir en vez de quedar guardada esperando — que es justamente lo que se espera de una
 * revocacion.
 *
 * <p>Se mira el estado NUEVO y no el nombre de la transicion: {@code allowsAccess()} es la misma
 * pregunta que hace el resto del sistema, asi que si algun dia aparece otro estado sin acceso,
 * este listener ya lo cubre. Reactivar no revoca nada; la app vuelve a registrar el token sola en
 * cuanto haya sesion.
 */
@Component
class EstadoDeCuentaCambiadoTokensPushListener {

    private final RevocarTokensPushUseCase revocarTokensPushUseCase;

    EstadoDeCuentaCambiadoTokensPushListener(RevocarTokensPushUseCase revocarTokensPushUseCase) {
        this.revocarTokensPushUseCase = revocarTokensPushUseCase;
    }

    @ApplicationModuleListener
    void on(EstadoDeCuentaCambiadoEvent event) {
        if (event.estadoNuevo().allowsAccess()) {
            return;
        }
        revocarTokensPushUseCase.revocarDe(event.usuarioId());
    }
}
