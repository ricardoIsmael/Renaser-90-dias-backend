package com.renaser.os.calendar.infrastructure.adapter.out.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCifradoTest {

    private static final String CLAVE_1 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String CLAVE_2 = Base64.getEncoder().encodeToString("ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".getBytes());
    private static final String SECRETO = "1//permiso-permanente-de-prueba-con-ñ";

    // spec 040 FR-011, SC-006
    @Test
    @DisplayName("FR-011: el secreto cifrado no contiene el texto en claro y se descifra igual")
    void cifraYDescifra() {
        AesGcmCifrado cifrado = new AesGcmCifrado(CLAVE_1, 1, "", 0);

        String guardado = cifrado.cifrar(SECRETO);

        assertThat(guardado).startsWith("v1:").doesNotContain(SECRETO).doesNotContain("permiso-permanente");
        assertThat(cifrado.descifrar(guardado)).isEqualTo(SECRETO);
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: cada valor lleva su propio nonce: cifrar lo mismo dos veces da textos distintos")
    void unNonceNuevoPorValor() {
        AesGcmCifrado cifrado = new AesGcmCifrado(CLAVE_1, 1, "", 0);

        assertThat(cifrado.cifrar(SECRETO)).isNotEqualTo(cifrado.cifrar(SECRETO));
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: lo cifrado y alterado, o con otra clave, no se descifra (GCM autentica)")
    void alteradoOConOtraClaveNoSeDescifra() {
        AesGcmCifrado cifrado = new AesGcmCifrado(CLAVE_1, 1, "", 0);
        String guardado = cifrado.cifrar(SECRETO);
        int medio = guardado.length() / 2;
        String alterado = guardado.substring(0, medio) + (guardado.charAt(medio) == 'A' ? 'B' : 'A') + guardado.substring(medio + 1);

        assertThatThrownBy(() -> cifrado.descifrar(alterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmCifrado(CLAVE_2, 1, "", 0).descifrar(guardado)).isInstanceOf(IllegalStateException.class);
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: hay identificador de versión de clave: tras rotar, lo cifrado con la clave anterior se sigue descifrando y lo nuevo usa la nueva")
    void rotarLaClave() {
        String conLaVieja = new AesGcmCifrado(CLAVE_1, 1, "", 0).cifrar(SECRETO);

        AesGcmCifrado rotado = new AesGcmCifrado(CLAVE_2, 2, CLAVE_1, 1);

        assertThat(rotado.versionDeLaClave()).isEqualTo(2);
        assertThat(rotado.descifrar(conLaVieja)).isEqualTo(SECRETO);
        assertThat(rotado.cifrar(SECRETO)).startsWith("v2:");
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: sin clave configurada el servidor arranca, no está configurado y no guarda nada; con una clave que no es de 32 bytes, falla con su motivo")
    void sinClaveOConClaveMala() {
        AesGcmCifrado sinClave = new AesGcmCifrado("", 1, "", 0);

        assertThat(sinClave.estaConfigurado()).isFalse();
        assertThatThrownBy(() -> sinClave.cifrar(SECRETO)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Falta la clave de cifrado");
        assertThatThrownBy(() -> new AesGcmCifrado(Base64.getEncoder().encodeToString("corta".getBytes()), 1, "", 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }
}
