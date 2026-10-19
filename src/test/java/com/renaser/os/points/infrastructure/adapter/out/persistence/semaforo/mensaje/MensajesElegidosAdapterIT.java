package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajesVistos;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code mensajes_semaforo_elegidos} contra Postgres real (D-277, y el canal de D-281). Sin {@code @Transactional}: el cruce de dos
 * lecturas necesita dos conexiones y dos commits de verdad, así que la prueba limpia lo que siembra.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MensajesElegidosAdapterIT {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);
    private static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");

    @Autowired
    private MensajesElegidosPort elegidos;
    @Autowired
    private JdbcClient jdbcClient;

    private UserId persona;

    @BeforeEach
    void sembrarPersona() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture Elegidos', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (:id, 5)")
                .param("id", id).update();
        persona = UserId.of(id);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa y, con él, lo elegido.
        jdbcClient.sql("DELETE FROM renaser.usuarios WHERE id = :id").param("id", persona.value()).update();
    }

    @Test
    @DisplayName("registrar guarda la elección y la relee; una segunda con otro mensaje no la pisa")
    void registraUnaVezPorPersonaFechaYGrupo() {
        String primera = elegidos.registrar(clave(GrupoDelMensaje.VERDE), "v001", AHORA);
        String segunda = elegidos.registrar(clave(GrupoDelMensaje.VERDE), "v002", AHORA.plusSeconds(60));

        assertThat(primera).isEqualTo("v001");
        assertThat(segunda).as("queda la primera: la fila es append-only").isEqualTo("v001");
        assertThat(deHoy(GrupoDelMensaje.VERDE)).contains("v001");
        assertThat(filasDe(persona)).isEqualTo(1);
    }

    @Test
    @DisplayName("el mismo día con otro grupo es otra fila, y cada grupo se relee por separado")
    void otroGrupoEsOtraFila() {
        elegidos.registrar(clave(GrupoDelMensaje.VERDE), "v001", AHORA);
        elegidos.registrar(clave(GrupoDelMensaje.AMARILLO), "a001", AHORA);

        assertThat(deHoy(GrupoDelMensaje.AMARILLO)).contains("a001");
        assertThat(deHoy(GrupoDelMensaje.ROJO)).isEmpty();
        assertThat(elegidos.deLaPersona(clave(HOY.plusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY)).deHoy())
                .isEmpty();
        assertThat(filasDe(persona)).isEqualTo(2);
    }

    @Test
    @DisplayName("dos lecturas que se cruzan dejan una sola fila y las dos ven el mismo mensaje")
    void dosRegistrarConcurrentesDejanUnaFila() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = hilos.submit(registrarCuandoSalga(salida, "v001"));
            Future<String> b = hilos.submit(registrarCuandoSalga(salida, "v002"));
            salida.countDown();

            assertThat(a.get()).isEqualTo(b.get());
            assertThat(filasDe(persona)).isEqualTo(1);
            assertThat(deHoy(GrupoDelMensaje.VERDE)).contains(a.get());
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("sin elección de hoy devuelve lo visto en los 14 días anteriores, de cualquier grupo, y nada más viejo")
    void sinEleccionDeHoyTraeElHistorial() {
        for (int atras = 20; atras >= 1; atras--) {
            elegidos.registrar(clave(HOY.minusDays(atras), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY),
                    "v0" + (10 + atras), AHORA);
        }
        elegidos.registrar(clave(HOY.minusDays(1), GrupoDelMensaje.AMARILLO, CanalDelMensaje.HOY), "a001", AHORA);

        EleccionesDelDia elecciones = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE));

        assertThat(elecciones.deHoy()).isEmpty();
        assertThat(elecciones.vistos().enLaVentana()).hasSize(15).contains("a001", "v011", "v024")
                .doesNotContain("v025", "v030");
        assertThat(elecciones.vistos().deAyer()).containsExactlyInAnyOrder("v011", "a001");
    }

    @Test
    @DisplayName("con elección de hoy para el grupo devuelve solo esa y NO trae el historial (una consulta, sin lo que no hace falta)")
    void conEleccionDeHoyNoTraeElHistorial() {
        elegidos.registrar(clave(HOY.minusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);
        elegidos.registrar(clave(HOY.minusDays(2), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v002", AHORA);
        elegidos.registrar(clave(GrupoDelMensaje.VERDE), "v003", AHORA);

        EleccionesDelDia elecciones = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE));

        assertThat(elecciones.deHoy()).contains("v003");
        assertThat(elecciones.vistos()).isEqualTo(MensajesVistos.ninguno());
    }

    @Test
    @DisplayName("la elección de hoy de OTRO grupo no cuenta: se pide el historial y lo de hoy no entra en él")
    void laEleccionDeHoyDeOtroGrupoNoCuenta() {
        elegidos.registrar(clave(HOY.minusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);
        elegidos.registrar(clave(GrupoDelMensaje.AMARILLO), "a001", AHORA);

        EleccionesDelDia elecciones = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE));

        assertThat(elecciones.deHoy()).isEmpty();
        assertThat(elecciones.vistos().enLaVentana()).containsExactly("v001");
        assertThat(elecciones.vistos().hoyEnElOtroCanal()).as("es el mismo canal: no es «el otro»").isEmpty();
    }

    @Test
    @DisplayName("la fecha que se pide es la que manda: lo elegido ayer no es la elección de hoy")
    void laFechaEsLaQueSePide() {
        elegidos.registrar(clave(HOY.minusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);

        assertThat(elegidos.deLaPersona(clave(HOY.minusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY)).deHoy())
                .contains("v001");
        assertThat(elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE)).deHoy()).isEmpty();
    }

    // ── el canal (D-281) ────────────────────────────────────────────────────

    @Test
    @DisplayName("Hoy y el chat guardan filas distintas el mismo día y grupo, cada una con su canal")
    void cadaCanalTieneSuFila() {
        String enHoy = elegidos.registrar(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);
        String enElChat = elegidos.registrar(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT), "v002", AHORA);

        assertThat(enHoy).isEqualTo("v001");
        assertThat(enElChat).as("el chat no es pisado por la fila de Hoy: tiene la suya").isEqualTo("v002");
        assertThat(filasDe(persona)).isEqualTo(2);
        assertThat(canalesDe(persona)).containsExactlyInAnyOrder("HOY", "CHAT");
        assertThat(elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.HOY)).deHoy()).contains("v001");
        assertThat(elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT)).deHoy()).contains("v002");
    }

    @Test
    @DisplayName("lo que el OTRO canal mostró hoy llega aparte, de cualquier grupo, y sin elección propia")
    void loDeHoyDelOtroCanalLlegaAparte() {
        elegidos.registrar(clave(GrupoDelMensaje.AMARILLO, CanalDelMensaje.HOY), "a001", AHORA);

        EleccionesDelDia delChat = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT));

        assertThat(delChat.deHoy()).isEmpty();
        assertThat(delChat.vistos().hoyEnElOtroCanal()).containsExactly("a001");
        assertThat(delChat.vistos().enLaVentana()).as("lo de hoy no entra en la ventana").isEmpty();
    }

    @Test
    @DisplayName("visto desde Hoy, lo que el chat mostró hoy también llega aparte: el de Hoy no repite el del chat")
    void loDelChatDeHoyLlegaAparteParaHoy() {
        elegidos.registrar(clave(GrupoDelMensaje.ROJO, CanalDelMensaje.CHAT), "r003", AHORA);

        EleccionesDelDia deHoy = elegidos.deLaPersona(clave(GrupoDelMensaje.ROJO, CanalDelMensaje.HOY));

        assertThat(deHoy.deHoy()).isEmpty();
        assertThat(deHoy.vistos().hoyEnElOtroCanal()).containsExactly("r003");
    }

    @Test
    @DisplayName("con la elección propia hecha no se trae lo del otro canal: igual que el historial, no hace falta")
    void conEleccionPropiaNoTraeLoDelOtroCanal() {
        elegidos.registrar(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);
        elegidos.registrar(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT), "v002", AHORA);

        EleccionesDelDia delChat = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT));

        assertThat(delChat.deHoy()).contains("v002");
        assertThat(delChat.vistos()).isEqualTo(MensajesVistos.ninguno());
    }

    @Test
    @DisplayName("la ventana de 14 días suma los dos canales")
    void laVentanaSumaLosDosCanales() {
        elegidos.registrar(clave(HOY.minusDays(5), GrupoDelMensaje.VERDE, CanalDelMensaje.HOY), "v001", AHORA);
        elegidos.registrar(clave(HOY.minusDays(1), GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT), "v002", AHORA);

        EleccionesDelDia deHoy = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.HOY));
        EleccionesDelDia delChat = elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT));

        assertThat(deHoy.vistos().enLaVentana()).containsExactlyInAnyOrder("v001", "v002");
        assertThat(delChat.vistos().enLaVentana()).containsExactlyInAnyOrder("v001", "v002");
        assertThat(delChat.vistos().deAyer()).containsExactly("v002");
    }

    @Test
    @DisplayName("dos registros del chat que se cruzan dejan una sola fila de canal CHAT")
    void dosRegistrosDelChatDejanUnaFila() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = hilos.submit(registrarEnElChatCuandoSalga(salida, "v001"));
            Future<String> b = hilos.submit(registrarEnElChatCuandoSalga(salida, "v002"));
            salida.countDown();

            assertThat(a.get()).isEqualTo(b.get());
            assertThat(filasDe(persona)).isEqualTo(1);
            assertThat(canalesDe(persona)).containsExactly("CHAT");
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("la base rechaza un canal que no existe")
    void laBaseRechazaUnCanalDesconocido() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO renaser.mensajes_semaforo_elegidos (participante_id, fecha, canal, grupo, mensaje_id, elegido_en)
                VALUES (:id, :fecha, 'RADIO', 'VERDE', 'v001', now())
                """).param("id", persona.value()).param("fecha", HOY).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("las filas de antes de la columna canal (sin valor) quedan como canal HOY")
    void sinCanalEsHoy() {
        jdbcClient.sql("""
                INSERT INTO renaser.mensajes_semaforo_elegidos (participante_id, fecha, grupo, mensaje_id, elegido_en)
                VALUES (:id, :fecha, 'VERDE', 'v001', now())
                """).param("id", persona.value()).param("fecha", HOY).update();

        assertThat(canalesDe(persona)).containsExactly("HOY");
        assertThat(elegidos.deLaPersona(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.HOY)).deHoy()).contains("v001");
    }

    private ClaveDeLaEleccion clave(GrupoDelMensaje grupo) {
        return clave(HOY, grupo, CanalDelMensaje.HOY);
    }

    private ClaveDeLaEleccion clave(GrupoDelMensaje grupo, CanalDelMensaje canal) {
        return clave(HOY, grupo, canal);
    }

    private ClaveDeLaEleccion clave(LocalDate fecha, GrupoDelMensaje grupo, CanalDelMensaje canal) {
        return new ClaveDeLaEleccion(persona, fecha, grupo, canal);
    }

    private Optional<String> deHoy(GrupoDelMensaje grupo) {
        return elegidos.deLaPersona(clave(grupo)).deHoy();
    }

    private Callable<String> registrarCuandoSalga(CountDownLatch salida, String mensajeId) {
        return () -> {
            salida.await();
            return elegidos.registrar(clave(GrupoDelMensaje.VERDE), mensajeId, AHORA);
        };
    }

    private Callable<String> registrarEnElChatCuandoSalga(CountDownLatch salida, String mensajeId) {
        return () -> {
            salida.await();
            return elegidos.registrar(clave(GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT), mensajeId, AHORA);
        };
    }

    private List<String> canalesDe(UserId quien) {
        return jdbcClient.sql("SELECT canal FROM renaser.mensajes_semaforo_elegidos WHERE participante_id = :id")
                .param("id", quien.value()).query(String.class).list();
    }

    private int filasDe(UserId quien) {
        return jdbcClient.sql("SELECT count(*) FROM renaser.mensajes_semaforo_elegidos WHERE participante_id = :id")
                .param("id", quien.value()).query(Integer.class).single();
    }
}
