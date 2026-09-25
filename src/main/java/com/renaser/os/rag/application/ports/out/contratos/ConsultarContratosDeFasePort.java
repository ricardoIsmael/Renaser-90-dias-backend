package com.renaser.os.rag.application.ports.out.contratos;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Puerto propio de {@code rag} para leer los contratos de fase de la persona con la que habla el
 * acompanante (2026-09-23, herramienta {@code consultar_contratos_de_fase}). Las tablas son de
 * {@code phasecontracts}: el adaptador delega en {@code phasecontracts.api} (D-41).
 *
 * <p><b>Solo lectura, a proposito.</b> No existe ni va a existir un metodo para firmar: firmar un
 * contrato de fase es consentimiento legal de la persona y queda fuera del acompanante (R3).
 *
 * <p>Propaga lo que propaga {@code phasecontracts}: {@code NoSuchElementException} y
 * {@code NotAuthorizedException} cuando la persona no puede consultar sus contratos.
 */
public interface ConsultarContratosDeFasePort {

    ContratosDeFase delAprendiz(UserId aprendizId);

    /** @param pendienteHoy la fase que le toca firmar hoy y todavia no firmo, o {@code null} */
    record ContratosDeFase(List<Fase> firmados, Fase pendienteHoy) {

        public ContratosDeFase {
            firmados = List.copyOf(firmados);
        }
    }

    record Fase(int numero, String etiqueta) {
    }
}
