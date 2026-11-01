package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Lo que una persona respondió en el Código Renaser en un tramo de tiempo, para que el análisis con IA de {@code rag} lo lea
 * (spec 031, D-306). Es el tercer contrato que saca texto del Radar de una persona, junto a {@link CodigoRenaserDePersonaFinder} (las
 * fichas) y a la lectura propia, y se distingue de ellos en una cosa: <b>no lo usa ninguna ficha ni ninguna pantalla</b>. Quien lo reciba
 * lo usa solo para armar el pedido al proveedor de IA de esa misma persona; lo que de ahí salga hacia otra persona (el análisis) pasa por
 * el interruptor {@code radar.fichas} (spec 034) antes de mostrarse.
 *
 * <p>Sin {@code actorId}, a propósito, como los demás finders: quien llama ya decidió que puede leer. <b>Todo es texto personal</b>: no se
 * loguea.
 */
public interface RespuestasDelRadarFinder {

    /** Del más viejo al más nuevo. {@code desde} es inclusivo y {@code hasta} exclusivo. */
    List<RespuestaDelRadar> entre(UserId personaId, Instant desde, Instant hasta);

    /**
     * @param horaPrevista la hora local (0-23) a la que respondía, o {@code null} si fue un registro libre
     * @param dictado      al menos una respuesta se dictó con el reconocimiento de voz (spec 030)
     */
    record RespuestaDelRadar(Instant creadoEn, Integer horaPrevista, int nivelEnergia, String queHago, String quePienso,
                             String queSiento, String queEvito, boolean dictado) {
    }
}
