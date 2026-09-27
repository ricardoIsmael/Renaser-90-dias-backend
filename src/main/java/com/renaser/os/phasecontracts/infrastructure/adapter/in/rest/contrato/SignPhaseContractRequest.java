package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;

/**
 * Cuerpo OPCIONAL de {@code POST /api/v1/phase-contracts} (D-216, TRN-21 del e2e): la fase que la
 * persona cree que esta firmando, el mismo valor que devuelve {@code GET /pending} en {@code phase}.
 * No elige que se firma (lo decide el servidor, D-193): si no es la que toca, 409. Con ella, repetir el
 * pedido devuelve el mismo pacto; sin cuerpo sigue valiendo el pedido de siempre, que con dos o mas
 * pactos pendientes responde 409 y pide la fase. No hay campo de ruta: la ruta la calcula el servidor.
 */
public record SignPhaseContractRequest(FasePrograma phase) {
}
