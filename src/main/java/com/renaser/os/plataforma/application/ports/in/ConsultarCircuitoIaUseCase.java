package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;

import java.util.List;

/**
 * El estado del circuito automático de la IA (contrato §6, {@code GET /soporte-ti/ia/circuito}). Lo publica {@code rag}
 * por {@code shared.application.EstadoAutomaticoDeFuncionalidades}; mientras no exista, la lista es vacía.
 */
public interface ConsultarCircuitoIaUseCase {

    List<CircuitoAutomatico> circuitos(AccesoDeConsola acceso);
}
