package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

/**
 * Traduce el rechazo de {@code habits} al confirmar un cambio de horario (fase 4, 2026-09-23) a un
 * {@code Fallo} apto para mostrar. Entre proponer y confirmar pasa el tiempo: la persona pudo gastar
 * el cupo desde la app, o pasar la medianoche y volver "pasado" el dia elegido.
 *
 * <p>Se traduce por TIPO de excepcion, que es el contrato documentado de
 * {@code habits.api.AjustarHorarioHabitoUseCase}; el mensaje crudo nunca llega al modelo (va al log).
 *
 * @param siEstadoInvalido  {@code IllegalStateException}: sin cupo, u obligatorio, segun la operacion
 * @param siArgumentoInvalido {@code IllegalArgumentException}: fecha u hora no editable
 */
record RechazoDeHorario(String siEstadoInvalido, String siArgumentoInvalido) {

    private static final Logger log = LoggerFactory.getLogger(RechazoDeHorario.class);

    ResultadoHerramienta traducir(String herramienta, RuntimeException rechazo) {
        log.info("[rag] {} rechazada por habits al confirmar: {}", herramienta, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NotAuthorizedException ajenaOSuspendida ->
                    "No se pudo: la cuenta no esta activa o ese habito no es suyo.";
            case NoSuchElementException noExiste -> "No se pudo: ese habito ya no existe o no esta activo.";
            case IllegalStateException estado -> siEstadoInvalido;
            case IllegalArgumentException argumento -> siArgumentoInvalido;
            default -> "No pude aplicar el cambio en este momento. Puede intentarlo desde la app.";
        });
    }
}
