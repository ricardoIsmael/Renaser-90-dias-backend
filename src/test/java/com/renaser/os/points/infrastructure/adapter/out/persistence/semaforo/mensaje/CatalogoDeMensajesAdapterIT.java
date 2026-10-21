package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El catálogo sembrado por V110 (D-277), leído por el adaptador. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogoDeMensajesAdapterIT {

    @Autowired
    private CatalogoDeMensajesPort catalogo;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("V110 siembra 35 VERDE, 35 AMARILLO, 30 ROJO y 3 por cada caso sin color, ordenados por id")
    void elCatalogoSembrado() {
        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).hasSize(35);
        assertThat(catalogo.activosDe(GrupoDelMensaje.AMARILLO)).hasSize(35);
        assertThat(catalogo.activosDe(GrupoDelMensaje.ROJO)).hasSize(30);
        for (GrupoDelMensaje caso : List.of(GrupoDelMensaje.INICIO, GrupoDelMensaje.SIN_PROGRAMA,
                GrupoDelMensaje.PAUSA, GrupoDelMensaje.CUENTA_PAUSADA, GrupoDelMensaje.GRADUADO)) {
            assertThat(catalogo.activosDe(caso)).as("%s", caso).hasSize(3);
        }
        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).extracting(MensajeDelSemaforo::id).isSorted();
    }

    /** El catálogo vive en memoria: lo que esta clase cambia (y se deshace al terminar) no debe quedar en la copia. */
    @AfterEach
    void descartarLaCopia() {
        catalogo.recargar();
    }

    @Test
    @DisplayName("un mensaje desactivado no se lista pero se sigue encontrando por id")
    void unMensajeDesactivadoSigueExistiendo() {
        jdbcClient.sql("UPDATE renaser.mensajes_semaforo SET activo = false WHERE id = 'v001'").update();
        catalogo.recargar();

        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).extracting(MensajeDelSemaforo::id)
                .doesNotContain("v001").hasSize(34);
        assertThat(catalogo.porId("v001")).get().extracting(MensajeDelSemaforo::activo).isEqualTo(false);
    }

    @Test
    @DisplayName("una fila con una variable que no existe se salta sin tumbar las demás")
    void unTextoMalEscritoSeSalta() {
        jdbcClient.sql("UPDATE renaser.mensajes_semaforo SET texto = '{nobre}, hola.' WHERE id = 'v002'").update();
        catalogo.recargar();

        assertThat(catalogo.activosDe(GrupoDelMensaje.VERDE)).extracting(MensajeDelSemaforo::id)
                .doesNotContain("v002").hasSize(34);
    }

    @Test
    @DisplayName("lee el texto con sus variables y el sticker tal como están en la base")
    void leeUnMensaje() {
        assertThat(catalogo.porId("v001")).get().satisfies(mensaje -> {
            assertThat(mensaje.grupo()).isEqualTo(GrupoDelMensaje.VERDE);
            assertThat(mensaje.texto()).startsWith("{nombre}, tu constancia");
            assertThat(mensaje.sticker()).isEqualTo("dorado-orgulloso");
            assertThat(mensaje.activo()).isTrue();
        });
        assertThat(catalogo.porId("no-existe")).isEmpty();
    }
}
