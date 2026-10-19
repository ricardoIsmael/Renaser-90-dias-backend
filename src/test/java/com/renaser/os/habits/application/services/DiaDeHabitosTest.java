package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase.RegistrosDelDia;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiaDeHabitosTest {
    @ParameterizedTest
    @CsvSource({
        "America/Lima, 2026-10-17T04:59:59Z, 2026-10-16, 2026-10-17T05:00:00Z",
        "Europe/Madrid, 2026-10-17T04:59:59Z, 2026-10-17, 2026-10-17T22:00:00Z",
        "Europe/Madrid, 2026-03-29T00:00:00Z, 2026-03-29, 2026-03-29T22:00:00Z",
        "Europe/Madrid, 2026-10-24T23:00:00Z, 2026-10-25, 2026-10-25T23:00:00Z"
    })
    void conservaFechaZonaYCierreAunqueLaListaEsteVacia(String zona, Instant ahora, LocalDate fecha, Instant cierre) {
        var consulta = mock(ConsultarTracksDelDiaUseCase.class);
        var generacion = mock(GenerarTracksDelDiaUseCase.class);
        UserId actor = UserId.of(UUID.randomUUID());
        var registros = new RegistrosDelDia(List.of(), fecha, ZoneId.of(zona));
        when(consulta.consultarHoy(actor, actor)).thenReturn(registros);
        when(consulta.consultarEnSuZona(actor, actor, fecha)).thenReturn(registros);
        var servicio = new TracksDelDiaProyeccionService(consulta, generacion,
                mock(com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort.class),
                mock(com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort.class),
                mock(com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort.class),
                mock(com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort.class),
                mock(com.renaser.os.evidence.api.RegistrosConEvidenciaFinder.class),
                mock(com.renaser.os.habits.application.ports.out.renombre.LoadRenombreHabitoPort.class),
                new MedicionesDelDia(List.of(), (p, c, h) -> Map.of()),
                new RachasDeHabitos((p, h, d, a) -> Map.of()), FixedClock.at(ahora),
                mock(PlatformTransactionManager.class));

        var dia = servicio.consultarHoyConContextoDe(actor);

        assertThat(dia.fechaLocal()).isEqualTo(fecha);
        assertThat(dia.zonaHoraria()).isEqualTo(ZoneId.of(zona));
        assertThat(dia.cierraEn()).isEqualTo(cierre);
        assertThat(dia.ahoraServidor()).isEqualTo(ahora);
        assertThat(dia.tracks()).isEmpty();
        verify(generacion).generarDisponiblesAhora(actor);
        verify(consulta).consultarEnSuZona(actor, actor, fecha);
    }
}
