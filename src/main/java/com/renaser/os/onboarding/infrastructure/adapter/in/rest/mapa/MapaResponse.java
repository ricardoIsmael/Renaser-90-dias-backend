package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import com.renaser.os.onboarding.application.ports.in.mapa.ConsultarMapaUseCase.MapaDelParticipante;
import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/**
 * Las dos listas del Mapa y si la etapa ya se dio por terminada.
 *
 * <p><b>{@code retired: true} (spec 026, contrato §3).</b> El Mapa de Renacimiento se retiró: el diagnóstico lo reemplaza. Sigue respondiendo con los
 * datos que haya, {@code stageCompleted} sale verdadero para todos (la app anterior decide con él si muestra la puerta del Mapa) y este campo avisa a la
 * app nueva que no hay nada que activar.
 *
 * <p><b>Lo que NO viaja aca</b>: los tres objetivos, los nueve hitos, la prioridad, el retorno y el
 * compromiso. Esos son respuestas del flujo `mapa_dia7` y salen de
 * {@code GET /api/v1/onboarding/answers?flow=mapa_dia7}, que existe desde antes de esto.
 */
public record MapaResponse(List<AccionResponse> actions, List<ProtocoloResponse> protocols,
                            boolean stageCompleted, boolean retired) {

    public static MapaResponse from(MapaDelParticipante mapa) {
        return new MapaResponse(
                mapa.acciones().acciones().stream().map(AccionResponse::from).toList(),
                mapa.protocolos().protocolos().stream().map(ProtocoloResponse::from).toList(),
                mapa.etapaCompletada(), true);
    }

    public record AccionResponse(String actionId, String area, String text, int weeklyFrequency,
                                  List<Integer> days, String moment, String evidence, UUID habitId) {

        static AccionResponse from(AccionMapa accion) {
            return new AccionResponse(accion.accionId(), accion.area().clave(), accion.texto(),
                    accion.frecuenciaSemanal(), accion.dias().stream().map(DayOfWeek::getValue).sorted().toList(),
                    accion.momento() == null ? null : accion.momento().clave(),
                    accion.evidencia() == null ? null : accion.evidencia().clave(), accion.habitoId());
        }
    }

    public record ProtocoloResponse(String protocolId, String pattern, String trigger, String currentBehavior,
                                     String alternativeResponse, String sentence) {

        static ProtocoloResponse from(ProtocoloReemplazoMapa protocolo) {
            return new ProtocoloResponse(protocolo.protocoloId(), protocolo.patron(), protocolo.disparador(),
                    protocolo.conductaActual(), protocolo.respuestaAlternativa(), protocolo.frase());
        }
    }
}
