package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase.RegistrarObservacionCommand;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.ANA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LIDER;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LUISA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Al guardar una observación el servidor se la entrega al mentor (spec 056, FR-025 y SC-005): su texto por el chat directo del líder con el mentor y un
 * aviso para su teléfono, y una sola vez por {@code operationKey}. Antes lo hacía la app, y una app que fallaba a medias dejaba la observación
 * guardada sin que el mentor se enterara.
 */
class ObservacionAvisaAlMentorTest {

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();
    private ObservacionesDeMentorService observaciones;

    @BeforeEach
    void preparar() {
        PadronDeMentoresServiceTest.prepararCuerpo(banco);
        observaciones = banco.observacionesService();
    }

    private static RegistrarObservacionCommand sugerencia(String clave) {
        return new RegistrarObservacionCommand(LIDER, LUISA, "SUGERENCIA", "Las consultas del fin de semana", false, null, clave);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: guardar una observación le manda el texto al mentor por su chat con el líder y publica el aviso para su teléfono")
    void leLlegaAlMentorPorElChatYPorElAviso() {
        ObservacionDeMentor guardada = observaciones.registrar(sugerencia("k1"));

        assertThat(banco.mensajesDirectos).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.de()).isEqualTo(LIDER);
            assertThat(mensaje.para()).isEqualTo(LUISA);
            assertThat(mensaje.texto()).isEqualTo("Las consultas del fin de semana");
        });
        assertThat(guardada.enviadaPorChat()).as("la observación dice, de verdad, que llegó al chat").isTrue();
        assertThat(guardada.mensajeId()).isEqualTo(banco.mensajesDirectos.getFirst().mensajeId());
        assertThat(banco.avisosPublicados).singleElement().isInstanceOfSatisfying(ObservacionDelLiderEvent.class, aviso -> {
            assertThat(aviso.mentorId()).isEqualTo(LUISA.value());
            assertThat(aviso.autorId()).isEqualTo(LIDER.value());
            assertThat(aviso.tipo()).isEqualTo("SUGERENCIA");
            assertThat(aviso.observacionId()).isEqualTo(guardada.id());
            assertThat(aviso.rutaApp()).as("el aviso lleva al chat donde está el texto")
                    .isEqualTo("/chat/" + banco.mensajesDirectos.getFirst().conversacionId());
        });
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: la misma operationKey no reenvía el mensaje ni repite el aviso")
    void unaSolaVezPorOperationKey() {
        ObservacionDeMentor primera = observaciones.registrar(sugerencia("k1"));
        ObservacionDeMentor repetida = observaciones.registrar(sugerencia("k1"));

        assertThat(repetida.id()).isEqualTo(primera.id());
        assertThat(banco.mensajesDirectos).hasSize(1);
        assertThat(banco.avisosPublicados).hasSize(1);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: dos observaciones distintas son dos mensajes y dos avisos, cada uno con su clave de deduplicación")
    void cadaObservacionTieneSuMensajeYSuAviso() {
        observaciones.registrar(sugerencia("k1"));
        observaciones.registrar(sugerencia("k2"));

        assertThat(banco.mensajesDirectos).hasSize(2);
        assertThat(banco.avisosPublicados).hasSize(2).extracting(a -> ((ObservacionDelLiderEvent) a).claveDeduplicacion()).doesNotHaveDuplicates();
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: una app vieja que ya mandó el mensaje (trae messageId) no lo reenvía; el aviso sale igual y el mensaje queda enlazado")
    void unaAppViejaQueYaMandoElMensajeNoLoReenvia() {
        UUID yaEnviado = UUID.randomUUID();

        ObservacionDeMentor guardada = observaciones.registrar(new RegistrarObservacionCommand(LIDER, LUISA, "ALERTA", "Revisa el cierre de Ana", true,
                yaEnviado, "k1"));

        assertThat(banco.mensajesDirectos).as("el mensaje ya lo mandó la app: no se duplica en el chat").isEmpty();
        assertThat(guardada.mensajeId()).isEqualTo(yaEnviado);
        assertThat(guardada.enviadaPorChat()).isTrue();
        assertThat(banco.avisosPublicados).singleElement().isInstanceOfSatisfying(ObservacionDelLiderEvent.class, aviso -> {
            assertThat(aviso.tipo()).isEqualTo("ALERTA");
            assertThat(aviso.rutaApp()).as("sin conversación conocida el aviso no inventa una ruta").isNull();
        });
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: si el chat no puede entregar el mensaje, el motivo sube y no queda guardada la observación ni sale el aviso")
    void siElChatRechazaNoQuedaNada() {
        banco.chatRechaza = new NotAuthorizedException("Los chats directos no están disponibles en esta formación");

        assertThatThrownBy(() -> observaciones.registrar(sugerencia("k1"))).isInstanceOf(NotAuthorizedException.class).hasMessageContaining("chats directos");

        assertThat(banco.observaciones).isEmpty();
        assertThat(banco.avisosPublicados).isEmpty();
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: una observación sobre una persona del grupo también le llega al mentor, con la persona y la semana guardadas")
    void sobreUnaPersonaDelGrupoTambienLlega() {
        ObservacionDeMentor guardada = observaciones.registrar(new RegistrarObservacionCommand(LIDER, LUISA, "SUGERENCIA", "Mira el semáforo de Ana", false,
                null, "k1", ANA, LocalDate.of(2026, 9, 26)));

        assertThat(guardada.personaId()).isEqualTo(ANA);
        assertThat(banco.mensajesDirectos).hasSize(1);
        assertThat(banco.avisosPublicados).hasSize(1);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: quien no es del liderazgo (un mentor) no deja observaciones: 403 antes de escribir al chat")
    void unMentorNoDejaObservaciones() {
        UserId raul = PadronDeMentoresServiceTest.RAUL;

        assertThatThrownBy(() -> observaciones.registrar(new RegistrarObservacionCommand(raul, LUISA, "SUGERENCIA", "x", false, null, "k1")))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(banco.mensajesDirectos).isEmpty();
        assertThat(banco.avisosPublicados).isEmpty();
    }
}
