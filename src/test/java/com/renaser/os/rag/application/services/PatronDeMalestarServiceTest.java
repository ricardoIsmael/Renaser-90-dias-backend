package com.renaser.os.rag.application.services;

import com.renaser.os.rag.api.PatronDeMalestarRepetidoEvent;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.seguridad.MensajeDeApoyo;
import com.renaser.os.rag.domain.model.seguridad.PatronDeMalestarRepetido;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las dos respuestas a que el patron se repita, sobre puertos mockeados: el aviso que sale por
 * evento y el texto que vuelve para la persona. La regla del umbral tiene su propia prueba de
 * dominio; aca se verifica que el caso de uso la usa bien y que las dos mitades son independientes
 * — en particular, que el aviso se emite aunque el texto del MINSA no este configurado.
 */
@ExtendWith(MockitoExtension.class)
class PatronDeMalestarServiceTest {

    /** 03:00 UTC = 22:00 del dia anterior en Lima (regla 02 §3). */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-15T03:00:00Z"));
    private static final String TEXTO_DE_APOYO_DE_PRUEBA =
            "Texto de prueba: el del MINSA todavia no esta confirmado y no se inventa.";

    @Mock
    private LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ApplicationEventPublisher publisher;

    private final UserId persona = UserId.of(UUID.randomUUID());

    private PatronDeMalestarService servicioCon(MensajeDeApoyo mensajeDeApoyo) {
        return new PatronDeMalestarService(loadMensajeRenasiaPort, userSummaryFinder, publisher, mensajeDeApoyo,
                CLOCK);
    }

    private MensajeRenasia deLaPersona(String texto, long haceDias) {
        return MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), persona,
                AgenteConversacional.COMPANION, texto, CLOCK.now().minus(haceDias, ChronoUnit.DAYS));
    }

    private void laVentanaTiene(List<MensajeRenasia> mensajes) {
        when(loadMensajeRenasiaPort.escritosPorElUsuarioDesde(any(), any())).thenReturn(mensajes);
        lenient().when(userSummaryFinder.findById(persona)).thenReturn(
                Optional.of(new UserSummary(persona, "Ana Quispe", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    private PatronDeMalestarRepetidoEvent eventoPublicado() {
        ArgumentCaptor<PatronDeMalestarRepetidoEvent> captor =
                ArgumentCaptor.forClass(PatronDeMalestarRepetidoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    /**
     * El caso normal, que es el 99% de los mensajes: si lo recien escrito no contiene ninguna
     * expresion, la cuenta de la ventana no pudo subir y no se consulta nada. Sin esto, cada
     * mensaje de cada persona pagaria una consulta a la base para nada.
     */
    @Test
    @DisplayName("un mensaje corriente no toca la base de datos")
    void noConsultaNadaSiElMensajeNuevoNoCuenta() {
        Optional<String> apoyo = servicioCon(MensajeDeApoyo.sinConfigurar())
                .revisar(persona, "no puedo a esa hora, lo muevo a las 7?");

        assertThat(apoyo).isEmpty();
        verify(loadMensajeRenasiaPort, never()).escritosPorElUsuarioDesde(any(), any());
        verify(publisher, never()).publishEvent(any(PatronDeMalestarRepetidoEvent.class));
    }

    @Test
    @DisplayName("con dos detecciones no avisa a nadie ni muestra nada")
    void noAvisaPorDebajoDelUmbral() {
        laVentanaTiene(List.of(
                deLaPersona("me siento mal", 3),
                deLaPersona("ya no puedo mas", 0)));

        Optional<String> apoyo = servicioCon(new MensajeDeApoyo(TEXTO_DE_APOYO_DE_PRUEBA))
                .revisar(persona, "ya no puedo mas");

        assertThat(apoyo).isEmpty();
        verify(publisher, never()).publishEvent(any(PatronDeMalestarRepetidoEvent.class));
    }

    @Test
    @DisplayName("a la tercera avisa y devuelve el texto configurado")
    void avisaYAcompanaAlLlegarAlUmbral() {
        laVentanaTiene(List.of(
                deLaPersona("me siento mal", 4),
                deLaPersona("ya no doy mas", 2),
                deLaPersona("ya no puedo mas", 0)));

        Optional<String> apoyo = servicioCon(new MensajeDeApoyo(TEXTO_DE_APOYO_DE_PRUEBA))
                .revisar(persona, "ya no puedo mas");

        assertThat(apoyo).contains(TEXTO_DE_APOYO_DE_PRUEBA);
        PatronDeMalestarRepetidoEvent evento = eventoPublicado();
        assertThat(evento.usuarioId()).isEqualTo(persona.value());
        assertThat(evento.nombreDeLaPersona()).isEqualTo("Ana Quispe");
        assertThat(evento.detecciones()).isEqualTo(3);
        assertThat(evento.diasDeLaVentana()).isEqualTo(PatronDeMalestarRepetido.diasDeLaVentana());
        assertThat(evento.detectadoEn()).isEqualTo(CLOCK.now());
        assertThat(evento.rutaApp()).isEqualTo("/admin/trainees/" + persona.value());
    }

    /**
     * La mitad que no depende de ningun texto. Mientras el MINSA siga sin confirmarse, la persona
     * no ve nada —preferimos no decirle nada antes que un numero inventado— pero un humano SI se
     * entera y puede actuar. Si esta prueba se rompiera, el mecanismo entero quedaria dormido
     * esperando una propiedad que nadie sabe que hay que completar.
     */
    @Test
    @DisplayName("sin texto configurado el aviso sale igual, y a la persona no se le muestra nada")
    void avisaAunqueElTextoDelMinsaNoEsteConfigurado() {
        laVentanaTiene(List.of(
                deLaPersona("me siento mal", 4),
                deLaPersona("ya no doy mas", 2),
                deLaPersona("ya no puedo mas", 0)));

        Optional<String> apoyo = servicioCon(MensajeDeApoyo.sinConfigurar()).revisar(persona, "ya no puedo mas");

        assertThat(apoyo).isEmpty();
        assertThat(eventoPublicado().detecciones()).isEqualTo(3);
    }

    @Test
    @DisplayName("el evento nunca lleva lo que la persona escribio")
    void elAvisoNoFiltraElContenidoDeLaConversacion() {
        String confidencial = "me siento mal por lo que paso con mi jefe el lunes";
        laVentanaTiene(List.of(
                deLaPersona(confidencial, 4),
                deLaPersona("ya no doy mas", 2),
                deLaPersona("ya no puedo mas", 0)));

        servicioCon(MensajeDeApoyo.sinConfigurar()).revisar(persona, "ya no puedo mas");

        assertThat(eventoPublicado().toString()).doesNotContain("jefe");
    }

    /** Un usuario sin nombre resoluble no puede dejar al administrador sin aviso. */
    @Test
    void avisaIgualSiNoSePuedeResolverElNombre() {
        when(loadMensajeRenasiaPort.escritosPorElUsuarioDesde(any(), any())).thenReturn(List.of(
                deLaPersona("me siento mal", 4),
                deLaPersona("ya no doy mas", 2),
                deLaPersona("ya no puedo mas", 0)));
        when(userSummaryFinder.findById(persona)).thenReturn(Optional.empty());

        servicioCon(MensajeDeApoyo.sinConfigurar()).revisar(persona, "ya no puedo mas");

        assertThat(eventoPublicado().nombreDeLaPersona()).isNotBlank();
    }
}
