package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VozDelOrbeServiceTest {

    private static final byte[] SONIDO = {'R', 'I', 'F', 'F', 1, 2, 3};

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final FakeUserSummaryFinder usuarios = new FakeUserSummaryFinder().conActor(actor, UserRole.TRAINEE);
    private final VozFalsa voz = new VozFalsa();
    // 03:00 UTC: en Lima todavia es el dia anterior (regla 02), aunque aca no hay dias de por medio.
    private final RelojMovible reloj = new RelojMovible(Instant.parse("2026-09-23T03:00:00Z"));
    private final VozDelOrbeService service = new VozDelOrbeService(usuarios, voz, reloj, Runnable::run);

    private byte[] escuchar(VozDelOrbeUseCase.AudioDelOrbe audio) throws Exception {
        ByteArrayOutputStream escuchado = new ByteArrayOutputStream();
        audio.escribirEn(escuchado);
        return escuchado.toByteArray();
    }

    @Test
    @DisplayName("preparar genera el audio con el texto recortado y su dueno lo encuentra y lo escucha")
    void prepararYEscuchar() throws Exception {
        UUID id = service.preparar(actor, "  Hola  ").orElseThrow();

        VozDelOrbeUseCase.AudioDelOrbe audio = service.buscar(actor, id).orElseThrow();
        assertThat(voz.textos).containsExactly("Hola");
        assertThat(audio.tieneSonido()).isTrue();
        assertThat(escuchar(audio)).isEqualTo(SONIDO);
    }

    @Test
    @DisplayName("sin proveedor de voz es vacio y ni se intenta generar")
    void sinProveedorEsVacio() {
        voz.disponible = false;

        assertThat(service.preparar(actor, "Hola")).isEmpty();
        assertThat(voz.textos).isEmpty();
    }

    @Test
    @DisplayName("otra persona no encuentra el audio, aunque tenga el id")
    void otroActorNoLoEncuentra() {
        UserId otro = UserId.of(UUID.randomUUID());
        usuarios.conActor(otro, UserRole.TRAINEE);
        UUID id = service.preparar(actor, "Hola").orElseThrow();

        assertThat(service.buscar(otro, id)).isEmpty();
    }

    @Test
    @DisplayName("pasados dos minutos el audio vence")
    void venceALosDosMinutos() {
        UUID id = service.preparar(actor, "Hola").orElseThrow();

        reloj.avanzar(VozDelOrbeService.VIGENCIA.minusSeconds(1));
        assertThat(service.buscar(actor, id)).isPresent();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(service.buscar(actor, id)).isEmpty();
    }

    @Test
    @DisplayName("un id que no existe es vacio")
    void idDesconocidoEsVacio() {
        assertThat(service.buscar(actor, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("si la voz falla sin producir sonido, el audio existe pero no tiene sonido")
    void falloAntesDeSonar() throws Exception {
        voz.falla = true;
        UUID id = service.preparar(actor, "Hola").orElseThrow();

        assertThat(service.buscar(actor, id).orElseThrow().tieneSonido()).isFalse();
    }

    @Test
    @DisplayName("una excepcion del proveedor no se escapa: el audio queda cerrado y sin sonido")
    void excepcionDelProveedorNoSeEscapa() throws Exception {
        voz.lanza = true;
        UUID id = service.preparar(actor, "Hola").orElseThrow();

        assertThat(service.buscar(actor, id).orElseThrow().tieneSonido()).isFalse();
    }

    @Test
    @DisplayName("con el tope de audios en memoria lleno, es vacio (la app usa su propia voz)")
    void topeEnMemoria() {
        for (int i = 0; i < VozDelOrbeService.MAXIMO_EN_MEMORIA; i++) {
            assertThat(service.preparar(actor, "Hola")).isPresent();
        }

        assertThat(service.preparar(actor, "Hola")).isEmpty();
    }

    @Test
    @DisplayName("vacio o de mas de 400 caracteres (despues de recortar) es IllegalArgumentException")
    void textoInvalido() {
        assertThatThrownBy(() -> service.preparar(actor, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preparar(actor, "a".repeat(VozDelOrbeUseCase.LARGO_MAXIMO_TEXTO + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.preparar(actor, " " + "a".repeat(VozDelOrbeUseCase.LARGO_MAXIMO_TEXTO) + " "))
                .isPresent();
    }

    @Test
    @DisplayName("una cuenta suspendida no prepara ni escucha")
    void suspendidoNoPuede() {
        UUID id = service.preparar(actor, "Hola").orElseThrow();
        usuarios.conActor(actor, UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.preparar(actor, "Hola")).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.buscar(actor, id)).isInstanceOf(NotAuthorizedException.class);
    }

    private static final class VozFalsa implements SintetizarVozPort {

        final List<String> textos = new ArrayList<>();
        boolean disponible = true;
        boolean falla;
        boolean lanza;

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public boolean sintetizar(String texto, Consumer<byte[]> destino) {
            textos.add(texto);
            if (lanza) {
                throw new IllegalStateException("se cayo");
            }
            if (falla) {
                return false;
            }
            destino.accept(SONIDO);
            return true;
        }
    }

    private static final class RelojMovible implements Clock {

        private Instant ahora;

        RelojMovible(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}
