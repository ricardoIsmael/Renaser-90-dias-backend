package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * La Audioterapia semanal vista desde otro modulo (2026-09-26, D-171): que audio le toca a la
 * persona esta semana, como esta su registro de hoy, y la entrega de sus respuestas.
 *
 * <p>Primer consumidor: el acompanante de {@code rag} ({@code consultar_audioterapia} y
 * {@code proponer_resumen_audioterapia}). Hace <b>lo mismo que la app</b> y nada mas: la app cierra
 * la Audioterapia con sus dos respuestas como evidencia de TEXTO y despues completa el registro por
 * el camino generico ({@code POST /habit-tracks/{id}/evidence/confirm} y
 * {@code POST /habit-tracks/{id}/complete}). <b>No</b> pasa por {@code /spirit-audio/submit}: ese
 * completa la Pastilla Renacer, no la Audioterapia.
 *
 * <p>Por que un puerto propio y no un "completar con evidencia cualquier habito": el mismo motivo
 * que {@link CompletarClaseDiariaHabitoUseCase}. Un metodo generico le daria a cualquier modulo
 * que importe {@code habits.api} un atajo para cerrar habitos que piden foto con un texto. Este
 * solo sabe cerrar la Audioterapia de hoy.
 */
public interface AudioterapiaDelAprendizPort {

    /** Identidad funcional estable del habito en el catalogo ({@code habitos.clave_sistema}). */
    String CLAVE_SISTEMA_AUDIOTERAPIA = "AUDIO_THERAPY_WEEKLY";

    /**
     * El audio de la semana y el registro de HOY (en la zona de la persona) del habito.
     *
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida o no
     *         cursa el programa (mismo criterio que {@code GET /api/v1/audio-therapy/status})
     */
    AudioterapiaDeHoy deHoyDe(UserId participanteId);

    /**
     * Adjunta {@code texto} como evidencia de TEXTO al registro de hoy y lo completa, con las
     * guardas de siempre ({@code CompletarRegistroUseCase}). Devuelve los puntos otorgados.
     *
     * @param registroId el registro que la persona vio al proponer: si ya no es el de hoy (paso la
     *                   medianoche de su zona), se rechaza en vez de cerrar otro dia
     * @throws java.util.NoSuchElementException si no hay audio esta semana o hoy no tiene ese registro
     * @throws IllegalStateException            si el registro ya no esta PENDIENTE ni EN_CURSO
     */
    int entregarRespuestas(UserId actorId, UUID registroId, String texto);

    /**
     * @param semana         {@code null} si todavia no hay audio (no desbloqueo o no se cargo)
     * @param titulo         {@code null} en el mismo caso
     * @param registroId     el registro de hoy del habito, o {@code null} si hoy no se genero
     * @param estadoRegistro espejo de {@code EstadoRegistro} como texto, o {@code null} sin registro
     */
    record AudioterapiaDeHoy(Integer semana, String titulo, Integer diaSiguienteCambio, UUID registroId,
                             String estadoRegistro) {

        public boolean hayAudio() {
            return semana != null;
        }
    }
}
