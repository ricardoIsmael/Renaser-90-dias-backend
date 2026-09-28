package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import java.math.BigDecimal;
import java.util.List;

/**
 * Los cuerpos de la Caja Renaser (D-219). Sin {@code @Valid}: lo que se valida lo valida el dominio, con
 * mensajes que la app muestra tal cual, y DESPUÉS de saber si quien pide puede (403 antes que 400).
 */
final class CajaRequests {

    private CajaRequests() {
    }

    record EnviarRequest(String medio, String courier, String codigo, BigDecimal costo) {
    }

    record EntregadaRequest(Boolean previa) {
    }

    record ProblemaRequest(String motivo, String nota) {
    }

    record MarcadosRequest(List<String> marcados) {
    }

    /** {@code image/jpeg} (por defecto) o {@code image/png}: el mismo que el teléfono manda en el PUT. */
    record SubidaRequest(String contentType) {
    }

    /** La {@code ruta} que devolvió la URL de subida, nunca la URL (lleva firma y vence). */
    record ConfirmarFotoRequest(String ruta) {
    }

    record ContenidoRequest(List<ElementoRequest> elementos) {
    }

    record ElementoRequest(String valor, String etiqueta) {
    }
}
