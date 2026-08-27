package com.renaser.os.onboarding.application.ports.out.metamaestra;

import java.util.List;

/**
 * Puerto PROPIO de este modulo para el "Filtro de las 6 Ps" aplicado a la Meta Maestra
 * escrita (Diseno de Destino) — a proposito NO se comparte con {@code ValidacionIAPort}
 * (V90, audio): son dos prompts distintos para dos juicios distintos (una meta a 90 dias
 * no se evalua con la vara de una variable de conducta), documentado en el backend viejo
 * (RenaserBack/docs/FEATURE_ONBOARDING_AI_VALIDATION.md) como decision deliberada de NO
 * unificar los dos prompts.
 *
 * <p><b>SIN IA en este alcance:</b> el unico adaptador que existe hoy
 * ({@code NoOpMetaMaestraValidacionIAAdapter}) siempre devuelve {@code NO_DISPONIBLE} —
 * mismo estado del modulo que {@code ValidacionIAPort} (V90). El contrato queda completo
 * para cuando se conecte Gemini de verdad (Spring AI {@code ChatClient}, CLAUDE.MD §7).
 *
 * <p><b>Por que este puerto es SINCRONO y no async+polling como V90</b> (decision
 * documentada, ver javadoc de {@code ValidarMetaMaestraUseCase}): a diferencia de una
 * grabacion V90 (que se persiste en {@code grabaciones_v90} con un id contra el que hacer
 * polling), el texto de la Meta Maestra se valida ANTES de guardarse como respuesta —
 * verificado contra el frontend real ({@code C:\renaserPlayStore\app\renaser\diseno-destino.tsx}:
 * llama a {@code validateMetaMaestra} sobre un borrador en memoria, y solo si
 * {@code accepted} hace {@code flush()} del autosave — y contra el backend viejo
 * ({@code RenaserBack/src/app/api/v1/onboarding/smart/validate/route.ts}: "No recording,
 * nothing persisted — the client re-validates on every submit"). No hay una fila en una
 * tabla congelada (D-40) donde colgar un estado {@code PROCESANDO}/intentos para un texto
 * que todavia no es una {@code Respuesta} — inventar una tabla nueva solo para trackear un
 * intento efimero de validacion violaria D-40 sin necesidad real de negocio.
 */
public interface ValidacionMetaMaestraPort {

    ResultadoValidacionMetaMaestra validar(String texto);

    record ResultadoValidacionMetaMaestra(Estado estado, List<String> pesFaltantes, String feedback) {

        public enum Estado {
            APROBADA,
            RECHAZADA,
            NO_DISPONIBLE
        }

        public static ResultadoValidacionMetaMaestra noDisponible() {
            return new ResultadoValidacionMetaMaestra(Estado.NO_DISPONIBLE, List.of(), null);
        }
    }
}
