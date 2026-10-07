package com.renaser.os.chat.application.ports.in.ranking;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * El podio de la semana en el grupo general «Formación Renaser Global» (D-262): la imagen (sin aviso) y el texto
 * (con aviso a todos), firmados por el programa. Idempotente: los ids salen de la semana, así que publicar dos
 * veces la misma semana no duplica nada.
 */
public interface PublicarPodioDeLaSemanaUseCase {

    /** El barrido del lunes: no hace nada con el interruptor {@code renaser.chat.ranking-semanal.activo} apagado. */
    ResultadoDelPodio publicarLaSemanaCerrada();

    /**
     * Publica YA la última semana cerrada, pedido por Administración (para probarlo sin esperar al lunes). No mira
     * el interruptor: el interruptor gobierna la publicación automática.
     *
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST con la cuenta activa
     */
    ResultadoDelPodio publicarAhora(UserId actorId);

    /** @param piezas cuántos mensajes salieron ahora (0 si ya estaban o no salió nada) */
    record ResultadoDelPodio(LocalDate lunes, LocalDate domingo, Estado estado, int piezas) {
    }

    enum Estado {
        PUBLICADO,
        /** El texto de esa semana ya estaba en el grupo. */
        YA_ESTABA,
        /** Nadie cerró la semana con puntaje: no se publica. */
        SIN_PUNTAJES,
        /** No existe el grupo general (no debería pasar: lo crea V47). */
        SIN_GRUPO_GENERAL,
        /** El interruptor está apagado (solo el barrido). */
        APAGADO
    }
}
