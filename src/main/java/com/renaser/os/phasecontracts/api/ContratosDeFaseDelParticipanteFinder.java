package com.renaser.os.phasecontracts.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Contrato publico de {@code phasecontracts} para LEER los contratos de fase de una persona desde otro
 * modulo (2026-09-23, herramienta {@code consultar_contratos_de_fase} del acompanante, {@code rag}).
 * Solo lectura: firmar es consentimiento legal y no tiene contrato publico (R3 de
 * {@code docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md} §4.1).
 *
 * <p><b>Por que un archivo aparte y no un metodo mas de {@link ContratoFaseFinder}.</b> Ese finder
 * responde una pregunta interna ("esta firmada la fase N") sin guardas de cuenta; este responde lo
 * mismo que {@code GET /phase-contracts} y {@code GET /phase-contracts/pending}, con sus guardas.
 *
 * <p><b>Delega, no reimplementa.</b> Usa {@code ConsultarContratosUseCase} y
 * {@code ConsultarContratosPendientesUseCase}: que fase toca firmar segun el dia de programa
 * ({@code FasePrograma.faseAFirmarEnDia}) y si ya se firmo se deciden ahi.
 *
 * <p><b>Misma autorizacion que la app:</b> propaga {@code NoSuchElementException} si el participante
 * no existe y {@code NotAuthorizedException} si esta suspendido, su rol no consulta contratos, o no
 * activo su programa. El que llama decide como contarlo.
 *
 * <p><b>Nunca cruza la URL de la firma.</b> {@code ConsultarContratosUseCase} devuelve una URL
 * prefirmada de lectura por contrato; es una credencial de acceso al archivo y se descarta aca.
 */
public interface ContratosDeFaseDelParticipanteFinder {

    ContratosDeFase delParticipante(UserId participanteId);

    /**
     * @param firmados    los contratos que la persona ya firmo, en el orden en que los guarda la base
     * @param pendienteHoy la fase que le corresponde firmar HOY y todavia no firmo; {@code null} si ninguna
     */
    record ContratosDeFase(List<FaseDelContrato> firmados, FaseDelContrato pendienteHoy) {

        public ContratosDeFase {
            firmados = List.copyOf(firmados);
        }
    }

    /** @param numeroFase 1..4; @param etiqueta la de {@code FasePrograma} ("Fase II · El Desarrollo") */
    record FaseDelContrato(int numeroFase, String etiqueta) {
    }
}
