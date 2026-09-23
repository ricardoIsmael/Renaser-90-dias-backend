package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort.BandejaSinLeer;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_marcar_notificaciones_leidas} y su escritura: se propone solo si hay algo que
 * marcar, nunca se marca al proponer, y al confirmar delega en {@code notifications}.
 */
class MarcarNotificacionesLeidasTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final InvocacionHerramienta INVOCACION =
            InvocacionHerramienta.sinArgumentos(PropuestaDeMarcarNotificacionesLeidas.NOMBRE);

    private final GestionarBandejaDeNotificacionesPort bandejaPort = mock(GestionarBandejaDeNotificacionesPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final PropuestaDeMarcarNotificacionesLeidas propuesta =
            new PropuestaDeMarcarNotificacionesLeidas(bandejaPort, proponerAccion);
    private final MarcarNotificacionesLeidasConfirmable confirmable =
            new MarcarNotificacionesLeidasConfirmable(bandejaPort);

    @Test
    @DisplayName("con no leidas, propone con el numero exacto y NO marca nada")
    void propone() {
        when(bandejaPort.sinLeer(PERSONA, 0)).thenReturn(new BandejaSinLeer(4, List.of()));

        ResultadoHerramienta resultado = propuesta.ejecutar(PERSONA, INVOCACION);

        verify(proponerAccion).proponer(PERSONA, INVOCACION, "Marcar como leidas tus 4 notificaciones sin leer");
        verify(bandejaPort, never()).marcarTodasLeidas(any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("sin no leidas, no ofrece un boton que no cambia nada")
    void nadaQueMarcar() {
        when(bandejaPort.sinLeer(PERSONA, 0)).thenReturn(new BandejaSinLeer(0, List.of()));

        assertThat(propuesta.ejecutar(PERSONA, INVOCACION)).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("al confirmar, marca todas via notifications y dice cuantas")
    void confirma() {
        when(bandejaPort.marcarTodasLeidas(PERSONA)).thenReturn(4);

        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeMarcarNotificacionesLeidas.NOMBRE);
        assertThat(confirmable.aplicar(PERSONA, INVOCACION))
                .isEqualTo(ResultadoHerramienta.exito("Listo: 4 notificaciones marcadas como leidas."));
    }

    @Test
    @DisplayName("si la cuenta se suspendio entre proponer y confirmar, Fallo legible")
    void suspendidaAlConfirmar() {
        when(bandejaPort.marcarTodasLeidas(PERSONA)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThat(((ResultadoHerramienta.Fallo) confirmable.aplicar(PERSONA, INVOCACION)).motivo())
                .contains("no se cambio nada");
    }
}
