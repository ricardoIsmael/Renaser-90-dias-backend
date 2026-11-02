package com.renaser.os.rag.infrastructure.adapter.in.rest.propuesta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.rag.domain.model.herramienta.HabitoCumplido;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

/**
 * Respuesta de {@code POST /api/v1/renasia/propuestas/{id}/confirmar}.
 *
 * <p>{@code estado} es {@code CONFIRMADA} si la accion se aplico y {@code FALLIDA} si el negocio la
 * rechazo al ejecutarla (vencio el habito, se agoto la cuota...). En los dos casos es 200: el pedido
 * se proceso, y {@code mensaje} es el texto legible que la app muestra tal cual. Los rechazos que
 * impiden siquiera intentarlo (ajena, suspendido, vencida, cancelada) no llegan aca: son 4xx.
 *
 * <p><b>Spec 038:</b> si la accion cumplio un habito, {@code habitoCumplido} dice cual (registro, habito, dia y puntos) para que la app haga lo
 * mismo que al cumplirlo desde Training: apagar las alarmas de ese habito que le quedaban a ese dia (issue #33), celebrar y refrescar el
 * semaforo. Es aditivo y sale solo cuando hay dato: una app anterior lo ignora. Una respuesta repetida de una propuesta ya resuelta (doble toque,
 * reintento) trae solo el texto que quedo guardado, sin el dato.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoPropuestaResponse(String estado, String mensaje, HabitoCumplidoResponse habitoCumplido) {

    static final String CONFIRMADA = "CONFIRMADA";
    static final String FALLIDA = "FALLIDA";

    public ResultadoPropuestaResponse(String estado, String mensaje) {
        this(estado, mensaje, null);
    }

    public static ResultadoPropuestaResponse from(ResultadoHerramienta resultado) {
        return switch (resultado) {
            case ResultadoHerramienta.Exito exito -> new ResultadoPropuestaResponse(CONFIRMADA, exito.contenido(),
                    exito.cumplido() == null ? null : HabitoCumplidoResponse.from(exito.cumplido()));
            case ResultadoHerramienta.Fallo fallo -> new ResultadoPropuestaResponse(FALLIDA, fallo.motivo());
        };
    }

    /** {@code fecha} es {@code YYYY-MM-DD}: el dia del habito en la zona de la persona. */
    public record HabitoCumplidoResponse(String registroId, String habitoId, String fecha, int puntos) {

        static HabitoCumplidoResponse from(HabitoCumplido cumplido) {
            return new HabitoCumplidoResponse(cumplido.registroId().toString(), cumplido.habitoId().toString(),
                    cumplido.fecha().toString(), cumplido.puntos());
        }
    }
}
