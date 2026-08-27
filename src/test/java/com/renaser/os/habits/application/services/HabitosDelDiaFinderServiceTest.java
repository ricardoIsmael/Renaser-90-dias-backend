package com.renaser.os.habits.application.services;

import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitosDelDiaFinderServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T09:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 8, 26);

    @Mock
    private ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    @Mock
    private LoadHabitoPort loadHabitoPort;

    private HabitosDelDiaFinderService service;

    @BeforeEach
    void setUp() {
        service = new HabitosDelDiaFinderService(consultarTracksUseCase, loadHabitoPort);
    }

    private static Habito habito(String titulo) {
        return Habito.crearDeSistema(titulo, TipoHabito.CHECKBOX, "MENTE", ExigenciaEvidencia.OPCIONAL, AHORA);
    }

    @Test
    void invocaElUseCaseExistenteConActorIgualAlParticipanteParaAutoservicio() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(consultarTracksUseCase.consultar(participante, participante, FECHA)).thenReturn(List.of());

        service.deHoy(participante, FECHA);

        verify(consultarTracksUseCase).consultar(participante, participante, FECHA);
    }

    @Test
    void sinRegistrosNoConsultaElCatalogoDeHabitos() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(consultarTracksUseCase.consultar(participante, participante, FECHA)).thenReturn(List.of());

        List<HabitoDelDiaResumen> resultado = service.deHoy(participante, FECHA);

        assertThat(resultado).isEmpty();
        verify(loadHabitoPort, never()).porIds(any());
    }

    @Test
    void resuelveTituloYEstadoConUnaSolaConsultaDeCatalogo() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habito("Meditar");
        RegistroHabito registro = RegistroHabito.generar(participante, habito.id(), FECHA, 5, TipoDia.DISCIPLINA,
                false, AHORA);

        when(consultarTracksUseCase.consultar(participante, participante, FECHA)).thenReturn(List.of(registro));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));

        List<HabitoDelDiaResumen> resultado = service.deHoy(participante, FECHA);

        assertThat(resultado).hasSize(1);
        HabitoDelDiaResumen resumen = resultado.get(0);
        assertThat(resumen.trackId()).isEqualTo(registro.id().value());
        assertThat(resumen.tituloHabito()).isEqualTo("Meditar");
        assertThat(resumen.estado()).isEqualTo("PENDIENTE");
    }
}
