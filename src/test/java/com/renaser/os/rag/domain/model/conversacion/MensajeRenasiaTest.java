package com.renaser.os.rag.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COMPANION;
import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COURSE_TUTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensajeRenasiaTest {

    /** El id ya no lo sortea el agregado: entra por parametro, lo arma el caso de uso (IdGenerator). */
    private static final MensajeRenasiaId ID = MensajeRenasiaId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final UserId usuarioId = UserId.of(UUID.randomUUID());
    private final Instant ahora = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void escribirDeUsuarioRechazaContenidoVacio() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, COMPANION, "", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, COMPANION, "   ", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, COMPANION, null, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escribirDeUsuarioRechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, null, COMPANION, "hola", ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** D-102: un mensaje sin agente seria la mezcla de historiales que el dueno pidio no hacer. */
    @Test
    void escribirRechazaAgenteNulo() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, null, "hola", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.escribirDeAsistente(ID, usuarioId, null, "hola", List.of(), ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.rehydrate(ID, usuarioId, null, RolMensaje.USUARIO, "hola",
                List.of(), ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escribirDeUsuarioProducePreguntaSinFuentesConRolUsuario() {
        MensajeRenasia mensaje = MensajeRenasia.escribirDeUsuario(ID, usuarioId, COMPANION, "que es Renasia?", ahora);

        assertThat(mensaje.rol()).isEqualTo(RolMensaje.USUARIO);
        assertThat(mensaje.agente()).isEqualTo(COMPANION);
        assertThat(mensaje.contenido()).isEqualTo("que es Renasia?");
        assertThat(mensaje.fuentes()).isEmpty();
        assertThat(mensaje.usuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void escribirDeAsistenteConservaSusFuentesYSuAgente() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"), FuenteMensaje.of("leccion-2"));

        MensajeRenasia mensaje = MensajeRenasia.escribirDeAsistente(ID, usuarioId, COURSE_TUTOR, "la respuesta",
                fuentes, ahora);

        assertThat(mensaje.rol()).isEqualTo(RolMensaje.ASISTENTE);
        assertThat(mensaje.agente()).isEqualTo(COURSE_TUTOR);
        assertThat(mensaje.fuentes()).containsExactlyElementsOf(fuentes);
    }

    @Test
    void escribirDeAsistenteRechazaContenidoVacioIgualQueUsuario() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeAsistente(ID, usuarioId, COMPANION, " ", List.of(), ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaFuentesEnMensajeDeUsuario() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"));

        assertThatThrownBy(() -> MensajeRenasia.rehydrate(MensajeRenasiaId.of(UUID.randomUUID()), usuarioId,
                COMPANION, RolMensaje.USUARIO, "hola", fuentes, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateAceptaMensajeDeAsistenteConFuentes() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"));

        MensajeRenasia mensaje = MensajeRenasia.rehydrate(MensajeRenasiaId.of(UUID.randomUUID()), usuarioId,
                COURSE_TUTOR, RolMensaje.ASISTENTE, "la respuesta", fuentes, ahora);

        assertThat(mensaje.fuentes()).containsExactlyElementsOf(fuentes);
        assertThat(mensaje.agente()).isEqualTo(COURSE_TUTOR);
    }

    @Test
    void dosMensajesConDistintoIdNuncaSonIguales() {
        MensajeRenasia m1 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()),
                usuarioId, COMPANION, "hola", ahora);
        MensajeRenasia m2 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()),
                usuarioId, COMPANION, "hola", ahora);

        assertThat(m1).isNotEqualTo(m2);
    }

    /** toString nunca lleva el contenido (dato personal), pero si el agente: sirve para depurar mezclas. */
    @Test
    void toStringNoFiltraElContenidoPeroSiElAgente() {
        MensajeRenasia mensaje = MensajeRenasia.escribirDeUsuario(ID, usuarioId, COURSE_TUTOR, "secreto", ahora);

        assertThat(mensaje.toString()).contains("COURSE_TUTOR").doesNotContain("secreto");
    }
}
