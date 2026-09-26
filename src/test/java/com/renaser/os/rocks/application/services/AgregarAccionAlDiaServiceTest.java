package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.AccionNueva;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.MotivoRechazo;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.ResultadoAgregado;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase.AgregarRocaDiariaCommand;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-177: {@code rocks.api.AgregarAccionAlDiaPort} solo traduce; las reglas estan en el caso de uso. */
class AgregarAccionAlDiaServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final AccionNueva ACCION = new AccionNueva("CUERPO", "Estirar", 5, false, LocalTime.of(7, 0), null);

    private final AgregarRocaDiariaUseCase agregar = mock(AgregarRocaDiariaUseCase.class);
    private final AgregarAccionAlDiaService service = new AgregarAccionAlDiaService(agregar);

    @Test
    @DisplayName("delega con los mismos datos y devuelve eje, posicion y color que decidio rocks")
    void delega() {
        RocaDiaria agregada = RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), APRENDIZ, JUEVES, 2, "Estirar",
                null, 5, false, EjeObjetivo.CUERPO, null, LocalTime.of(7, 0), null, List.of(),
                FixedClock.at(Instant.parse("2026-09-24T03:00:00Z")));
        when(agregar.agregar(any())).thenReturn(agregada);

        ResultadoAgregado resultado = service.agregar(APRENDIZ, JUEVES, ACCION);

        assertThat(resultado).isEqualTo(new ResultadoAgregado.Agregada("CUERPO", 2, "AMARILLA"));
        verify(agregar).agregar(new AgregarRocaDiariaCommand(APRENDIZ, JUEVES, EjeObjetivo.CUERPO, "Estirar", 5, false,
                LocalTime.of(7, 0), null));
    }

    @Test
    @DisplayName("cada codigo del caso de uso se traduce a su motivo")
    void traduceLosRechazos() {
        assertThat(rechazoAnte(new IllegalStateException("CURRENT_DAY: hoy"))).isEqualTo(MotivoRechazo.DIA_EN_CURSO);
        assertThat(rechazoAnte(new IllegalStateException("AXIS_FULL: lleno"))).isEqualTo(MotivoRechazo.EJE_COMPLETO);
        assertThat(rechazoAnte(new IllegalStateException("DAY_FULL: lleno"))).isEqualTo(MotivoRechazo.DIA_COMPLETO);
        assertThat(rechazoAnte(new IllegalArgumentException("INVALID_DATE: x")))
                .isEqualTo(MotivoRechazo.FECHA_NO_PLANIFICABLE);
        assertThat(rechazoAnte(new IllegalArgumentException("NO_WEEKLY_ROCK: x")))
                .isEqualTo(MotivoRechazo.SIN_OBJETIVO_SEMANAL);
        assertThat(rechazoAnte(new IllegalArgumentException("titulo es obligatorio")))
                .isEqualTo(MotivoRechazo.DATOS_INVALIDOS);
        assertThat(rechazoAnte(new NotAuthorizedException("ROCKS_LOCKED: x"))).isEqualTo(MotivoRechazo.ROCAS_BLOQUEADAS);
        assertThat(rechazoAnte(new NotAuthorizedException("Cuenta suspendida"))).isEqualTo(MotivoRechazo.SIN_ACCESO);
        assertThat(rechazoAnte(new NoSuchElementException("x"))).isEqualTo(MotivoRechazo.SIN_PROGRAMA);
    }

    @Test
    @DisplayName("un eje inventado es DATOS_INVALIDOS sin llamar al caso de uso")
    void ejeInventado() {
        ResultadoAgregado resultado = service.agregar(APRENDIZ, JUEVES,
                new AccionNueva("NEGOCIO", "Vender", 5, false, null, null));

        assertThat(resultado).isEqualTo(new ResultadoAgregado.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        verifyNoInteractions(agregar);
    }

    @Test
    @DisplayName("un conflicto que no es un rechazo conocido sube")
    void conflictoDesconocidoSube() {
        when(agregar.agregar(any())).thenThrow(new IllegalStateException("otra cosa"));

        assertThatThrownBy(() -> service.agregar(APRENDIZ, JUEVES, ACCION)).isInstanceOf(IllegalStateException.class);
    }

    private MotivoRechazo rechazoAnte(RuntimeException rechazo) {
        doThrow(rechazo).when(agregar).agregar(any());
        return ((ResultadoAgregado.Rechazado) service.agregar(APRENDIZ, JUEVES, ACCION)).motivo();
    }
}
