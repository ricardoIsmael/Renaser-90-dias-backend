package com.renaser.os.chat.application.ports.in.lectura;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.mensaje.ConfirmacionDeLectura;

/**
 * Hasta dónde leyeron todos en una conversación, para marcar ✓ o ✓✓ los mensajes propios (D-208).
 *
 * <p><b>No autoriza.</b> Recibe la conversación ya cargada y la usa quien ya comprobó que el actor puede
 * verla ({@code MensajeService.listar}); nunca se expone sola a un cliente. Es una lectura de los
 * participantes de UNA conversación, no por mensaje, y en la comunidad ni siquiera consulta.
 */
@FunctionalInterface
public interface ConsultarLecturaUseCase {

    ConfirmacionDeLectura confirmacionDe(Conversacion conversacion);
}
