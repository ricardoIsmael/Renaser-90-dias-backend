package com.renaser.os.evidence.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * "De estos registros de habito, cuales ya tienen al menos una evidencia subida."
 *
 * <p><b>Por que existe (2026-09-05, D-113).</b> El movil venia respondiendo esa pregunta por su
 * cuenta: pedia {@code GET /api/v1/habit-tracks/today}, pedia {@code GET /api/v1/evidence} y
 * cruzaba los {@code registroHabitoId} de la segunda contra los ids de la primera. Ese cruce esta
 * roto por construccion — el listado devuelve UNA pagina de 20 filas, ordenada por {@code creadoEn}
 * descendente, sin filtro de fecha y mezclando los tres destinos (habito, roca, espiritu): el dia
 * que un aprendiz supere esas 20 filas, la evidencia de un habito de hoy cae fuera de la pagina y
 * la pantalla le ofrece "SUBIR" un archivo que ya subio. Se resuelve del lado del servidor, que es
 * el unico que puede hacerlo bien: aca la respuesta es exacta y no depende de cuantas filas entren
 * en una pagina.
 *
 * <p><b>Se pregunta en lote, nunca de a uno.</b> {@code habits} lo llama UNA vez con los 20-40
 * registros del dia — el mismo criterio "nunca N+1" que ya rige la proyeccion de
 * {@code GET /habit-tracks/today}. La consulta se apoya en {@code evidencias_registro_idx}, el
 * indice parcial sobre {@code registro_habito_id} que ya existe desde el baseline.
 *
 * <p><b>No recibe {@code actorId}, a proposito</b> — mismo criterio documentado en
 * {@code habits.api.AgendaDelDiaFinder} y {@code points.HabitosDelDiaFinder}: es una llamada de
 * modulo a modulo donde el llamador ya establecio que puede ver esos registros (en el unico
 * consumidor de hoy, porque {@code ConsultarTracksDelDiaUseCase} ya autorizo al participante antes
 * de devolverlos). Quien agregue un consumidor nuevo tiene que autorizar ANTES de armar la lista de
 * ids: este puerto responde por los ids que le den, no verifica de quien son.
 */
public interface RegistrosConEvidenciaFinder {

    /**
     * El subconjunto de {@code registrosHabitoIds} que tiene al menos una evidencia, en cualquier
     * estado de validacion.
     *
     * <p><b>Cualquier estado, incluido {@code RECHAZADA_IA}</b>: la pregunta que responde es "ya
     * subio algo", no "ya aprobo". Es lo que la pantalla necesita para no volver a pedirle el
     * archivo a alguien que ya lo mando — el veredicto se muestra aparte y por otra via.
     *
     * @return conjunto vacio si la coleccion viene vacia o si ninguno tiene evidencia; nunca null
     */
    Set<UUID> deEntre(Collection<UUID> registrosHabitoIds);
}
