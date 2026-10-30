package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.habits.application.ports.out.diario.LoadEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.diario.SaveEntradaDiarioPort;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La marca de «día cerrado» en {@code entradas_diario} (spec 017): una por persona y fecha, y la hora del PRIMER cierre se conserva. */
class EvaluacionDelDiaServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final Instant PRIMER_CIERRE = Instant.parse("2026-10-29T03:00:00Z");
    private static final Instant SEGUNDO_CIERRE = Instant.parse("2026-10-29T03:20:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    private final LoadEntradaDiarioPort cargar = mock(LoadEntradaDiarioPort.class);
    private final SaveEntradaDiarioPort guardar = mock(SaveEntradaDiarioPort.class);
    private final IdGenerator ids = mock(IdGenerator.class);

    @BeforeEach
    void preparar() {
        when(ids.newId()).thenReturn(ID);
        when(guardar.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private EvaluacionDelDiaService servicio(Instant ahora) {
        return new EvaluacionDelDiaService(cargar, guardar, FixedClock.at(ahora), ids);
    }

    @Test
    @DisplayName("la primera vez crea la entrada EVALUACION_DIA del dia con la nota")
    void primerCierre() {
        when(cargar.porParticipanteFechaYTipo(ANA, HOY, TipoEntradaDiario.EVALUACION_DIA)).thenReturn(Optional.empty());

        MarcaDeCierre marca = servicio(PRIMER_CIERRE).dejarCerrado(ANA, HOY, "Buen día");

        ArgumentCaptor<EntradaDiario> guardada = ArgumentCaptor.forClass(EntradaDiario.class);
        verify(guardar).save(guardada.capture());
        assertThat(guardada.getValue().tipo()).isEqualTo(TipoEntradaDiario.EVALUACION_DIA);
        assertThat(guardada.getValue().fecha()).isEqualTo(HOY);
        assertThat(guardada.getValue().participanteId()).isEqualTo(ANA);
        assertThat(guardada.getValue().id()).isEqualTo(EntradaDiarioId.of(ID));
        assertThat(marca).isEqualTo(new MarcaDeCierre(HOY, "Buen día", PRIMER_CIERRE));
    }

    @Test
    @DisplayName("un dia sin nota se cierra igual: la marca es la entrada, el texto es opcional")
    void sinNota() {
        when(cargar.porParticipanteFechaYTipo(ANA, HOY, TipoEntradaDiario.EVALUACION_DIA)).thenReturn(Optional.empty());
        assertThat(servicio(PRIMER_CIERRE).dejarCerrado(ANA, HOY, null).nota()).isNull();
    }

    @Test
    @DisplayName("volver a cerrar reemplaza la nota y conserva la hora del primer cierre")
    void reabrirYCambiar() {
        EntradaDiario existente = EntradaDiario.escribir(EntradaDiarioId.of(ID), ANA, HOY, TipoEntradaDiario.EVALUACION_DIA,
                "Nota vieja", PRIMER_CIERRE);
        when(cargar.porParticipanteFechaYTipo(ANA, HOY, TipoEntradaDiario.EVALUACION_DIA)).thenReturn(Optional.of(existente));

        MarcaDeCierre marca = servicio(SEGUNDO_CIERRE).dejarCerrado(ANA, HOY, "Nota nueva");

        assertThat(marca.nota()).isEqualTo("Nota nueva");
        assertThat(marca.cerradoEn()).isEqualTo(PRIMER_CIERRE);
        assertThat(existente.actualizadoEn()).isEqualTo(SEGUNDO_CIERRE);
    }

    @Test
    @DisplayName("volver a cerrar sin nota la borra")
    void reabrirSinNota() {
        EntradaDiario existente = EntradaDiario.escribir(EntradaDiarioId.of(ID), ANA, HOY, TipoEntradaDiario.EVALUACION_DIA,
                "Nota vieja", PRIMER_CIERRE);
        when(cargar.porParticipanteFechaYTipo(ANA, HOY, TipoEntradaDiario.EVALUACION_DIA)).thenReturn(Optional.of(existente));

        assertThat(servicio(SEGUNDO_CIERRE).dejarCerrado(ANA, HOY, null).nota()).isNull();
    }

    @Test
    @DisplayName("las marcas de un rango se piden de a UN tipo y salen con fecha, nota y hora del primer cierre")
    void marcasEntre() {
        EntradaDiario lunes = EntradaDiario.escribir(EntradaDiarioId.of(ID), ANA, HOY.minusDays(2), TipoEntradaDiario.EVALUACION_DIA,
                null, PRIMER_CIERRE);
        when(cargar.entreFechasDeTipo(ANA, TipoEntradaDiario.EVALUACION_DIA, HOY.minusDays(2), HOY)).thenReturn(List.of(lunes));

        assertThat(servicio(SEGUNDO_CIERRE).marcasEntre(ANA, HOY.minusDays(2), HOY))
                .containsExactly(new MarcaDeCierre(HOY.minusDays(2), null, PRIMER_CIERRE));
    }
}
