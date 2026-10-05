package com.renaser.os.onboarding.application.ports.out.respuesta;

import com.renaser.os.shared.domain.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lee respuestas <b>por clave de pregunta</b> y no por id.
 *
 * <p>{@link LoadRespuestaPort} resuelve por {@code pregunta_id}, que es la forma correcta cuando se
 * esta contestando un cuestionario: el cliente ya tiene el catalogo en la mano. Pero quien pregunta
 * "que declaro esta persona que iba a medir" solo conoce la clave estable
 * ({@code map_health_result_type}), no un id autoincremental que cambia de entorno en entorno.
 * Resolver clave -> id primero y respuesta despues serian dos viajes y una query por clave.
 */
public interface LeerRespuestasPorClavePort {

    /**
     * Las respuestas de ese usuario a esas claves, indexadas por clave. Las claves sin responder
     * —o que no existen en el catalogo— simplemente no aparecen en el mapa.
     */
    Map<String, ValorDeRespuesta> deUsuario(UserId usuarioId, Set<String> clavesDePregunta);

    /**
     * El {@code media_id} de la respuesta de ese usuario a esa clave: el archivo (firma, audio) que la
     * respalda. Vacío si no respondió, si la clave no existe o si la respuesta quedó sin archivo (el FK es
     * {@code ON DELETE SET NULL}).
     *
     * <p>Lo usa la firma del Pacto (D-253, clave {@code signature}). <b>No garantiza que el archivo sea de
     * ese usuario</b>: el {@code mediaId} de una respuesta lo manda el cliente (E-528). Quien lo use para
     * abrir el archivo tiene que comprobar el dueño ({@code MediaOnboarding.esDe}).
     */
    Optional<Long> mediaDe(UserId usuarioId, String clavePregunta);

    /**
     * Los dos unicos slots del EAV que hacen falta para leer el Mapa: el texto (donde caen
     * {@code TEXTO} y {@code SELECCION_UNICA}) y la escala 1-10. El resto de los slots existe, pero
     * ninguna pregunta del Mapa que a alguien le interese desde afuera los usa.
     */
    record ValorDeRespuesta(String texto, Short escala) {
    }
}
