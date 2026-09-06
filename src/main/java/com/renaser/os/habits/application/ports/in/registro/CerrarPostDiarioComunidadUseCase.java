package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * La mitad que faltaba de la regla del post diario en comunidad: <b>publicar en el Muro cierra
 * el habito y paga sus puntos</b>.
 *
 * <p><b>Por que existe</b> (E-121). El dueno del producto dijo el 2026-09-04, textual:
 * <i>"Cuando publique algo, y recien ahi, se marca como completado"</i>. De esa frase se habia
 * construido solo la mitad guardiana —{@code PoliticaPostDiarioComunidad}, que rechaza el cierre
 * manual de quien no publico— y ninguna que DISPARARA el cierre de quien si publico:
 * {@code PublicacionMuroService.publicar} emitia {@code PublicacionCreadaEvent} y ese evento no
 * tenia un solo oyente. El registro se quedaba PENDIENTE hasta que el barrido nocturno lo
 * expiraba, y el aprendiz perdia los puntos de un habito que si habia cumplido.
 *
 * <p>Preguntado el 2026-09-05 si la publicacion que {@code rocks} crea sola en el Muro al
 * completar la roca diaria tambien debia cerrar el habito, el dueno respondio <i>"si"</i>. Por eso
 * el disparador vive aca, colgado del evento, y no del endpoint de publicar: cualquier via de
 * publicacion —la manual del Muro y la automatica de {@code rocks} por
 * {@code publicarDesdeEvidencia}— emite el mismo evento y cierra el habito por igual.
 *
 * <p><b>Nunca falla hacia afuera por algo que no sea un error.</b> Publicar en el Muro es la
 * operacion que la persona vino a hacer; que ademas cierre un habito es un efecto secundario. Que
 * el aprendiz no tenga el habito hoy, que lo haya pausado, que quien publica sea un mentor o que
 * el catalogo de ese entorno no tenga la fila son situaciones normales, no fallas: en todas se
 * vuelve sin hacer nada.
 */
public interface CerrarPostDiarioComunidadUseCase {

    /**
     * Cierra el registro del habito de post diario correspondiente al DIA DE LA PUBLICACION, no al
     * dia de hoy.
     *
     * <p><b>La distincion no es cosmetica</b> (regla 02-tiempo-zonas-y-schedulers). Este caso de
     * uso lo dispara un evento del outbox de Modulith, y {@code republish-outstanding-events-on-restart}
     * esta en {@code true}: un evento que quedo sin procesar se reentrega al arrancar, que puede
     * ser al dia siguiente. Anclando en {@code publicadoEn} se cierra el habito del dia en que la
     * persona publico de verdad; anclando en "hoy" se le pagaria el habito de hoy con el post de
     * ayer. Y el dia se abre y se cierra en la zona del PARTICIPANTE (por defecto
     * {@code America/Lima}), no en UTC: para alguien en Lima, una publicacion de las 02:00 UTC
     * pertenece al dia anterior (E-91).
     *
     * @param autorId    quien publico. Si no es un participante del programa, o esta suspendido,
     *                   no se hace nada
     * @param publicadoEn instante de la publicacion — {@code PublicacionCreadaEvent.occurredAt()}
     */
    void alPublicarEnElMuro(UserId autorId, Instant publicadoEn);
}
