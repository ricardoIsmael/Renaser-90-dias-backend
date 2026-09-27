package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Las tarjetas con nombre en memoria (D-205): una por nombre, acotadas por peso, huella del contenido. */
class TarjetasConNombreEnMemoriaTest {

    /** Dibuja «bytes» que dicen el nombre recibido, del tamaño pedido, y anota cada dibujo. */
    private static final class DibujanteDePrueba implements DibujarBienvenidaPort {
        final List<String> dibujados = new ArrayList<>();
        private final int tamano;

        DibujanteDePrueba(int tamano) {
            this.tamano = tamano;
        }

        @Override
        public byte[] dibujar(String nombre) {
            dibujados.add(nombre);
            byte[] contenido = new byte[tamano];
            byte[] marca = nombre.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(marca, 0, contenido, 0, Math.min(marca.length, tamano));
            return contenido;
        }
    }

    @Test
    @DisplayName("el mismo nombre se dibuja una sola vez, sin importar mayúsculas ni espacios")
    void unaVezPorNombre() {
        DibujanteDePrueba dibujante = new DibujanteDePrueba(64);
        TarjetasConNombreEnMemoria tarjetas = new TarjetasConNombreEnMemoria(dibujante);

        TarjetaConNombre primera = tarjetas.tarjetaDe("Ana");
        TarjetaConNombre segunda = tarjetas.tarjetaDe(" ANA ");

        assertThat(dibujante.dibujados).containsExactly("ANA");
        assertThat(segunda).isSameAs(primera);
    }

    @Test
    @DisplayName("la huella es del contenido: estable para la misma imagen, distinta para otra")
    void huellaDelContenido() {
        TarjetasConNombreEnMemoria tarjetas = new TarjetasConNombreEnMemoria(new DibujanteDePrueba(64));

        TarjetaConNombre ana = tarjetas.tarjetaDe("Ana");
        TarjetaConNombre luis = tarjetas.tarjetaDe("Luis");

        assertThat(ana.huella()).hasSize(32).isEqualTo(TarjetasConNombreEnMemoria.huella(ana.jpeg()));
        assertThat(luis.huella()).isNotEqualTo(ana.huella());
    }

    @Test
    @DisplayName("acotada por peso: con tarjetas de 1 MB no se guardan más de 6")
    void acotadaPorPeso() {
        TarjetasConNombreEnMemoria tarjetas = new TarjetasConNombreEnMemoria(new DibujanteDePrueba(1024 * 1024));

        for (int i = 0; i < 20; i++) {
            tarjetas.tarjetaDe("Nombre" + i);
        }

        assertThat(tarjetas.guardadas()).isLessThanOrEqualTo(6);
    }
}
