package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La descarga de la Caja con un padrón grande, contra el Tomcat, el Postgres y el Redis reales (spec 050, tramo F; SC-008):
 * 20.000 aprendices con el programa activado. Comprueba que el CSV llega <b>completo</b> y <b>en el orden de siempre</b> (el de
 * la lista del Admin), que sale en streaming (sin {@code Content-Length}: se escribe por lotes) y que la memoria <b>viva</b>
 * mientras se descarga no crece en proporción a las filas: el padrón entero (las cajas con sus fichas) ya no se junta.
 *
 * <p>Mide y escribe en la salida estándar el tiempo y la memoria de la descarga y de una página de la lista (es la
 * medición del costo que pide FR-033; va al cuerpo del PR y a D-348). Falla sin el cambio: la respuesta de antes traía
 * {@code Content-Length} y subía la memoria viva decenas de megabytes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExportacionDeCajaIT {

    private static final String RUTA = "/api/v1/admin/caja";
    private static final String CORREO = "@caja-descarga-it.test";
    private static final String PREFIJO = "Caja IT ";
    private static final int APLICAN = 20_000;
    private static final int SIN_EL_DIA_8 = 25;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /**
     * Cuánta memoria viva más que antes de descargar se tolera. Medido con 20.000 aprendices y la ficha de envío llena: el
     * código de antes (todo el padrón en memoria) llegaba a +58 MB y el de ahora a +5 MB; 30 MB separa a los dos con margen
     * para el ruido del recolector.
     */
    private static final long TOPE_DE_MEMORIA_MB = 30;

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private PedidosDePrueba pedidos;
    private String sesionAdmin;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        sesionAdmin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        LocalDate inicioDelDia10 = Instant.now().atZone(LIMA).toLocalDate().minusDays(9);
        LocalDate inicioDelDia4 = Instant.now().atZone(LIMA).toLocalDate().minusDays(3);
        // Los que aplican (Día 10) y los que no (Día 4: «todavía no aplica»), con el nombre que ordena por número.
        sembrar(APLICAN, 0, inicioDelDia10);
        sembrar(SIN_EL_DIA_8, APLICAN, inicioDelDia4);
        conLaFichaDeEnvioLlena();
        // Una cuenta de nombre «peligroso»: en una planilla, una fórmula se ejecuta (el CSV la escapa con un apóstrofo).
        UUID formula = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, "
                + "CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))", formula,
                formula + CORREO, "=" + PREFIJO + "formula");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, "
                + "timezone) VALUES (?, 10, ?, ?, 'America/Lima')", formula, inicioDelDia10,
                Timestamp.from(Instant.now().minus(Duration.ofDays(10))));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE email LIKE ?", "%" + CORREO);
        pedidos.borrarCuentas();
    }

    /**
     * La Ficha Inicial y el destino de envío de TODOS los sembrados (las doce respuestas de los datos de envío), como las
     * tiene una persona real: sin ellas cada caja es casi vacía y juntar 20.000 no pesaría lo que pesa en producción.
     */
    private void conLaFichaDeEnvioLlena() {
        jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                SELECT u.id, p.id,
                       CASE p.clave_pregunta WHEN 'country' THEN 'Perú'
                            ELSE p.clave_pregunta || ' de ' || u.nombre_completo || ' · calle de los pinos 456, puerta verde, segundo piso' END
                FROM renaser.usuarios u CROSS JOIN renaser.preguntas_onboarding p
                WHERE u.email LIKE ? AND p.clave_pregunta IN ('full_name', 'whatsapp', 'country', 'city', 'district',
                      'address_reference', 'identity_document', 'caja_otra_direccion', 'caja_otro_celular',
                      'caja_quien_recibe', 'caja_referencias', 'caja_provincia')
                """, "%" + CORREO);
    }

    /** {@code cuantos} aprendices con el programa activado desde {@code inicio}, de nombre «Caja IT 000123». */
    private void sembrar(int cuantos, int desde, LocalDate inicio) {
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                SELECT gen_random_uuid(), 'p' || n || ?, ? || lpad(n::text, 6, '0'),
                       CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario)
                FROM generate_series(?, ?) AS n
                """, CORREO, PREFIJO, desde, desde + cuantos - 1);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                SELECT id, 10, ?, ?, 'America/Lima' FROM renaser.usuarios
                WHERE email LIKE ? AND nombre_completo >= ? AND nombre_completo < ?
                """, java.sql.Date.valueOf(inicio), Timestamp.from(Instant.now().minus(Duration.ofDays(10))),
                "%" + CORREO, PREFIJO + String.format("%06d", desde), PREFIJO + String.format("%06d", desde + cuantos));
    }

    private HttpRequest.Builder pedir(String ruta, String sesion) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("X-Auth-Token", sesion)
                .header("User-Agent", "ExportacionDeCajaIT/1.0");
    }

    private static long memoriaViva() {
        System.gc();
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    /**
     * Muestrea la memoria VIVA (después de un GC) cada 2 s mientras corre {@code descarga}; devuelve el pico. No más seguido:
     * en la suite completa el montón ya trae casi 2 GB de contextos cacheados y un GC completo cada 150 ms frenaba la descarga
     * hasta 100 s (cerca de los 120 s del tope asíncrono); lo que se busca, lo que se junta mientras dura la descarga,
     * dura mucho más que 2 s.
     */
    private long picoDeMemoriaViva(Runnable descarga) throws InterruptedException {
        AtomicLong pico = new AtomicLong(memoriaViva());
        AtomicBoolean terminada = new AtomicBoolean(false);
        Thread muestreo = new Thread(() -> {
            while (!terminada.get()) {
                pico.accumulateAndGet(memoriaViva(), Math::max);
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "muestreo-de-memoria");
        muestreo.setDaemon(true);
        muestreo.start();
        try {
            descarga.run();
        } finally {
            terminada.set(true);
            muestreo.join(5_000);
        }
        return pico.get();
    }

    private record Descarga(int filasDeAprendices, long milisegundos, boolean sinContentLength, boolean empiezaConBom) {
    }

    /** Descarga el CSV leyendo línea a línea (sin juntarlo), comprobando el orden y que no haya filas repetidas. */
    private Descarga descargar(List<String> idsEnOrden) throws Exception {
        long inicio = System.nanoTime();
        HttpResponse<InputStream> respuesta = http.send(pedir(RUTA + "/export.csv", sesionAdmin).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/csv"));
        assertThat(respuesta.headers().firstValue("Content-Disposition")).hasValueSatisfying(t -> assertThat(t).contains("caja-renaser.csv"));
        boolean sinContentLength = respuesta.headers().firstValue("Content-Length").isEmpty();
        Set<String> vistos = new HashSet<>();
        String anterior = "";
        int filas = 0;
        boolean bom;
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(respuesta.body(), StandardCharsets.UTF_8))) {
            String cabecera = lector.readLine();
            bom = cabecera.startsWith("﻿");
            assertThat(cabecera.substring(1)).startsWith("aprendizId;nombre;grupo;diaPrograma;estado;");
            String linea;
            while ((linea = lector.readLine()) != null) {
                String[] celdas = linea.split(";", 3);
                assertThat(vistos.add(celdas[0])).as("fila repetida: " + celdas[0]).isTrue();
                String nombre = celdas[1];
                if (nombre.contains(PREFIJO)) {
                    filas++;
                    // El CSV escapa una fórmula con un apóstrofo; el orden es el del nombre sin él (y sin mayúsculas ni tildes).
                    String clave = (nombre.startsWith("'") ? nombre.substring(1) : nombre).toLowerCase();
                    assertThat(clave).as("orden por nombre").isGreaterThanOrEqualTo(anterior);
                    anterior = clave;
                    if (idsEnOrden != null) {
                        idsEnOrden.add(celdas[0]);
                    }
                }
            }
        }
        return new Descarga(filas, (System.nanoTime() - inicio) / 1_000_000, sinContentLength, bom);
    }

    // spec 050 FR-032, FR-033, SC-008
    @Test
    @DisplayName("20.000 aprendices: el CSV llega completo y en orden, en streaming, y la memoria viva no crece con las filas")
    void veinteMilFilas() throws Exception {
        long antes = memoriaViva();
        List<String> ids = new ArrayList<>();
        Descarga[] resultado = new Descarga[1];

        long pico = picoDeMemoriaViva(() -> {
            try {
                resultado[0] = descargar(ids);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        long crecioMb = Math.max(0, pico - antes) / (1024 * 1024);
        System.out.printf("[medicion caja] descarga de %d aprendices: %d ms, memoria viva +%d MB sobre %d MB%n",
                resultado[0].filasDeAprendices(), resultado[0].milisegundos(), crecioMb, antes / (1024 * 1024));
        // Los 20.000 que aplican más la cuenta de la fórmula: los de «todavía no aplica» (Día 4) no salen.
        assertThat(resultado[0].filasDeAprendices()).isEqualTo(APLICAN + 1);
        assertThat(resultado[0].empiezaConBom()).isTrue();
        assertThat(resultado[0].sinContentLength()).as("se escribe por lotes: no hay un tamaño que anunciar").isTrue();
        assertThat(crecioMb).as("MB vivos de más mientras se descarga (tope %d)".formatted(TOPE_DE_MEMORIA_MB))
                .isLessThan(TOPE_DE_MEMORIA_MB);

        // El mismo orden y el mismo conjunto que la lista del Admin: la primera página, la del medio y la última.
        for (int pagina : new int[] {0, (APLICAN + 1) / 200 / 2, (APLICAN + 1) / 200}) {
            long inicio = System.nanoTime();
            HttpResponse<String> lista = http.send(pedir(RUTA + "?size=200&page=" + pagina + "&q="
                    + URLEncoder.encode(PREFIJO.strip(), StandardCharsets.UTF_8), sesionAdmin).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(lista.statusCode()).as(lista.body()).isEqualTo(200);
            JsonNode cuerpo = json.readTree(lista.body());
            List<String> deLaLista = new ArrayList<>();
            cuerpo.get("items").forEach(item -> deLaLista.add(item.get("aprendizId").asText()));
            System.out.printf("[medicion caja] página %d de la lista: %d ms%n", pagina, (System.nanoTime() - inicio) / 1_000_000);
            assertThat(cuerpo.get("total").asInt()).as("la lista cuenta a los mismos que el CSV").isEqualTo(APLICAN + 1);
            assertThat(deLaLista).as("la página " + pagina + " de la lista es la misma porción del CSV")
                    .isEqualTo(ids.subList(pagina * 200, Math.min(ids.size(), pagina * 200 + 200)));
        }
    }

    // spec 050 FR-032
    @Test
    @DisplayName("el guard de ADMIN pasa antes del primer byte: un mentor y una cuenta suspendida reciben 403 en JSON, no un CSV")
    void elGuardPasaAntesDeEscribir() throws Exception {
        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        String suspendido = pedidos.sesionDe(pedidos.usuario("ADMIN", "SUSPENDIDO"));

        for (String sesion : new String[] {mentor, suspendido}) {
            HttpResponse<String> respuesta = http.send(pedir(RUTA + "/export.csv", sesion).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(403);
            assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("application/json"));
            assertThat(respuesta.body()).doesNotContain("aprendizId;").contains("\"message\"");
        }
    }

    // spec 050 FR-034
    @Test
    @DisplayName("size=100000 en la lista de Caja se recorta al máximo (200), como siempre: no revienta ni devuelve todo")
    void tamanoDescomunalSeRecorta() throws Exception {
        HttpResponse<String> lista = http.send(pedir(RUTA + "?size=100000&page=0&q="
                + URLEncoder.encode(PREFIJO.strip(), StandardCharsets.UTF_8), sesionAdmin).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(lista.statusCode()).as(lista.body()).isEqualTo(200);
        assertThat(json.readTree(lista.body()).get("items")).hasSize(200);
    }
}
