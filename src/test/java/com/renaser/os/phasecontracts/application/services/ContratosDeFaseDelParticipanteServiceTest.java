package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.api.ContratosDeFaseDelParticipanteFinder.ContratosDeFase;
import com.renaser.os.phasecontracts.api.ContratosDeFaseDelParticipanteFinder.FaseDelContrato;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase.ContratoPendiente;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase.ContratoConUrlLectura;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link ContratosDeFaseDelParticipanteService}: traduce los dos casos de uso de la app, sin la URL de la firma. */
class ContratosDeFaseDelParticipanteServiceTest {

    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final Instant FIRMADO_EN = Instant.parse("2026-09-10T15:00:00Z");

    private final ConsultarContratosUseCase contratos = mock(ConsultarContratosUseCase.class);
    private final ConsultarContratosPendientesUseCase pendiente = mock(ConsultarContratosPendientesUseCase.class);
    private final ContratosDeFaseDelParticipanteService service =
            new ContratosDeFaseDelParticipanteService(contratos, pendiente);

    private static ContratoConUrlLectura firmado(FasePrograma fase) {
        ContratoFase contrato = ContratoFase.rehydrate(ContratoFaseId.of(UUID.randomUUID()), PARTICIPANTE, fase,
                ContratoFase.BUCKET_DEFAULT, ContratoFase.rutaFirma(PARTICIPANTE, fase), FIRMADO_EN, FIRMADO_EN);
        return new ContratoConUrlLectura(contrato, URI.create("https://s3.example/firma?X-Amz-Signature=secreta"));
    }

    @Test
    @DisplayName("firmados y pendiente de hoy, como numero y etiqueta de la fase")
    void traduce() {
        when(contratos.consultarDeParticipante(PARTICIPANTE)).thenReturn(List.of(firmado(FasePrograma.FASE_2_DESARROLLO)));
        when(pendiente.consultarPendiente(PARTICIPANTE))
                .thenReturn(ContratoPendiente.de(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA));

        ContratosDeFase resultado = service.delParticipante(PARTICIPANTE);

        assertThat(resultado).isEqualTo(new ContratosDeFase(
                List.of(new FaseDelContrato(2, "Fase II · El Desarrollo")),
                new FaseDelContrato(3, "Fase III · El Guerrero Alquimista")));
        assertThat(resultado.toString()).doesNotContain("X-Amz-Signature");
    }

    @Test
    @DisplayName("sin nada pendiente hoy, pendienteHoy es null")
    void sinPendiente() {
        when(contratos.consultarDeParticipante(PARTICIPANTE)).thenReturn(List.of());
        when(pendiente.consultarPendiente(PARTICIPANTE)).thenReturn(ContratoPendiente.ninguno());

        assertThat(service.delParticipante(PARTICIPANTE)).isEqualTo(new ContratosDeFase(List.of(), null));
    }

    @Test
    @DisplayName("la guarda del caso de uso (cuenta suspendida) se propaga, igual que en la app")
    void propagaLaGuarda() {
        when(pendiente.consultarPendiente(PARTICIPANTE)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> service.delParticipante(PARTICIPANTE)).isInstanceOf(NotAuthorizedException.class);
    }
}
