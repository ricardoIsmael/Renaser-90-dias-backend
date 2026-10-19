package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.github.benmanes.caffeine.cache.Ticker;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El catálogo en memoria (D-277, SC-004): se lee una vez, vence a los 10 minutos y se puede descartar a mano. */
class CatalogoDeMensajesEnMemoriaTest {

    private final List<MensajeDelSemaforo> filas = new ArrayList<>();
    private final AtomicInteger lecturas = new AtomicInteger();
    private final RelojDeMaquina reloj = new RelojDeMaquina();
    private boolean fallar;
    private CatalogoDeMensajesEnMemoria catalogo;

    @BeforeEach
    void armar() {
        filas.add(mensaje("v002", GrupoDelMensaje.VERDE, true));
        filas.add(mensaje("v001", GrupoDelMensaje.VERDE, true));
        filas.add(mensaje("v003", GrupoDelMensaje.VERDE, false));
        filas.add(mensaje("a001", GrupoDelMensaje.AMARILLO, true));
        catalogo = new CatalogoDeMensajesEnMemoria(() -> {
            lecturas.incrementAndGet();
            if (fallar) {
                throw new IllegalStateException("base caida");
            }
            return List.copyOf(filas);
        }, reloj);
    }

    private static MensajeDelSemaforo mensaje(String id, GrupoDelMensaje grupo, boolean activo) {
        return new MensajeDelSemaforo(id, grupo, "Texto de " + id, "dorado-sereno", activo);
    }

    @Test
    @DisplayName("varias consultas de grupos y de ids distintos leen la base una sola vez")
    void seLeeUnaVez() {
        catalogo.activosDe(GrupoDelMensaje.VERDE);
        catalogo.activosDe(GrupoDelMensaje.AMARILLO);
        catalogo.porId("v001");
        catalogo.porId("a001");
        catalogo.porId("no-existe");

        assertThat(lecturas).hasValue(1);
    }

    @Test
    @DisplayName("activosDe lista solo los activos de ese grupo, ordenados por id; un grupo sin filas da lista vacía")
    void activosOrdenados() {
        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).extracting(MensajeDelSemaforo::id)
                .containsExactly("v001", "v002");
        assertThat(catalogo.activosDe(GrupoDelMensaje.ROJO)).isEmpty();
    }

    @Test
    @DisplayName("porId encuentra también un mensaje desactivado: el elegido hoy se sigue mostrando")
    void porIdEncuentraLosInactivos() {
        assertThat(catalogo.porId("v003")).get().extracting(MensajeDelSemaforo::activo).isEqualTo(false);
        assertThat(catalogo.porId("no-existe")).isEmpty();
    }

    @Test
    @DisplayName("a los 10 minutos vuelve a leer y ve lo que cambió; un minuto antes todavía sirve la copia")
    void vencePasadosDiezMinutos() {
        catalogo.activosDe(GrupoDelMensaje.VERDE);
        filas.add(mensaje("v004", GrupoDelMensaje.VERDE, true));

        reloj.pasan(9, TimeUnit.MINUTES);
        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).as("9 minutos: la copia de siempre").hasSize(2);

        reloj.pasan(61, TimeUnit.SECONDS);
        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).as("10 minutos: la copia nueva").hasSize(3);
        assertThat(lecturas).hasValue(2);
    }

    @Test
    @DisplayName("recargar descarta la copia: la próxima consulta lee otra vez, sin esperar a que venza")
    void recargarDescarta() {
        catalogo.activosDe(GrupoDelMensaje.VERDE);
        filas.set(0, mensaje("v002", GrupoDelMensaje.VERDE, false));

        catalogo.recargar();

        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).extracting(MensajeDelSemaforo::id).containsExactly("v001");
        assertThat(lecturas).hasValue(2);
    }

    @Test
    @DisplayName("si la lectura falla, el error llega a quien preguntó, no se guarda nada y la siguiente consulta reintenta")
    void unaLecturaQueFallaNoEnvenenaLaCopia() {
        fallar = true;
        assertThatThrownBy(() -> catalogo.activosDe(GrupoDelMensaje.VERDE)).isInstanceOf(IllegalStateException.class);

        fallar = false;

        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).hasSize(2);
        assertThat(lecturas).hasValue(2);
    }

    /** Un reloj de máquina que solo avanza cuando la prueba lo dice. */
    private static final class RelojDeMaquina implements Ticker {

        private long nanos;

        void pasan(long cantidad, TimeUnit unidad) {
            nanos += unidad.toNanos(cantidad);
        }

        @Override
        public long read() {
            return nanos;
        }
    }
}
