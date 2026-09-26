package com.renaser.os.habits.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un habito del dia visto desde afuera de `habits`, con lo que hace falta para hablar de el:
 * como se llama, en que estado esta, cuanto paga y hasta cuando.
 *
 * <p>Nace para las herramientas del agente conversacional (2026-09-05), pero no es un tipo "del
 * agente": es la proyeccion publica que a `habits` le faltaba. {@code points.HabitoDelDiaResumen}
 * ya existia y se queda como esta — solo lleva {@code trackId}/{@code titulo}/{@code estado},
 * porque {@code GET /home} no necesita mas. Este agrega lo que aquel no tiene (puntos y plazo) en
 * vez de ensancharlo, para no cambiarle el contrato a un consumidor que no lo pidio.
 *
 * @param estado         espejo de {@code EstadoRegistro} como String — mismo criterio que
 *                       {@code HabitoDelDiaResumen.estado}: no filtrar un tipo interno fuera del
 *                       {@code @NamedInterface}
 * @param puntosEnJuego  lo que paga completarlo ahora, o {@code null} si ya esta en estado
 *                       terminal (no hay nada en juego en lo que ya se hizo o se perdio)
 * @param puntosMaximos  el techo de la escala, para poder decir "6 de 10" sin conocer la constante
 * @param plazo          instante en que se bloquea, o {@code null} si el habito no vence
 * @param exigeEvidencia si el catalogo pide evidencia para este habito (2026-09-14). Que la
 *                       EXIJA no impide completarlo: {@code RegistroService.completar} no mira
 *                       esta bandera. Existe para poder decirlo, no para bloquear
 * @param tramos         la escala de puntos completa del habito, en orden y hasta el
 *                       {@code plazo} (2026-09-23). Vacia cuando no hay nada que escalonar: el
 *                       registro esta en estado terminal o el habito no vence. Ver {@link TramoPuntos}
 * @param claveSistema   la clave funcional del habito de catalogo ({@code DAILY_CLASS},
 *                       {@code AUDIO_THERAPY_WEEKLY}...), {@code null} en los personales
 *                       (2026-09-26, D-171). El acompanante la usa para no ofrecer la camara en la
 *                       Clase diaria, que se cierra con su resumen y no por el camino generico.
 *                       Nunca se empareja por {@code titulo}: la persona lo puede renombrar (D-133)
 */
public record HabitoEnJuegoResumen(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                                    Integer puntosMaximos, Instant plazo, boolean exigeEvidencia,
                                    List<TramoPuntos> tramos, String claveSistema) {

    public HabitoEnJuegoResumen {
        tramos = tramos == null ? List.of() : List.copyOf(tramos);
    }

    /**
     * Un tramo de la escala: entregar ANTES de {@code hasta} (y despues del tramo anterior)
     * paga {@code puntos}. El primero cubre tambien todo lo previo a la hora ancla; el ultimo
     * termina en el {@code plazo}.
     *
     * <p>Nace para la herramienta {@code consultar_tiempo_para_puntos} del agente (2026-09-23):
     * "si no lo haces antes de las 8:32, pasa a pagar 9". Solo con {@code plazo} y
     * {@code puntosEnJuego} el llamador tendria que saber la escala de D-97 (cuando empieza la
     * gracia, cada cuanto baja) y reimplementarla fuera de {@code habits}; con los tramos ya
     * resueltos solo tiene que ubicar un instante en una lista.
     *
     * <p>El puntaje del instante exacto del plazo (el minimo de la escala, que se paga solo en
     * ese instante) no forma tramo: dura cero segundos y anunciarlo seria prometer algo que nadie
     * puede alcanzar a proposito.
     */
    public record TramoPuntos(Instant hasta, int puntos) {
    }
}
