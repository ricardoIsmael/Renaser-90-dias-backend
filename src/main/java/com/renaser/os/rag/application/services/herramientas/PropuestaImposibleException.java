package com.renaser.os.rag.application.services.herramientas;

/**
 * Una propuesta de horario que no se puede ofrecer, con el motivo YA escrito para el modelo
 * (fase 4, 2026-09-23). Nunca sale de este paquete: la herramienta la atrapa y la devuelve como
 * {@code Fallo}, igual que exige {@link HerramientaAgente}.
 *
 * <p>Existe para que cada validacion se lea como una linea ("el habito tiene que existir ese dia",
 * "tiene que quedar cupo") en vez de una cadena de {@code if} que arrastra un resultado.
 */
final class PropuestaImposibleException extends RuntimeException {

    PropuestaImposibleException(String motivoParaElModelo) {
        super(motivoParaElModelo, null, false, false);
    }
}
