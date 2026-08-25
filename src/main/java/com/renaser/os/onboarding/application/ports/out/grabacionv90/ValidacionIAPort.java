package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.shared.domain.UserId;

/**
 * Puerto PROPIO de este modulo para validar una grabacion V90 — a proposito NO se comparte
 * con el {@code EvidenceValidationPort} de `evidence` (modulo distinto, construido en
 * paralelo): validar transcripciones/audio V90 es conceptualmente otra cosa que validar
 * evidencia diaria, y `evidence` puede no existir todavia cuando esto se construye.
 *
 * <p><b>SIN IA en este alcance:</b> el unico adaptador que existe hoy
 * ({@code NoOpValidacionIAAdapter}) siempre devuelve {@code NO_DISPONIBLE} — ver
 * docs/MODULO_ONBOARDING.md. El contrato queda completo para cuando se conecte Gemini de
 * verdad (Spring AI `ChatClient`, CLAUDE.MD §7).
 */
public interface ValidacionIAPort {

    ResultadoValidacionV90 validar(SolicitudValidacionV90 solicitud);

    record SolicitudValidacionV90(UserId usuarioId, long grabacionId, String fase, String eje,
                                   String transcripcion) {
    }

    record ResultadoValidacionV90(Estado estado, String feedbackJson) {

        public enum Estado {
            APROBADA,
            RECHAZADA,
            NO_DISPONIBLE
        }

        public static ResultadoValidacionV90 noDisponible() {
            return new ResultadoValidacionV90(Estado.NO_DISPONIBLE, null);
        }
    }
}
