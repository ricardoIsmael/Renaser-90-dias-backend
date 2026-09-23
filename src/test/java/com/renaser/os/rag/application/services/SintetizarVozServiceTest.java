package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.voz.SintetizarVozUseCase;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SintetizarVozServiceTest {

    private static final byte[] WAV = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final FakeUserSummaryFinder usuarios = new FakeUserSummaryFinder();
    private final VozFalsa voz = new VozFalsa();
    private final SintetizarVozService service = new SintetizarVozService(usuarios, voz);

    @Test
    @DisplayName("un actor activo recibe el audio del proveedor, con el texto recortado")
    void activoRecibeElAudio() {
        usuarios.conActor(actor, UserRole.TRAINEE);
        voz.responde(Optional.of(WAV));

        Optional<byte[]> audio = service.sintetizar(actor, "  Hola, ¿como estas?  \n");

        assertThat(audio).containsSame(WAV);
        assertThat(voz.textos).containsExactly("Hola, ¿como estas?");
    }

    @Test
    @DisplayName("sin proveedor de voz devuelve vacio, sin error")
    void sinProveedorDevuelveVacio() {
        usuarios.conActor(actor, UserRole.TRAINEE);
        voz.responde(Optional.empty());

        assertThat(service.sintetizar(actor, "Hola")).isEmpty();
    }

    @Test
    @DisplayName("un texto en blanco es rechazado y no llega al proveedor")
    void textoEnBlanco() {
        usuarios.conActor(actor, UserRole.TRAINEE);

        assertThatThrownBy(() -> service.sintetizar(actor, "   \n\t"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sintetizar(actor, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(voz.textos).isEmpty();
    }

    @Test
    @DisplayName("un texto de mas de 400 caracteres es rechazado; justo 400 entra")
    void textoDemasiadoLargo() {
        usuarios.conActor(actor, UserRole.TRAINEE);
        voz.responde(Optional.of(WAV));
        int tope = SintetizarVozUseCase.LARGO_MAXIMO_TEXTO;

        assertThatThrownBy(() -> service.sintetizar(actor, "a".repeat(tope + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.sintetizar(actor, "a".repeat(tope))).isPresent();
        assertThat(voz.textos).hasSize(1);
    }

    @Test
    @DisplayName("el tope se mide sobre el texto recortado: espacios alrededor no cuentan")
    void elTopeSeMideRecortado() {
        usuarios.conActor(actor, UserRole.TRAINEE);
        voz.responde(Optional.of(WAV));

        String conEspacios = "  " + "a".repeat(SintetizarVozUseCase.LARGO_MAXIMO_TEXTO) + "  ";

        assertThat(service.sintetizar(actor, conEspacios)).isPresent();
    }

    @Test
    @DisplayName("autorizacion negativa: una cuenta SUSPENDIDA recibe NotAuthorizedException y no se sintetiza")
    void suspendidoEsRechazado() {
        usuarios.conActor(actor, UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.sintetizar(actor, "Hola"))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(voz.textos).isEmpty();
    }

    @Test
    @DisplayName("un actor que no existe es NoSuchElementException")
    void actorInexistente() {
        assertThatThrownBy(() -> service.sintetizar(actor, "Hola"))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(voz.textos).isEmpty();
    }

    private static final class VozFalsa implements SintetizarVozPort {

        private final List<String> textos = new ArrayList<>();
        private Optional<byte[]> respuesta = Optional.empty();

        void responde(Optional<byte[]> respuesta) {
            this.respuesta = respuesta;
        }

        @Override
        public Optional<byte[]> sintetizar(String texto) {
            textos.add(texto);
            return respuesta;
        }
    }
}
