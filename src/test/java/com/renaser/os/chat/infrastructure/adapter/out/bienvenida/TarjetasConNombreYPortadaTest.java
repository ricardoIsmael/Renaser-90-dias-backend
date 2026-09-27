package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las tarjetas guardadas en memoria no sobreviven a un cambio de portada (D-210): la portada va en la
 * clave. Aparte de {@code TarjetasConNombreEnMemoriaTest} para no pisar esas pruebas.
 */
class TarjetasConNombreYPortadaTest {

    private static final String NUEVA = "bienvenida/portadas/p1";

    /** Dibuja «bytes» que dicen el nombre y la portada; la vigente se cambia desde la prueba. */
    private static final class DibujanteConPortadas implements DibujarBienvenidaPort {
        final List<String> dibujados = new ArrayList<>();
        String vigente = PORTADA_ORIGINAL;
        /** Si no es null, la vigente cambia MIENTRAS se dibuja (otro pedido que confirma una portada). */
        String cambiaMientrasDibuja;

        @Override
        public byte[] dibujar(String nombre) {
            return dibujar(nombre, vigente);
        }

        @Override
        public String portadaVigente() {
            return vigente;
        }

        @Override
        public byte[] dibujar(String nombre, String portada) {
            if (cambiaMientrasDibuja != null) {
                vigente = cambiaMientrasDibuja;
            }
            dibujados.add(nombre + "@" + portada);
            return (nombre + "@" + portada).getBytes(StandardCharsets.UTF_8);
        }
    }

    private final DibujanteConPortadas dibujante = new DibujanteConPortadas();
    private final TarjetasConNombreEnMemoria tarjetas = new TarjetasConNombreEnMemoria(dibujante);

    @Test
    @DisplayName("con la portada nueva no se sirve la tarjeta vieja: se dibuja otra y cambia la huella (ETag)")
    void conLaPortadaNuevaNoSirveLaVieja() {
        TarjetaConNombre vieja = tarjetas.tarjetaDe("Ana");

        dibujante.vigente = NUEVA;
        TarjetaConNombre nueva = tarjetas.tarjetaDe("Ana");

        assertThat(texto(nueva)).isEqualTo("ANA@" + NUEVA);
        assertThat(nueva.huella()).isNotEqualTo(vieja.huella());
        assertThat(dibujante.dibujados).containsExactly("ANA@original", "ANA@" + NUEVA);
    }

    @Test
    @DisplayName("al volver a la original se sirve la de la original, sin dibujarla de nuevo")
    void alVolverALaOriginal() {
        tarjetas.tarjetaDe("Ana");
        dibujante.vigente = NUEVA;
        tarjetas.tarjetaDe("Ana");

        dibujante.vigente = DibujarBienvenidaPort.PORTADA_ORIGINAL;

        assertThat(texto(tarjetas.tarjetaDe("ana"))).isEqualTo("ANA@original");
        assertThat(dibujante.dibujados).hasSize(2);
    }

    @Test
    @DisplayName("se dibuja sobre la portada de la clave aunque la vigente cambie mientras tanto")
    void loGuardadoCorrespondeASuClave() {
        dibujante.cambiaMientrasDibuja = NUEVA;

        TarjetaConNombre primera = tarjetas.tarjetaDe("Ana");
        dibujante.cambiaMientrasDibuja = null;

        assertThat(texto(primera)).as("se pidió con la original vigente").isEqualTo("ANA@original");
        assertThat(texto(tarjetas.tarjetaDe("Ana"))).isEqualTo("ANA@" + NUEVA);
    }

    private static String texto(TarjetaConNombre tarjeta) {
        return new String(tarjeta.jpeg(), StandardCharsets.UTF_8);
    }
}
