package com.renaser.os.chat.application.ports.out.presencia;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * Quien tiene ahora mismo un socket abierto.
 *
 * <p>Es estado <b>efimero y compartido entre instancias</b>, no un dato del dominio: vive en
 * Redis con vencimiento y nunca en Postgres. Si el backend se cae, la respuesta correcta es
 * "no hay nadie en linea" —que es a lo que llega solo cuando vencen las llaves— y no una
 * tabla que quede afirmando que media comunidad sigue conectada.
 */
public interface PresenciaPort {

    /** Deja constancia de que {@code usuarioId} esta conectado, y por cuanto vale sin refrescar. */
    void marcarEnLinea(UserId usuarioId, Duration vigencia);

    /** Borra la constancia. Idempotente: quitar a alguien que no estaba no es un error. */
    void marcarFueraDeLinea(UserId usuarioId);

    /**
     * Cuales de {@code candidatos} estan en linea, en una sola ida a Redis.
     *
     * <p>En lote y no uno por uno: quien pregunta siempre tiene delante una lista (los
     * participantes de una conversacion), y una llamada por participante seria N+1 contra
     * Redis en la pantalla que mas se abre.
     */
    Set<UserId> enLineaDe(Collection<UserId> candidatos);
}
