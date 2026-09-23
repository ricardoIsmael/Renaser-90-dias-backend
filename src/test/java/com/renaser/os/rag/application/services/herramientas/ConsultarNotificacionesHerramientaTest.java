package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort.AvisoSinLeer;
import com.renaser.os.rag.application.ports.out.notificaciones.GestionarBandejaDeNotificacionesPort.BandejaSinLeer;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code consultar_notificaciones}: el total del badge, las mas recientes y cuanto hace que llegaron. */
class ConsultarNotificacionesHerramientaTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    /** 02:30 UTC: en Lima todavia es el dia anterior. Lo transcurrido no depende de eso. */
    private static final Instant AHORA = Instant.parse("2026-09-24T02:30:00Z");

    private final GestionarBandejaDeNotificacionesPort bandejaPort = mock(GestionarBandejaDeNotificacionesPort.class);
    private final ConsultarNotificacionesHerramienta herramienta =
            new ConsultarNotificacionesHerramienta(bandejaPort, FixedClock.at(AHORA));

    private ResultadoHerramienta ejecutar() {
        return herramienta.ejecutar(PERSONA, InvocacionHerramienta.sinArgumentos(ConsultarNotificacionesHerramienta.NOMBRE));
    }

    @Test
    @DisplayName("lista las mas recientes con su texto y cuanto hace, y cuenta las que no muestra")
    void recientesYResto() {
        when(bandejaPort.sinLeer(PERSONA, ConsultarNotificacionesHerramienta.RECIENTES)).thenReturn(new BandejaSinLeer(7,
                List.of(new AvisoSinLeer("Tu mentor respondio", "Mira la respuesta a tu ticket",
                                Instant.parse("2026-09-24T02:10:00Z")),
                        new AvisoSinLeer("Hito", "Llegaste al dia 30", Instant.parse("2026-09-23T20:30:00Z")))));

        String texto = ((ResultadoHerramienta.Exito) ejecutar()).contenido();

        assertThat(texto).contains("Notificaciones sin leer: 7.")
                .contains("- Tu mentor respondio: Mira la respuesta a tu ticket (hace 20 min)")
                .contains("- Hito: Llegaste al dia 30 (hace 6 h)")
                .contains("(y 5 mas, que puede ver en la app)");
    }

    @Test
    @DisplayName("sin notificaciones sin leer, lo dice sin inventar")
    void bandejaVacia() {
        when(bandejaPort.sinLeer(PERSONA, ConsultarNotificacionesHerramienta.RECIENTES))
                .thenReturn(new BandejaSinLeer(0, List.of()));

        assertThat(ejecutar()).isEqualTo(ResultadoHerramienta.exito("No tiene notificaciones sin leer."));
    }

    @Test
    @DisplayName("una cuenta suspendida recibe un Fallo legible, no la excepcion")
    void suspendida() {
        when(bandejaPort.sinLeer(PERSONA, ConsultarNotificacionesHerramienta.RECIENTES))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThat(((ResultadoHerramienta.Fallo) ejecutar()).motivo()).contains("suspendida");
    }
}
