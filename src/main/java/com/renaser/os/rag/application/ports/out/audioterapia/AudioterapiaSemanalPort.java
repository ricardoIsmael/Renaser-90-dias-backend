package com.renaser.os.rag.application.ports.out.audioterapia;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Lo que el acompanante necesita de la Audioterapia semanal (D-171): que audio le toca a la
 * persona, como esta su registro de hoy, y entregar sus dos respuestas. Puerto propio de
 * {@code rag}, con sus tipos, igual que {@code EnfoqueDiarioDelAprendizPort}: el adaptador delega
 * en {@code habits.api.AudioterapiaDelAprendizPort}, donde viven las reglas.
 */
public interface AudioterapiaSemanalPort {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException cuenta suspendida o sin programa */
    AudioterapiaDeHoy deHoyDe(UserId participanteId);

    /**
     * Adjunta el texto como evidencia de TEXTO al registro de hoy y lo completa, como la app.
     *
     * @return los puntos otorgados
     * @throws RuntimeException si ya no corresponde (otro dia, ya hecho, sin audio): quien llama lo
     *                          traduce a un {@code Fallo} legible
     */
    int entregarRespuestas(UserId actorId, UUID registroId, String texto);

    /**
     * @param semana         {@code null} si todavia no hay audio esta semana
     * @param registroId     el registro de hoy del habito, o {@code null} si hoy no hay
     * @param estadoRegistro PENDIENTE, EN_CURSO, COMPLETADO, EXPIRADO o FALLIDO; {@code null} sin registro
     */
    record AudioterapiaDeHoy(Integer semana, String titulo, Integer diaSiguienteCambio, UUID registroId,
                             String estadoRegistro) {

        public boolean hayAudio() {
            return semana != null;
        }

        /** Hay registro hoy y todavia se puede entregar (no esta hecho, vencido ni cerrado). */
        public boolean pendienteHoy() {
            return "PENDIENTE".equals(estadoRegistro) || "EN_CURSO".equals(estadoRegistro);
        }
    }
}
