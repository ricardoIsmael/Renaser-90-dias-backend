package com.renaser.os.rag.infrastructure.adapter.out.diario;

import com.renaser.os.habits.api.DiarioYRadarPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.BitacoraDeHoy;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadar;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadarRegistrado;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.RespuestasRadar;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El adaptador solo traduce los records de {@code habits.api.DiarioYRadarPort} a los de {@code rag}. */
class DiarioYRadarDelAprendizAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 9, 23, 22, 10);

    private final DiarioYRadarPort habits = mock(DiarioYRadarPort.class);
    private final DiarioYRadarDelAprendizAdapter adapter = new DiarioYRadarDelAprendizAdapter(habits);

    @Test
    @DisplayName("traduce bitacora, ultimo radar y registro sin perder ningun campo")
    void traduce() {
        when(habits.bitacoraDeHoy(APRENDIZ)).thenReturn(new DiarioYRadarPort.BitacoraDeHoy(HOY, true, "texto", true));
        assertThat(adapter.bitacoraDeHoy(APRENDIZ)).isEqualTo(new BitacoraDeHoy(HOY, true, "texto", true));

        when(habits.escribirBitacoraDeHoy(APRENDIZ, "nuevo"))
                .thenReturn(new DiarioYRadarPort.BitacoraDeHoy(HOY, true, "nuevo", false));
        assertThat(adapter.escribirBitacoraDeHoy(APRENDIZ, "nuevo")).isEqualTo(new BitacoraDeHoy(HOY, true, "nuevo", false));

        DiarioYRadarPort.RespuestasRadar deHabits = new DiarioYRadarPort.RespuestasRadar("a", "b", "c", 5, "d");
        when(habits.ultimoCheckInRadar(APRENDIZ))
                .thenReturn(Optional.of(new DiarioYRadarPort.CheckInRadar(HORA, true, deHabits)));
        assertThat(adapter.ultimoCheckInRadar(APRENDIZ))
                .contains(new CheckInRadar(HORA, true, new RespuestasRadar("a", "b", "c", 5, "d")));

        when(habits.registrarCheckInRadar(APRENDIZ, deHabits))
                .thenReturn(new DiarioYRadarPort.CheckInRadarRegistrado(HORA, true));
        assertThat(adapter.registrarCheckInRadar(APRENDIZ, new RespuestasRadar("a", "b", "c", 5, "d")))
                .isEqualTo(new CheckInRadarRegistrado(HORA, true));
    }
}
