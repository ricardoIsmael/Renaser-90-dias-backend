package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La negociación de los latidos del canal en vivo (D-202, propuesto en E-331) contra el Tomcat real,
 * con un cliente STOMP escrito a mano igual que el de la app ({@code conexionStomp.ts}): tramas en
 * BINARIO, {@code heart-beat:10000,10000} en el CONNECT y un salto de línea como latido.
 *
 * <p>Acá se fija el VALOR de producción: 10 s. El cierre de la conexión muda se prueba en
 * {@link LatidosDelChatCortosIT} con los mismos latidos pero de 2 s, para no gastar 30–40 s de reloj
 * real (E-648).
 *
 * <p>Antes de D-202 el broker contestaba {@code heart-beat:0,0}: esta prueba falla contra ese código.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LatidosDelChatIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;

    private ConexionesStompDePrueba conexiones;

    @BeforeEach
    void abrir() {
        conexiones = new ConexionesStompDePrueba(puerto, sesiones);
    }

    @AfterEach
    void cerrar() {
        conexiones.close();
    }

    @Test
    @DisplayName("el CONNECTED negocia latidos de 10 s en los dos sentidos (antes: heart-beat:0,0)")
    void elConnectedNegociaLosLatidos() throws Exception {
        ConexionesStompDePrueba.Escucha cliente = new ConexionesStompDePrueba.Escucha();

        conexiones.conectar(cliente, "10000,10000");

        Map<String, String> connected = ConexionesStompDePrueba.cabeceras(cliente.esperarTrama("CONNECTED", 10));
        assertThat(connected).containsEntry("heart-beat", "10000,10000").containsEntry("version", "1.2");
    }
}
