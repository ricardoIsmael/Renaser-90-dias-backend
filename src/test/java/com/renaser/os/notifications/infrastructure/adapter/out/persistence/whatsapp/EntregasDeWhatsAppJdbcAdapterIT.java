package com.renaser.os.notifications.infrastructure.adapter.out.persistence.whatsapp;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La tabla {@code entregas_whatsapp} (V168, spec 041) contra Postgres de verdad: lo que un mock no puede verificar. El apartado es un
 * {@code INSERT ... ON CONFLICT (clave) DO NOTHING}: dos instancias a la vez deben apartar UNA sola vez (SC-005), y el estado solo avanza (FR-022). Sin el texto del
 * mensaje y sin el numero (FR-030). No es {@code @Transactional}: el apartado corre en su propia transaccion ({@code REQUIRES_NEW}), como en el servidor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EntregasDeWhatsAppJdbcAdapterIT {

    private static final Instant AHORA = Instant.parse("2026-11-04T20:00:00Z");

    @Autowired
    private EntregasDeWhatsAppJdbcAdapter adaptador;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID evento = UUID.randomUUID();
    private final List<UUID> creadas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        creadas.forEach(id -> jdbc.update("DELETE FROM renaser.entregas_whatsapp WHERE id = ?", id));
    }

    private EntregaDeWhatsApp apartada(UUID clave, UserId persona, int avisoMin, Instant creadaEn) {
        EntregaDeWhatsApp e = EntregaDeWhatsApp.apartar(UUID.randomUUID(), clave, evento, persona, avisoMin, creadaEn);
        creadas.add(e.id());
        return e;
    }

    // spec 041 FR-020, SC-005
    @Test
    @DisplayName("apartar dos veces la misma clave deja UNA fila: la segunda dice false y no se llama al proveedor")
    void apartarEsIdempotente() {
        UUID clave = UUID.randomUUID();
        UserId persona = UserId.of(UUID.randomUUID());

        assertThat(adaptador.apartar(apartada(clave, persona, 10, AHORA))).isTrue();
        assertThat(adaptador.apartar(apartada(clave, persona, 10, AHORA))).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.entregas_whatsapp WHERE clave = ?", Integer.class, clave)).isEqualTo(1);
    }

    // spec 041 FR-020, SC-005
    @Test
    @DisplayName("dos instancias que aparten a la vez la misma clave: gana UNA sola")
    void dosInstanciasALaVez() throws Exception {
        UUID clave = UUID.randomUUID();
        UserId persona = UserId.of(UUID.randomUUID());
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            EntregaDeWhatsApp intento = apartada(clave, persona, 10, AHORA);
            resultados.add(hilos.submit(() -> {
                salida.await();
                return adaptador.apartar(intento);
            }));
        }
        salida.countDown();
        int ganadoras = 0;
        for (Future<Boolean> resultado : resultados) {
            ganadoras += resultado.get() ? 1 : 0;
        }
        hilos.shutdown();

        assertThat(ganadoras).isEqualTo(1);
    }

    // spec 041 FR-022
    @Test
    @DisplayName("el resultado de la llamada se guarda con el id del proveedor y el wamid, y se puede buscar por cualquiera de los tres")
    void guardarElResultado() {
        EntregaDeWhatsApp e = apartada(UUID.randomUUID(), UserId.of(UUID.randomUUID()), 30, AHORA);
        adaptador.apartar(e);

        adaptador.guardarResultado(e.conResultado(ResultadoDelEnvio.aceptado("ycloud-" + e.id(), "wamid." + e.id()), AHORA.plusSeconds(2)));

        assertThat(adaptador.porId(e.id())).get().satisfies(guardada -> {
            assertThat(guardada.estado()).isEqualTo(EstadoDeEntrega.ACEPTADO);
            assertThat(guardada.wamid()).isEqualTo("wamid." + e.id());
            assertThat(guardada.usuarioId()).isEqualTo(e.usuarioId());
            assertThat(guardada.avisoMin()).isEqualTo(30);
        });
        assertThat(adaptador.porWamid("wamid." + e.id())).get().extracting(EntregaDeWhatsApp::id).isEqualTo(e.id());
        assertThat(adaptador.porProveedorId("ycloud-" + e.id())).get().extracting(EntregaDeWhatsApp::id).isEqualTo(e.id());
        assertThat(adaptador.porWamid("wamid.que-no-existe")).isEmpty();
    }

    // spec 041 FR-022
    @Test
    @DisplayName("el estado solo avanza: leido no vuelve a enviado, y un fallido tardio no deshace lo entregado")
    void elEstadoSoloAvanza() {
        EntregaDeWhatsApp e = apartada(UUID.randomUUID(), UserId.of(UUID.randomUUID()), 60, AHORA);
        adaptador.apartar(e);
        adaptador.guardarResultado(e.conResultado(ResultadoDelEnvio.aceptado("y-" + e.id(), null), AHORA));

        assertThat(adaptador.avanzarEstado(e.id(), EstadoDeEntrega.ENTREGADO, null, "wamid-" + e.id(), null, AHORA)).isTrue();
        assertThat(adaptador.avanzarEstado(e.id(), EstadoDeEntrega.LEIDO, null, null, null, AHORA)).isTrue();
        assertThat(adaptador.avanzarEstado(e.id(), EstadoDeEntrega.ENVIADO, null, null, null, AHORA)).isFalse();
        assertThat(adaptador.avanzarEstado(e.id(), EstadoDeEntrega.FALLIDO, "tarde", null, null, AHORA)).isFalse();

        assertThat(adaptador.porId(e.id())).get().satisfies(guardada -> {
            assertThat(guardada.estado()).isEqualTo(EstadoDeEntrega.LEIDO);
            assertThat(guardada.wamid()).as("el wamid del aviso se completo").isEqualTo("wamid-" + e.id());
        });
    }

    // spec 041 FR-022
    @Test
    @DisplayName("un resultado que llega despues de que el proveedor ya aviso («entregado») no lo retrocede, pero completa el wamid")
    void elResultadoNoRetrocede() {
        EntregaDeWhatsApp e = apartada(UUID.randomUUID(), UserId.of(UUID.randomUUID()), 10, AHORA);
        adaptador.apartar(e);
        adaptador.avanzarEstado(e.id(), EstadoDeEntrega.ENTREGADO, null, null, null, AHORA);

        adaptador.guardarResultado(e.conResultado(ResultadoDelEnvio.aceptado("y-" + e.id(), "w-" + e.id()), AHORA));

        assertThat(adaptador.porId(e.id())).get().satisfies(guardada -> {
            assertThat(guardada.estado()).isEqualTo(EstadoDeEntrega.ENTREGADO);
            assertThat(guardada.wamid()).isEqualTo("w-" + e.id());
        });
    }

    // spec 041 FR-029
    @Test
    @DisplayName("el conteo de un evento por estado y las personas sin numero (personas, no mensajes)")
    void elConteoDelEvento() {
        UserId a = UserId.of(UUID.randomUUID());
        UserId b = UserId.of(UUID.randomUUID());
        adaptador.apartar(apartada(UUID.randomUUID(), a, 10, AHORA));
        EntregaDeWhatsApp entregada = apartada(UUID.randomUUID(), a, 30, AHORA);
        adaptador.apartar(entregada);
        adaptador.avanzarEstado(entregada.id(), EstadoDeEntrega.ENTREGADO, null, null, null, AHORA);
        adaptador.apartar(sinNumero(b, 10));
        adaptador.apartar(sinNumero(b, 30));

        var conteo = adaptador.conteoDelEvento(evento);

        assertThat(conteo.porEstado()).containsEntry(EstadoDeEntrega.ENVIANDO, 1).containsEntry(EstadoDeEntrega.ENTREGADO, 1)
                .containsEntry(EstadoDeEntrega.OMITIDO, 2);
        assertThat(conteo.omitidasSinNumero()).isEqualTo(2);
        assertThat(conteo.personasSinNumero()).isEqualTo(1);
        assertThat(adaptador.conteoDelEvento(UUID.randomUUID()).porEstado()).isEmpty();
    }

    private EntregaDeWhatsApp sinNumero(UserId persona, int avisoMin) {
        EntregaDeWhatsApp e = EntregaDeWhatsApp.omitida(UUID.randomUUID(), UUID.randomUUID(), evento, persona, avisoMin, "Sin WhatsApp", AHORA);
        creadas.add(e.id());
        return e;
    }

    // spec 041 FR-025
    @Test
    @DisplayName("el tope cuenta lo que salio en el mes, sin los omitidos ni los fallidos")
    void contarLoQueSalio() {
        Instant inicioDeMes = Instant.parse("2031-03-01T05:00:00Z");
        UserId persona = UserId.of(UUID.randomUUID());
        adaptador.apartar(apartada(UUID.randomUUID(), persona, 10, inicioDeMes.plusSeconds(60)));
        EntregaDeWhatsApp fallida = apartada(UUID.randomUUID(), persona, 30, inicioDeMes.plusSeconds(120));
        adaptador.apartar(fallida);
        adaptador.avanzarEstado(fallida.id(), EstadoDeEntrega.FALLIDO, "x", null, null, AHORA);
        EntregaDeWhatsApp omitida = EntregaDeWhatsApp.omitida(UUID.randomUUID(), UUID.randomUUID(), evento, persona, 60, "Llegó tarde", inicioDeMes.plusSeconds(180));
        creadas.add(omitida.id());
        adaptador.apartar(omitida);
        adaptador.apartar(apartada(UUID.randomUUID(), persona, 1440, inicioDeMes.minusSeconds(60)));

        assertThat(adaptador.contarQueSalieron(inicioDeMes, inicioDeMes.plusSeconds(86_400L * 31))).isEqualTo(1);
    }

    // spec 041 FR-030
    @Test
    @DisplayName("la retencion de 12 meses borra lo viejo y deja lo reciente")
    void laPurga() {
        UserId persona = UserId.of(UUID.randomUUID());
        EntregaDeWhatsApp vieja = apartada(UUID.randomUUID(), persona, 10, Instant.parse("2030-01-01T00:00:00Z"));
        EntregaDeWhatsApp nueva = apartada(UUID.randomUUID(), persona, 30, Instant.parse("2030-12-01T00:00:00Z"));
        adaptador.apartar(vieja);
        adaptador.apartar(nueva);

        adaptador.purgarAnterioresA(Instant.parse("2030-06-01T00:00:00Z"));

        assertThat(adaptador.porId(vieja.id())).isEmpty();
        assertThat(adaptador.porId(nueva.id())).isPresent();
    }

    // spec 041 FR-030, SC-014
    @Test
    @DisplayName("la tabla no tiene ninguna columna para el texto del mensaje, el numero ni las respuestas, ni llaves foraneas a otros modulos")
    void laTablaGuardaSoloLoMinimo() {
        List<String> columnas = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'renaser' "
                + "AND table_name = 'entregas_whatsapp'", String.class);
        Integer llavesForaneas = jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema = 'renaser' "
                + "AND table_name = 'entregas_whatsapp' AND constraint_type = 'FOREIGN KEY'", Integer.class);

        assertThat(columnas).containsExactlyInAnyOrder("id", "clave", "evento_id", "usuario_id", "aviso_min", "estado", "motivo", "wamid", "proveedor_id",
                "creado_en", "actualizado_en");
        assertThat(columnas).doesNotContain("texto", "mensaje", "cuerpo", "numero", "telefono", "whatsapp", "respuesta");
        assertThat(llavesForaneas).isZero();
    }

    // spec 041 FR-030, FR-040
    @Test
    @DisplayName("la tabla de eventos no suma ninguna columna: a quien llega el mensaje no se elige por evento")
    void laTablaDeEventosNoCambia() {
        List<String> columnas = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'renaser' "
                + "AND table_name = 'eventos'", String.class);

        assertThat(columnas).isNotEmpty().noneMatch(c -> c.contains("whatsapp") || c.contains("mensaje"));
    }

    // spec 041 FR-030
    @Test
    @DisplayName("no se creo ninguna tabla de contactos, de numeros ni de permisos de WhatsApp: la unica es la de entregas")
    void sinTablasDeContactosNiDeNumeros() {
        List<String> tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'renaser'", String.class);

        assertThat(tablas).contains("entregas_whatsapp").noneMatch(t -> t.contains("contacto") || t.contains("numero")
                || (t.contains("whatsapp") && !t.equals("entregas_whatsapp")));
    }
}
