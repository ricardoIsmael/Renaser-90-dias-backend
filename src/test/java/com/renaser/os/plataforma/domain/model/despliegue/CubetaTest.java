package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** La cubeta estable del despliegue (contrato fase 2 §3.2; SC-004). Sin Spring. */
class CubetaTest {

    private static final List<UserId> MIL_PERSONAS = IntStream.range(0, 1000)
            .mapToObj(i -> UserId.of(UUID.nameUUIDFromBytes(("persona-" + i).getBytes()))).toList();

    @Test
    @DisplayName("1000 personas × 10 lecturas: cada una cae siempre en la misma cubeta (0 oscilaciones, SC-004)")
    void estable() {
        for (UserId persona : MIL_PERSONAS) {
            int primera = Cubeta.de("chat.audio", persona);
            for (int lectura = 0; lectura < 10; lectura++) {
                assertThat(Cubeta.de("chat.audio", persona)).isEqualTo(primera);
            }
            assertThat(primera).isBetween(0, Cubeta.CANTIDAD - 1);
        }
    }

    /**
     * Congela el algoritmo del contrato: SHA-256 de «clave:uuid», primeros 4 bytes SIN signo, módulo 100. Valores
     * calculados aparte (Python, hashlib). Si esta prueba falla, cambió la asignación de TODAS las personas: quien
     * estaba dentro de un despliegue quedaría fuera de golpe. Los dos últimos tienen el bit alto prendido: con el entero
     * con signo darían -33 y -83.
     */
    @Test
    @DisplayName("el algoritmo es el del contrato: SHA-256 de «clave:uuid», 4 bytes sin signo, mod 100")
    void algoritmoDelContrato() {
        assertThat(Cubeta.de("chat", UserId.of("00000000-0000-0000-0000-000000000001"))).isEqualTo(21);
        assertThat(Cubeta.de("muro.reacciones", UserId.of("3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b"))).isEqualTo(63);
        assertThat(Cubeta.de("chat", UserId.of("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF")))
                .as("el uuid entra en minúsculas, como lo escribe UUID").isEqualTo(17);
    }

    @Test
    @DisplayName("reparte parejo: al 25 % cae alrededor de una de cada cuatro personas")
    void repartoParejo() {
        long dentro = MIL_PERSONAS.stream().filter(persona -> Cubeta.de("eventos", persona) < 25).count();

        assertThat(dentro).isBetween(200L, 300L);
    }

    @Test
    @DisplayName("cada funcionalidad reparte distinto: el mismo 25 % no prueba todo lo nuevo")
    void cadaFuncionalidadRepartaDistinto() {
        long enLasDos = MIL_PERSONAS.stream()
                .filter(persona -> Cubeta.de("eventos", persona) < 25 && Cubeta.de("radar", persona) < 25).count();

        assertThat(enLasDos).as("independientes: ~6 %, no 25 %").isLessThan(130L);
    }
}
