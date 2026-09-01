package com.renaser.os.rag.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, "", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, "   ", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, usuarioId, null, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escribirDeUsuarioRechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeUsuario(ID, null, "hola", ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escribirDeUsuarioProducePreguntaSinFuentesConRolUsuario() {
        MensajeRenasia mensaje = MensajeRenasia.escribirDeUsuario(ID, usuarioId, "que es Renasia?", ahora);

        assertThat(mensaje.rol()).isEqualTo(RolMensaje.USUARIO);
        assertThat(mensaje.contenido()).isEqualTo("que es Renasia?");
        assertThat(mensaje.fuentes()).isEmpty();
        assertThat(mensaje.usuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void escribirDeAsistenteConservaSusFuentes() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"), FuenteMensaje.of("leccion-2"));

        MensajeRenasia mensaje = MensajeRenasia.escribirDeAsistente(ID, usuarioId, "la respuesta", fuentes, ahora);

        assertThat(mensaje.rol()).isEqualTo(RolMensaje.ASISTENTE);
        assertThat(mensaje.fuentes()).containsExactlyElementsOf(fuentes);
    }

    @Test
    void escribirDeAsistenteRechazaContenidoVacioIgualQueUsuario() {
        assertThatThrownBy(() -> MensajeRenasia.escribirDeAsistente(ID, usuarioId, " ", List.of(), ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateRechazaFuentesEnMensajeDeUsuario() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"));

        assertThatThrownBy(() -> MensajeRenasia.rehydrate(MensajeRenasiaId.of(UUID.randomUUID()), usuarioId,
                RolMensaje.USUARIO, "hola", fuentes, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateAceptaMensajeDeAsistenteConFuentes() {
        List<FuenteMensaje> fuentes = List.of(FuenteMensaje.of("leccion-1"));

        MensajeRenasia mensaje = MensajeRenasia.rehydrate(MensajeRenasiaId.of(UUID.randomUUID()), usuarioId,
                RolMensaje.ASISTENTE, "la respuesta", fuentes, ahora);

        assertThat(mensaje.fuentes()).containsExactlyElementsOf(fuentes);
    }

    @Test
    void dosMensajesConDistintoIdNuncaSonIguales() {
        MensajeRenasia m1 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()),
                usuarioId, "hola", ahora);
        MensajeRenasia m2 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()),
                usuarioId, "hola", ahora);

        assertThat(m1).isNotEqualTo(m2);
    }
}
