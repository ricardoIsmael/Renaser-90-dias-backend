package com.renaser.os.rag.application.ports.in.seguridad;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Revisa, cada vez que un aprendiz le escribe al asistente, si ya se repitio el patron de
 * {@code PatronDeMalestarRepetido} — y si se repitio, hace las dos cosas que corresponden: avisarle
 * a quien pueda actuar (ADMIN/ALQUIMISTA, via evento) y devolver el texto de apoyo para la persona.
 *
 * <p><b>Que NO hace.</b> No clasifica, no diagnostica y no decide modo de respuesta. Eso es
 * {@code EvaluarRiesgoMensajePort} —que sigue sin implementacion real y sin criterio clinico
 * firmado— y no se toca desde aca.
 */
public interface RevisarPatronDeMalestarUseCase {

    /**
     * @param textoDelMensaje el mensaje que la persona acaba de escribir, ya guardado. Se mira
     *                        primero en memoria: si no contiene ninguna expresion de la lista, la
     *                        cuenta no pudo haber subido desde el mensaje anterior y no se consulta
     *                        nada. Es lo que hace que este mecanismo no cueste nada en el caso
     *                        normal, que es el 99% de los mensajes.
     * @return el texto de apoyo que hay que mostrarle a la persona en ESTE turno. Vacio cuando el
     *         patron no se repitio <b>o</b> cuando el texto todavia no esta configurado — en el
     *         segundo caso el aviso a ADMIN/ALQUIMISTA igual se emitio (ver {@code MensajeDeApoyo}).
     */
    Optional<String> revisar(UserId actorId, String textoDelMensaje);
}
