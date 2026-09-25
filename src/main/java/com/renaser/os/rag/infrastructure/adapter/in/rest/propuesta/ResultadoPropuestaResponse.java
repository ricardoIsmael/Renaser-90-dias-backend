package com.renaser.os.rag.infrastructure.adapter.in.rest.propuesta;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

/**
 * Respuesta de {@code POST /api/v1/renasia/propuestas/{id}/confirmar}.
 *
 * <p>{@code estado} es {@code CONFIRMADA} si la accion se aplico y {@code FALLIDA} si el negocio la
 * rechazo al ejecutarla (vencio el habito, se agoto la cuota...). En los dos casos es 200: el pedido
 * se proceso, y {@code mensaje} es el texto legible que la app muestra tal cual. Los rechazos que
 * impiden siquiera intentarlo (ajena, suspendido, vencida, cancelada) no llegan aca: son 4xx.
 */
public record ResultadoPropuestaResponse(String estado, String mensaje) {

    static final String CONFIRMADA = "CONFIRMADA";
    static final String FALLIDA = "FALLIDA";

    public static ResultadoPropuestaResponse from(ResultadoHerramienta resultado) {
        return switch (resultado) {
            case ResultadoHerramienta.Exito exito -> new ResultadoPropuestaResponse(CONFIRMADA, exito.contenido());
            case ResultadoHerramienta.Fallo fallo -> new ResultadoPropuestaResponse(FALLIDA, fallo.motivo());
        };
    }
}
