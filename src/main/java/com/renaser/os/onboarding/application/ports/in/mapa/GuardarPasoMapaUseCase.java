package com.renaser.os.onboarding.application.ports.in.mapa;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Guarda UN PASO del Mapa, no una respuesta suelta.
 *
 * <p><b>Por que por paso.</b> Es el mismo criterio que `usePersistenciaOnboarding.guardarCapitulo`
 * ya usa para los otros cinco flujos: se guarda cuando la persona termina una vista. Si abandona
 * en el 80 %, lo que llenó quedó; y el servidor recibe la vista COMPLETA, que es la unica forma de
 * validar reglas que miran varias filas a la vez (maximo 6 acciones, maximo 2 por area).
 */
public interface GuardarPasoMapaUseCase {

    /** V06 — el sistema de ejecucion entero. Reemplaza lo que hubiera. */
    void guardarSistemaDeEjecucion(GuardarAccionesCommand command);

    /** V07 — los protocolos de reemplazo enteros. Reemplaza lo que hubiera. */
    void guardarProtocolosDeReemplazo(GuardarProtocolosCommand command);

    record GuardarAccionesCommand(UserId actorId, List<AccionEntrada> acciones) {
    }

    record GuardarProtocolosCommand(UserId actorId, List<ProtocoloEntrada> protocolos) {
    }

    /**
     * `dias` son enteros ISO (1 = lunes … 7 = domingo), el mismo vocabulario que
     * `horario_semanal_habito` (V39). El cliente manda 'L','M','X'… y traduce en su capa de API:
     * el contrato HTTP no arrastra las iniciales en castellano a la base.
     */
    record AccionEntrada(String accionId, String area, String texto, int frecuenciaSemanal, List<Integer> dias,
                          String momento, String evidencia) {
    }

    record ProtocoloEntrada(String protocoloId, String patron, String disparador, String conductaActual,
                             String respuestaAlternativa) {
    }
}
