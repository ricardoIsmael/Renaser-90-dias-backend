package com.renaser.os.rag.domain.model.seguridad;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La regla del umbral, sin Spring y sin base de datos. */
class PatronDeMalestarRepetidoTest {

    /**
     * 03:00 UTC es 22:00 del dia ANTERIOR en Lima (regla 02 §3: todo test de un comportamiento
     * diario tiene que incluir una hora UTC que caiga en el dia local previo). Aca la ventana se
     * mide en horas exactas y no en dias calendario —justamente para no depender de ninguna zona—,
     * pero el reloj se fija igual en esa franja para que, si alguien cambia la regla a dias
     * calendario, el test lo note en vez de taparlo.
     */
    private static final Instant AHORA = Instant.parse("2026-09-15T03:00:00Z");

    private final UserId persona = UserId.of(UUID.randomUUID());

    private MensajeRenasia deLaPersona(String texto, Instant cuando) {
        return MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), persona,
                AgenteConversacional.COMPANION, texto, cuando);
    }

    private MensajeRenasia delAsistente(String texto, Instant cuando) {
        return MensajeRenasia.escribirDeAsistente(MensajeRenasiaId.of(UUID.randomUUID()), persona,
                AgenteConversacional.COMPANION, texto, List.of(), cuando);
    }

    private static Instant haceDias(long dias) {
        return AHORA.minus(dias, ChronoUnit.DAYS);
    }

    private Optional<PatronDeMalestarRepetido> evaluar(List<MensajeRenasia> mensajes) {
        return PatronDeMalestarRepetido.enLaVentana(persona, mensajes, AHORA);
    }

    @Test
    @DisplayName("dos detecciones todavia no son un patron; la tercera si")
    void exigeElUmbralCompleto() {
        List<MensajeRenasia> dos = List.of(
                deLaPersona("me siento mal", haceDias(2)),
                deLaPersona("ya no puedo mas", haceDias(1)));
        assertThat(evaluar(dos)).isEmpty();

        List<MensajeRenasia> tres = List.of(
                deLaPersona("me siento mal", haceDias(2)),
                deLaPersona("ya no puedo mas", haceDias(1)),
                deLaPersona("no doy mas", AHORA));
        assertThat(evaluar(tres)).isPresent();
        assertThat(evaluar(tres).orElseThrow().detecciones()).isEqualTo(3);
    }

    @Test
    @DisplayName("lo que quedo fuera de la ventana no cuenta, aunque siga guardado")
    void lasDeteccionesViejasSalenSolas() {
        List<MensajeRenasia> mensajes = List.of(
                deLaPersona("me siento mal", haceDias(8)),
                deLaPersona("ya no puedo mas", haceDias(6)),
                deLaPersona("no doy mas", haceDias(1)));

        assertThat(evaluar(mensajes)).isEmpty();
    }

    @Test
    @DisplayName("el borde exacto de la ventana cuenta")
    void elLimiteDeLaVentanaEsInclusivo() {
        List<MensajeRenasia> mensajes = List.of(
                deLaPersona("me siento mal", PatronDeMalestarRepetido.inicioDeLaVentana(AHORA)),
                deLaPersona("ya no puedo mas", haceDias(3)),
                deLaPersona("no doy mas", AHORA));

        assertThat(evaluar(mensajes)).isPresent();
    }

    /**
     * El asistente repite la frase de la persona al responderle ("entiendo que no puedas mas...").
     * Si esos mensajes contaran, un solo desahogo dispararia el umbral solo.
     */
    @Test
    void lasRespuestasDelAsistenteNoCuentan() {
        List<MensajeRenasia> mensajes = List.of(
                deLaPersona("me siento mal", haceDias(2)),
                delAsistente("entiendo que te sientas mal, no puedo mas es algo que muchos dicen", haceDias(2)),
                delAsistente("me siento mal por lo que contas", haceDias(1)));

        assertThat(evaluar(mensajes)).isEmpty();
    }

    @Test
    void losMensajesCorrientesNoSuman() {
        List<MensajeRenasia> mensajes = List.of(
                deLaPersona("no puedo con este habito", haceDias(3)),
                deLaPersona("no puedo a esa hora", haceDias(2)),
                deLaPersona("me siento mal preparado para la exposicion", haceDias(1)));

        assertThat(evaluar(mensajes)).isEmpty();
    }

    /**
     * La clave es del EPISODIO: mientras la primera deteccion siga dentro de la ventana, agregar
     * mensajes nuevos no la mueve — y por eso el administrador recibe UN aviso y no uno por mensaje.
     */
    @Test
    @DisplayName("la clave de deduplicacion no se mueve mientras el episodio siga abierto")
    void laClaveEsEstableDentroDelMismoEpisodio() {
        MensajeRenasia primera = deLaPersona("me siento mal", haceDias(4));
        List<MensajeRenasia> tres = List.of(primera,
                deLaPersona("ya no puedo mas", haceDias(2)),
                deLaPersona("no doy mas", haceDias(1)));
        List<MensajeRenasia> cuatro = List.of(primera,
                deLaPersona("ya no puedo mas", haceDias(2)),
                deLaPersona("no doy mas", haceDias(1)),
                deLaPersona("no aguanto mas", AHORA));

        UUID claveConTres = evaluar(tres).orElseThrow().claveDeDeduplicacion();
        UUID claveConCuatro = evaluar(cuatro).orElseThrow().claveDeDeduplicacion();

        assertThat(claveConCuatro).isEqualTo(claveConTres);
        assertThat(evaluar(cuatro).orElseThrow().detecciones()).isEqualTo(4);
    }

    @Test
    @DisplayName("dos personas con las mismas fechas no comparten clave")
    void laClaveIncluyeALaPersona() {
        List<MensajeRenasia> mensajes = List.of(
                deLaPersona("me siento mal", haceDias(2)),
                deLaPersona("ya no puedo mas", haceDias(1)),
                deLaPersona("no doy mas", AHORA));
        PatronDeMalestarRepetido unaPersona = evaluar(mensajes).orElseThrow();
        PatronDeMalestarRepetido otraPersona = new PatronDeMalestarRepetido(UserId.of(UUID.randomUUID()),
                unaPersona.detecciones(), unaPersona.inicioDelPatron());

        assertThat(otraPersona.claveDeDeduplicacion()).isNotEqualTo(unaPersona.claveDeDeduplicacion());
    }

    @Test
    void noSePuedeConstruirUnPatronPorDebajoDelUmbral() {
        assertThatThrownBy(() -> new PatronDeMalestarRepetido(persona, 2, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Si alguien mueve los numeros, que sepa que son los dos valores del mecanismo entero. */
    @Test
    void losDosNumerosDelUmbralSonLosEsperadosHoy() {
        assertThat(PatronDeMalestarRepetido.DETECCIONES_PARA_AVISAR).isEqualTo(3);
        assertThat(PatronDeMalestarRepetido.diasDeLaVentana()).isEqualTo(7);
    }
}
