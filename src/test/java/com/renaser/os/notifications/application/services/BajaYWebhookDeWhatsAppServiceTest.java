package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase.EstadoDeUnMensaje;
import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase.RespuestaRecibida;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.preferencia.SavePreferenciaPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.PalabrasDeBaja;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La baja y los estados que llegan del webhook (spec 041, FR-026 y FR-027). La preferencia es la REAL (una lista en memoria con la regla de «sin fila = encendido»):
 * lo que se mide es que la baja apague SU preferencia, conteste UNA vez y no haga nada con lo que no es un mensaje nuestro.
 */
class BajaYWebhookDeWhatsAppServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T00:00:00Z");
    private static final String SU_NUMERO = "51987654321";

    private final EntregasDeWhatsAppEnMemoria entregas = new EntregasDeWhatsAppEnMemoria();
    private final EnvioDeWhatsAppFalso envio = new EnvioDeWhatsAppFalso();
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final List<PreferenciaNotificacion> guardadas = new ArrayList<>();
    private final UserId rosa = UserId.of(UUID.randomUUID());
    private final UserId luis = UserId.of(UUID.randomUUID());
    private WebhookDeWhatsAppService servicio;
    private EntregaDeWhatsApp deRosa;

    @BeforeEach
    void armar() {
        var reloj = FixedClock.at(AHORA);
        LoadPreferenciasPort lectura = mock(LoadPreferenciasPort.class);
        when(lectura.habilitadaPara(any(), any())).thenAnswer(inv -> guardadas.stream()
                .filter(p -> p.usuarioId().equals(inv.getArgument(0)) && p.tipo() == inv.getArgument(1))
                .reduce((primera, ultima) -> ultima).map(PreferenciaNotificacion::habilitada));
        SavePreferenciaPort escritura = guardadas::add;
        when(participaciones.usuariosActivosConRol(any())).thenReturn(List.of(UserId.of(UUID.randomUUID())));
        var baja = new BajaDeWhatsAppService(lectura, escritura, envio, reloj);
        servicio = new WebhookDeWhatsAppService(entregas, baja, new AvisoDeFallaDeWhatsApp(emitir, participaciones, reloj), reloj);
        deRosa = entrega(rosa, "wamid.ROSA", "ycloud-rosa");
    }

    private EntregaDeWhatsApp entrega(UserId persona, String wamid, String proveedor) {
        UUID id = UUID.randomUUID();
        var e = new EntregaDeWhatsApp(id, UUID.randomUUID(), UUID.randomUUID(), persona, 10, EstadoDeEntrega.ACEPTADO, null, wamid, proveedor, AHORA, AHORA);
        entregas.apartar(e);
        return e;
    }

    private static RespuestaRecibida boton(String carga) {
        return new RespuestaRecibida(true, carga, "No recibir más", "wamid.ROSA", SU_NUMERO, "51900000000");
    }

    private static RespuestaRecibida texto(String texto, String respondeA) {
        return new RespuestaRecibida(false, null, texto, respondeA, SU_NUMERO, "51900000000");
    }

    private boolean rosaRecibeWhatsApp() {
        return guardadas.stream().filter(p -> p.usuarioId().equals(rosa) && p.tipo() == TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP)
                .reduce((a, b) -> b).map(PreferenciaNotificacion::habilitada).orElse(true);
    }

    // ── La baja ────────────────────────────────────────────────────────────────

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("tocar «No recibir más» apaga la preferencia de WhatsApp de ESA persona y le contesta una sola vez")
    void elBoton() {
        servicio.respuestaRecibida(boton(PalabrasDeBaja.cargaDelBoton(deRosa.id())));

        assertThat(rosaRecibeWhatsApp()).isFalse();
        assertThat(guardadas).singleElement().extracting(PreferenciaNotificacion::tipo).isEqualTo(TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP);
        assertThat(envio.textos).hasSize(1);
        assertThat(envio.textos.getFirst()[0]).isEqualTo(SU_NUMERO);
        assertThat(envio.textos.getFirst()[1]).contains("ya no te enviaremos").contains("chat").contains("Yo → Notificaciones");
    }

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("un segundo toque no vuelve a contestar")
    void unSegundoToqueNoContesta() {
        servicio.respuestaRecibida(boton(PalabrasDeBaja.cargaDelBoton(deRosa.id())));
        servicio.respuestaRecibida(boton(PalabrasDeBaja.cargaDelBoton(deRosa.id())));

        assertThat(envio.textos).hasSize(1);
        assertThat(guardadas).hasSize(1);
    }

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("escribir «BAJA» RESPONDIENDO a un mensaje nuestro (context.id = su wamid) apaga la preferencia y contesta una vez")
    void bajaRespondiendoAUnMensajeNuestro() {
        servicio.respuestaRecibida(texto("Baja", "wamid.ROSA"));

        assertThat(rosaRecibeWhatsApp()).isFalse();
        assertThat(envio.textos).hasSize(1);
    }

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("«BAJA» suelta (sin responder a nada) no hace nada y no se contesta")
    void bajaSueltaNoHaceNada() {
        servicio.respuestaRecibida(texto("BAJA", null));
        servicio.respuestaRecibida(texto("BAJA", "  "));

        assertThat(guardadas).isEmpty();
        assertThat(envio.textos).isEmpty();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("«BAJA» que responde a un mensaje que no es nuestro (otra conversacion de ese numero) se ignora")
    void bajaAUnMensajeAjeno() {
        servicio.respuestaRecibida(texto("BAJA", "wamid.DE_OTRA_CONVERSACION"));

        assertThat(guardadas).isEmpty();
        assertThat(envio.textos).isEmpty();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("un texto que no es una baja, aunque responda a un mensaje nuestro, no apaga nada")
    void otroTexto() {
        servicio.respuestaRecibida(texto("gracias, ahí estaré", "wamid.ROSA"));

        assertThat(guardadas).isEmpty();
        assertThat(envio.textos).isEmpty();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("el boton de otra plantilla de la cuenta (carga que no es BAJA:<id>) o con un id que no existe no cuenta, ni por su texto")
    void cargaAjena() {
        servicio.respuestaRecibida(boton("HECHO:" + deRosa.id()));
        servicio.respuestaRecibida(boton("BAJA:" + UUID.randomUUID()));
        servicio.respuestaRecibida(boton(null));

        assertThat(guardadas).isEmpty();
        assertThat(envio.textos).isEmpty();
    }

    // spec 041 FR-012, FR-027
    @Test
    @DisplayName("la baja es de la persona a quien pertenece la entrega, no de otra; y no toca el aviso de la app (RECORDATORIO_EVENTO)")
    void soloApagaElWhatsAppDeSuDuenio() {
        EntregaDeWhatsApp deLuis = entrega(luis, "wamid.LUIS", "ycloud-luis");

        servicio.respuestaRecibida(boton(PalabrasDeBaja.cargaDelBoton(deLuis.id())));

        assertThat(guardadas).singleElement().satisfies(p -> {
            assertThat(p.usuarioId()).isEqualTo(luis);
            assertThat(p.tipo()).isEqualTo(TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP);
            assertThat(p.habilitada()).isFalse();
        });
        assertThat(rosaRecibeWhatsApp()).isTrue();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("ningun mensaje recibido se guarda: lo unico que cambia es la preferencia y el estado de la entrega")
    void nadaDeLoRecibidoSeGuarda() {
        servicio.respuestaRecibida(texto("BAJA", "wamid.ROSA"));

        assertThat(entregas.todas()).hasSize(1);
        assertThat(entregas.todas().getFirst().toString()).doesNotContain(SU_NUMERO).doesNotContain("Baja");
    }

    // ── Estados de entrega ─────────────────────────────────────────────────────

    // spec 041 FR-022
    @Test
    @DisplayName("entregado y despues leido avanza el estado; un «enviado» tardio no lo deshace")
    void elEstadoSoloAvanza() {
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), "ycloud-rosa", "delivered", null, "wamid.ROSA"));
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), "ycloud-rosa", "read", null, "wamid.ROSA"));
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), "ycloud-rosa", "sent", null, "wamid.ROSA"));

        assertThat(entregas.porId(deRosa.id())).get().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.LEIDO);
    }

    // spec 041 FR-022
    @Test
    @DisplayName("se reconoce la entrega por el externalId o, si falta, por el id del proveedor; lo que no es nuestro no toca nada")
    void sePuedeBuscarDeDosManeras() {
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(null, "ycloud-rosa", "delivered", null, null));
        assertThat(entregas.porId(deRosa.id())).get().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.ENTREGADO);

        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(UUID.randomUUID().toString(), "otro", "read", null, null));
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje("no-es-un-uuid", null, "read", null, null));
        assertThat(entregas.porId(deRosa.id())).get().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.ENTREGADO);
    }

    // spec 041 FR-022
    @Test
    @DisplayName("un estado que no conocemos no cambia nada")
    void estadoDesconocido() {
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), null, "deleted", null, null));

        assertThat(entregas.porId(deRosa.id())).get().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.ACEPTADO);
    }

    // spec 041 FR-022, FR-024
    @Test
    @DisplayName("«failed» queda FALLIDO con el motivo; si es por la plantilla, Administracion lo sabe")
    void fallidoPorLaPlantilla() {
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), "ycloud-rosa", "failed", "132001 · Template does not exist", null));

        assertThat(entregas.porId(deRosa.id())).get().satisfies(e -> {
            assertThat(e.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
            assertThat(e.motivo()).contains("Template does not exist");
        });
        ArgumentCaptor<EmitirNotificacionCommand> aviso = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(aviso.capture());
        assertThat(aviso.getValue().tipo()).isEqualTo(TipoNotificacion.OPERACION_PLATAFORMA);
    }

    // spec 041 FR-022
    @Test
    @DisplayName("un «failed» despues de entregado no deshace lo entregado ni avisa a nadie")
    void fallidoDespuesDeEntregado() {
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), null, "delivered", null, null));
        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(deRosa.id().toString(), null, "failed", "Template does not exist", null));

        assertThat(entregas.porId(deRosa.id())).get().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.ENTREGADO);
        verify(emitir, never()).emitir(any(EmitirNotificacionCommand.class));
    }

    // spec 041 FR-022
    @Test
    @DisplayName("el wamid del aviso se guarda para poder reconocer despues un «BAJA» que responde a ese mensaje")
    void guardaElWamid() {
        EntregaDeWhatsApp sinWamid = entrega(luis, null, null);

        servicio.estadoDeUnMensaje(new EstadoDeUnMensaje(sinWamid.id().toString(), "ycloud-luis", "sent", null, "wamid.LUIS"));
        servicio.respuestaRecibida(texto("stop", "wamid.LUIS"));

        assertThat(guardadas).singleElement().extracting(PreferenciaNotificacion::usuarioId).isEqualTo(luis);
        assertThat(entregas.porWamid("wamid.LUIS")).isPresent();
    }
}
