package com.renaser.os.rag.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Una persona repitio, en pocos dias, expresiones de malestar al escribirle al asistente.
 *
 * <p><b>Lo que este evento afirma y lo que no.</b> Afirma que se repitio un patron de texto que
 * conviene mirar. No afirma que nadie este en crisis, ni deprimido, ni en riesgo: eso seria un
 * diagnostico, y ni el codigo que lo emite ni quien lo escribio tienen con que hacerlo. Quien lo
 * consuma no puede agregarle esa lectura al texto que muestre.
 *
 * <p><b>Nunca lleva lo que la persona escribio.</b> El contenido de una conversacion es dato
 * personal (CLAUDE.MD §5.4.9): viaja el nombre —para que el aviso sirva de algo— y la cuenta, nunca
 * una frase. Quien quiera el detalle entra a la ficha del aprendiz, donde los permisos se
 * revalidan.
 *
 * <p>Va por evento y no llamando a {@code notifications} a mano, por el mismo motivo que
 * {@code AvisoDeAcompanamientoEvent} y {@code GrupoPorVencerEvent}: {@code rag} no tiene por que
 * saber que existe una bandeja de notificaciones, y el outbox de Spring Modulith corre al listener
 * en su propia transaccion despues del commit, asi que un fallo al notificar no puede tumbar la
 * conversacion que lo origino.
 *
 * <p><b>Es la primera vez que {@code rag} publica algo hacia afuera.</b> Su {@code api/} estuvo
 * vacio a proposito desde el dia uno, esperando exactamente esto.
 *
 * @param claveDeDeduplicacion identifica el EPISODIO, no la deteccion — la calcula
 *                             {@code PatronDeMalestarRepetido.claveDeDeduplicacion()}. Se usa como
 *                             {@code origenEventoId}, que tiene indice unico: revisar el patron en
 *                             cada mensaje entrega UN aviso a cada destinatario.
 * @param detecciones          cuantas expresiones se contaron al emitirlo. Puede haber subido
 *                             despues sin que el aviso se reescriba; por eso el texto dice
 *                             "al menos".
 * @param diasDeLaVentana      el ancho de la ventana, para que el texto del aviso no repita el
 *                             numero por su cuenta.
 */
public record PatronDeMalestarRepetidoEvent(UUID claveDeDeduplicacion, UUID usuarioId, String nombreDeLaPersona,
                                             int detecciones, int diasDeLaVentana, Instant detectadoEn) {

    /** Ficha del aprendiz en el panel del operador. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/admin/trainees/" + usuarioId;
    }
}
