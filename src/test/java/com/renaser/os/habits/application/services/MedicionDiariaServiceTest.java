package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionDiariaUseCase.RegistrarMedicionCommand;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-226: la puerta de la fase 2 ubica el track por (participante, clave, fecha) y delega el cierre. */
@ExtendWith(MockitoExtension.class)
class MedicionDiariaServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 29);
    private static final MedicionDiaria KM = MedicionDiaria.manualSiHay(new BigDecimal("6.4"));

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private CompletarRegistroUseCase completarRegistroUseCase;
    @InjectMocks
    private MedicionDiariaService service;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final Habito km = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "KM", TipoHabito.CHECKBOX,
            "CUERPO", ExigenciaEvidencia.OBLIGATORIA, Instant.EPOCH);

    @Test
    @DisplayName("ubica el registro de ese día con cerrojo y lo completa por el gesto genérico con el número")
    void delegaEnCompletarConElNumero() {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), ana, km.id(), DIA,
                11, TipoDia.TODOS, true, Instant.EPOCH);
        when(loadHabitoPort.porClaveSistema(PoliticaKilometros.CLAVE_SISTEMA)).thenReturn(Optional.of(km));
        when(loadRegistroPort.porParticipanteHabitoYFechaParaEscritura(ana, km.id(), DIA))
                .thenReturn(Optional.of(registro));

        service.registrar(new RegistrarMedicionCommand(ana, ana, PoliticaKilometros.CLAVE_SISTEMA, DIA, KM));

        ArgumentCaptor<CompletarRegistroCommand> comando = ArgumentCaptor.forClass(CompletarRegistroCommand.class);
        verify(completarRegistroUseCase).completar(comando.capture());
        assertThat(comando.getValue().registroId()).isEqualTo(registro.id());
        assertThat(comando.getValue().gesto()).isEqualTo(GestoCompletar.GENERICO);
        assertThat(comando.getValue().medicion()).isEqualTo(KM);
    }

    @Test
    @DisplayName("nadie registra la medición de otro, y se corta antes de leer nada")
    void otroNoRegistra() {
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.registrar(
                new RegistrarMedicionCommand(otro, ana, PoliticaKilometros.CLAVE_SISTEMA, DIA, KM)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(loadHabitoPort, loadRegistroPort, completarRegistroUseCase);
    }

    @Test
    @DisplayName("sin registro ese día (pausado, o no generado) es un 404, no se inventa uno")
    void sinRegistroEsNoEncontrado() {
        when(loadHabitoPort.porClaveSistema(PoliticaKilometros.CLAVE_SISTEMA)).thenReturn(Optional.of(km));
        when(loadRegistroPort.porParticipanteHabitoYFechaParaEscritura(ana, km.id(), DIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(
                new RegistrarMedicionCommand(ana, ana, PoliticaKilometros.CLAVE_SISTEMA, DIA, KM)))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(completarRegistroUseCase);
    }
}
