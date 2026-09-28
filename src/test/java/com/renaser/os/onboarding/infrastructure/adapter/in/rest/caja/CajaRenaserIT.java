package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.onboarding.application.ports.in.caja.BarrerCajasUseCase;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.shared.application.ports.out.AlmacenamientoEnMemoria;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La Caja Renaser de punta a punta (D-219), contra Postgres real, por HTTP con sesiones de verdad y sin
 * dobles salvo el almacenamiento (en memoria: el PUT del teléfono se simula guardando los bytes) y el reloj
 * (movible, para los plazos de 3 y 5 días).
 *
 * <p>Ana empezó hace 9 días en Lima (va por su Día 10) y cumplió el 100 % de sus hábitos de los días 1 a 7:
 * pasa sola a «en revisión». Beto va por el mismo día sin hábitos: queda en evaluación. Carla puso Chile en
 * su ficha: fuera de la app. Mario es el mentor del grupo de Ana; Nora, una mentora de otro grupo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"renaser.caja.avisar-por-revisar=true", "renaser.scheduling.caja.cron=-"})
@Import({TestcontainersConfiguration.class, CajaRenaserIT.Dobles.class})
class CajaRenaserIT {

    private static final String ADMIN_CAJA = "/api/v1/admin/caja";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    static final class RelojMovible implements Clock {

        private volatile Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {
        @Bean
        @Primary
        AlmacenamientoEnMemoria almacenamientoEnMemoria() {
            return new AlmacenamientoEnMemoria();
        }

        @Bean
        @Primary
        RelojMovible relojMovible() {
            return new RelojMovible();
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AlmacenamientoEnMemoria almacenamiento;
    @Autowired
    private RelojMovible reloj;
    @Autowired
    private BarrerCajasUseCase barrido;
    @Autowired
    private EtapaOnboardingPort etapas;
    @Autowired
    private AsignarMentorCelulaUseCase asignarMentor;
    @Autowired
    private AsignarAprendizCelulaUseCase asignarAprendiz;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> usuarios = new ArrayList<>();
    private UUID admin;
    private UUID ana;
    private UUID beto;
    private UUID carla;
    private UUID mario;
    private UUID nora;
    private UUID soporteDeAna;
    private UUID habito;
    private UUID cohorte;
    private UUID grupo;
    private List<Map<String, Object>> contenidoOriginal;

    @BeforeEach
    void padron() {
        LocalDate hoyEnLima = reloj.now().atZone(LIMA).toLocalDate();
        LocalDate inicio = hoyEnLima.minusDays(9);
        admin = usuario("ADMIN", "Kelin Rojas", "ACTIVO");
        ana = aprendiz("Ana Pérez", inicio, "Perú");
        beto = aprendiz("Beto Díaz", inicio, "PERU");
        carla = aprendiz("Carla Soto", inicio, "Chile");
        habito = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, titulo, categoria_clave) VALUES (?, ?, "
                + "(SELECT clave FROM renaser.categorias_habito LIMIT 1))", habito, "Meditar " + habito);
        for (int dia = 0; dia < 7; dia++) {
            registro(ana, inicio, inicio.plusDays(dia), "COMPLETADO");
        }
        soporteDeAna = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, ?)",
                soporteDeAna, "soporte:" + ana, "Ana – Formación Renaser");
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                soporteDeAna, ana);
        mario = mentor("Mario Mentor");
        nora = mentor("Nora Mentora");
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte caja', CURRENT_DATE - 10)",
                cohorte);
        grupo = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, 'Grupo de Ana', ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20)
                """, grupo, cohorte);
        asignarMentor.asignar(new AsignarMentorCelulaCommand(UserId.of(admin), CelulaId.of(grupo), UserId.of(mario)));
        asignarAprendiz.asignar(new AsignarAprendizCelulaCommand(UserId.of(admin), CelulaId.of(grupo), UserId.of(ana)));
        contenidoOriginal = jdbc.queryForList("SELECT orden, valor, etiqueta FROM renaser.opciones_pregunta "
                + "WHERE pregunta_id = (SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'caja_contenido') "
                + "ORDER BY orden");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.opciones_pregunta WHERE pregunta_id = "
                + "(SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'caja_contenido')");
        contenidoOriginal.forEach(fila -> jdbc.update("INSERT INTO renaser.opciones_pregunta (pregunta_id, orden, valor, "
                        + "etiqueta) VALUES ((SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = "
                        + "'caja_contenido'), ?, ?, ?)", fila.get("orden"), fila.get("valor"), fila.get("etiqueta")));
        jdbc.update("DELETE FROM renaser.cambios_bienvenida WHERE pieza = 'CARTA_CAJA'");
        jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
        jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ? OR celula_id = ?", soporteDeAna, grupo);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", habito);
    }

    @Test
    @DisplayName("de en revisión a entregada: lista, avisos, checklist, fotos, envío, confirmación, problema y reenvío")
    void laCajaDePuntaAPunta() throws Exception {
        String kelin = sesionDe(admin);

        JsonNode lista = leer(pedir("GET", ADMIN_CAJA + "?size=200", kelin, null), 200);
        assertThat(item(lista, ana).path("estado").asString()).isEqualTo("POR_REVISAR");
        assertThat(item(lista, ana).path("diaPrograma").asInt()).isEqualTo(10);
        assertThat(item(lista, ana).path("cumplimientoFase1").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(item(lista, ana).path("grupo").asString()).isEqualTo("Grupo de Ana");
        assertThat(item(lista, beto).path("estado").asString()).isEqualTo("EN_EVALUACION");
        assertThat(item(lista, beto).path("cumplimientoFase1").isNull()).isTrue();
        assertThat(item(lista, carla).path("estado").asString()).isEqualTo("FUERA_DE_LA_APP");
        assertThat(lista.path("conteos").path("POR_REVISAR").asInt()).isPositive();
        JsonNode soloEvaluacion = leer(pedir("GET", ADMIN_CAJA + "?estado=EN_EVALUACION&q=beto&size=200", kelin, null), 200);
        assertThat(soloEvaluacion.path("items")).extracting(i -> i.path("aprendizId").asString())
                .containsExactly(beto.toString());

        // El barrido avisa «en revisión» una sola vez, en la bandeja de Ana, en la del Admin y en su chat.
        barrido.barrer();
        barrido.barrer();
        esperar(() -> notificaciones(ana) == 1 && notificaciones(admin) == 1 && mensajesDelSoporte() == 1);
        assertThat(ultimoMensajeDelSoporte()).isEqualTo("Tu Caja Renaser está en revisión.");

        // Beto no cumplió: el Admin lo aprueba caso por caso.
        assertThat(leer(pedir("POST", ADMIN_CAJA + "/" + beto + "/aprobar", kelin, null), 200).path("estado").asString())
                .isEqualTo("POR_REVISAR");
        assertThat(pedir("POST", ADMIN_CAJA + "/" + beto + "/aprobar", kelin, null).statusCode())
                .as("aprobarla dos veces no corresponde").isEqualTo(409);

        JsonNode armando = leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/armar", kelin, null), 200);
        assertThat(armando.path("estado").asString()).isEqualTo("ARMANDO");
        assertThat(armando.path("destino").path("dni").asString()).isEqualTo("DNI-" + ana.toString().substring(0, 8));
        assertThat(armando.path("contenido")).hasSize(8);

        HttpResponse<String> incompleta = pedir("POST", ADMIN_CAJA + "/" + ana + "/enviar", kelin,
                Map.of("medio", "Olva Courier", "courier", "Olva", "codigo", "OLV-1"));
        assertThat(incompleta.statusCode()).isEqualTo(409);
        assertThat(json.readTree(incompleta.body()).path("faltan")).extracting(JsonNode::asString)
                .containsExactly("CONTENIDO", "FOTO", "COMPROBANTE");

        List<String> todo = new ArrayList<>();
        armando.path("contenido").forEach(e -> todo.add(e.path("valor").asString()));
        JsonNode marcada = leer(pedir("PUT", ADMIN_CAJA + "/" + ana + "/contenido", kelin, Map.of("marcados", todo)), 200);
        assertThat(marcada.path("faltaParaEnviar")).extracting(JsonNode::asString).containsExactly("FOTO", "COMPROBANTE");
        subirYConfirmar(kelin, "foto", imagen(Color.ORANGE, 800, 800));
        JsonNode conFotos = subirYConfirmar(kelin, "comprobante", imagen(Color.WHITE, 800, 800));
        assertThat(conFotos.path("fotoArmadaUrl").asString()).contains("/caja/");
        assertThat(conFotos.path("faltaParaEnviar")).isEmpty();

        JsonNode enviada = leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/enviar", kelin,
                Map.of("medio", "Olva Courier", "courier", "Olva", "codigo", "OLV-1", "costo", 15.5)), 200);
        assertThat(enviada.path("estado").asString()).isEqualTo("ENVIADA");
        assertThat(enviada.path("envioDatos").path("rastreoUrl").asString()).isEqualTo("https://tracking.olvaexpress.pe/");
        assertThat(enviada.path("envioDatos").path("costo").decimalValue()).isEqualByComparingTo("15.5");
        esperar(() -> mensajesDelSoporte() == 4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ? "
                + "AND media_ruta LIKE 'chat/%'", Integer.class, soporteDeAna)).as("con la foto de la caja").isEqualTo(1);

        String sesionDeAna = sesionDe(ana);
        JsonNode miCaja = leer(pedir("GET", "/api/v1/me/caja", sesionDeAna, null), 200);
        assertThat(miCaja.path("estado").asString()).isEqualTo("ENVIADA");
        assertThat(miCaja.path("puedeConfirmar").asBoolean()).isTrue();
        assertThat(miCaja.path("puedeCambiarDestino").asBoolean()).isFalse();
        assertThat(miCaja.path("envioDatos").path("codigo").asString()).isEqualTo("OLV-1");
        assertThat(miCaja.path("envioDatos").has("costo")).as("el costo no es del aprendiz").isFalse();
        assertThat(pedir("PUT", "/api/v1/me/caja/destino", sesionDeAna, Map.of("otraDireccion", "Otra")).statusCode())
                .as("ya salió: no se cambia a dónde va").isEqualTo(409);

        // A los 3 días, el recordatorio a Ana; a los 5, el aviso al Admin. Una vez cada uno.
        reloj.avanzar(Duration.ofDays(3));
        barrido.barrer();
        reloj.avanzar(Duration.ofDays(2));
        barrido.barrer();
        barrido.barrer();
        esperar(() -> notificaciones(ana) == 4 && notificaciones(admin) == 2);

        JsonNode recibida = leer(pedir("POST", "/api/v1/me/caja/recibida", sesionDeAna, null), 200);
        assertThat(recibida.path("estado").asString()).isEqualTo("ENTREGADA");
        assertThat(pedir("POST", "/api/v1/me/caja/recibida", sesionDeAna, null).statusCode()).isEqualTo(409);
        esperar(() -> notificaciones(ana) == 5);

        JsonNode detalle = leer(pedir("GET", ADMIN_CAJA + "/" + ana, kelin, null), 200);
        assertThat(detalle.path("historial")).extracting(h -> h.path("estado").asString() + "/" + h.path("porNombre").asString())
                .containsExactly("ARMANDO/Kelin Rojas", "ENVIADA/Kelin Rojas", "ENTREGADA/Ana Pérez");

        // Llegó dañada: problema, reenvío con el envío 2 y el checklist de cero.
        JsonNode problema = leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/problema", kelin,
                Map.of("motivo", "DANADA", "nota", "Llegó mojada")), 200);
        assertThat(problema.path("estado").asString()).isEqualTo("CON_PROBLEMA");
        JsonNode reenvio = leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/reenviar", kelin, null), 200);
        assertThat(reenvio.path("estado").asString()).isEqualTo("ARMANDO");
        assertThat(reenvio.path("envio").asInt()).isEqualTo(2);
        assertThat(reenvio.path("faltaParaEnviar")).extracting(JsonNode::asString)
                .containsExactly("CONTENIDO", "FOTO", "COMPROBANTE");
        assertThat(reenvio.path("envioDatos").isNull()).isTrue();

        assertThat(etapas.flujosCompletados(UserId.of(ana))).as("el Mapa del Día 7 no se confunde con la caja")
                .doesNotContain("mapa_dia7").contains("caja:1:ENVIADA", "caja:2:ARMANDO");
    }

    @Test
    @DisplayName("la descarga: UTF-8 con BOM, «;», los datos de envío, y una fórmula escrita por el aprendiz no se ejecuta")
    void laDescarga() throws Exception {
        jdbc.update("UPDATE renaser.respuestas_onboarding SET valor_texto = '=HYPERLINK(\"x\")' WHERE usuario_id = ? "
                + "AND pregunta_id = (SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'address_reference')", ana);

        HttpResponse<byte[]> csv = pedirBytes(ADMIN_CAJA + "/export.csv", sesionDe(admin));

        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/csv"));
        byte[] cuerpo = csv.body();
        assertThat(new byte[]{cuerpo[0], cuerpo[1], cuerpo[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        String texto = new String(cuerpo, 3, cuerpo.length - 3, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("aprendizId;nombre;grupo;");
        String filaDeAna = texto.lines().filter(l -> l.startsWith(ana.toString())).findFirst().orElseThrow();
        assertThat(filaDeAna).contains("Ana Pérez", "DNI-" + ana.toString().substring(0, 8), "Miraflores")
                .contains("\"'=HYPERLINK(\"\"x\"\")\"");
    }

    @Test
    @DisplayName("el aprendiz cambia a dónde va antes de que salga, y el Admin lo ve en el detalle")
    void otroDestino() throws Exception {
        String sesionDeAna = sesionDe(ana);

        JsonNode cambiada = leer(pedir("PUT", "/api/v1/me/caja/destino", sesionDeAna,
                Map.of("otraDireccion", "Av. Arequipa 123", "quienRecibe", "Su mamá", "provincia", "Lima")), 200);
        assertThat(cambiada.path("destino").path("otraDireccion").asString()).isEqualTo("Av. Arequipa 123");
        assertThat(cambiada.path("pasos")).hasSize(5);

        JsonNode detalle = leer(pedir("GET", ADMIN_CAJA + "/" + ana, sesionDe(admin), null), 200);
        assertThat(detalle.path("destino").path("quienRecibe").asString()).isEqualTo("Su mamá");
        assertThat(detalle.path("destino").path("celular").asString()).isEqualTo("+51 999 111 222");

        leer(pedir("PUT", "/api/v1/me/caja/destino", sesionDeAna, Map.of("otraDireccion", " ")), 200);
        assertThat(leer(pedir("GET", ADMIN_CAJA + "/" + ana, sesionDe(admin), null), 200)
                .path("destino").path("otraDireccion").isNull()).as("vacío borra la respuesta").isTrue();
    }

    @Test
    @DisplayName("el contenido editable y la carta con el nombre, con su fondo cambiable y la vuelta al original")
    void contenidoYCarta() throws Exception {
        String kelin = sesionDe(admin);
        JsonNode lista = leer(pedir("PUT", ADMIN_CAJA + "/contenido", kelin, Map.of("elementos", List.of(
                Map.of("etiqueta", "Caja verde", "valor", "caja_verde"), Map.of("etiqueta", "Piedra de cuarzo")))), 200);
        assertThat(lista.path("elementos")).extracting(e -> e.path("valor").asString())
                .containsExactly("caja_verde", "piedra_de_cuarzo");
        assertThat(pedir("PUT", ADMIN_CAJA + "/contenido", kelin, Map.of("elementos", List.of())).statusCode())
                .isEqualTo(400);

        HttpResponse<byte[]> carta = pedirBytes(ADMIN_CAJA + "/" + ana + "/carta", kelin);
        assertThat(carta.statusCode()).isEqualTo(200);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(carta.body()));
        assertThat(original.getHeight()).isGreaterThan(original.getWidth());

        JsonNode subida = leer(pedir("POST", ADMIN_CAJA + "/carta/fondo/upload-url", kelin,
                Map.of("contentType", "image/png")), 200);
        String ruta = subida.path("ruta").asString();
        assertThat(ruta).startsWith("caja/cartas/");
        almacenamiento.guardar(ruta, imagen(new Color(0xCF, 0xE8, 0xF5), 1200, 1700));
        assertThat(leer(pedir("POST", ADMIN_CAJA + "/carta/fondo/confirm", kelin, Map.of("ruta", ruta)), 200)
                .path("cambiado").asBoolean()).isTrue();
        BufferedImage conFondo = ImageIO.read(new ByteArrayInputStream(pedirBytes(ADMIN_CAJA + "/" + ana + "/carta", kelin).body()));
        assertThat(new Color(conFondo.getRGB(20, 20))).isEqualTo(new Color(0xCF, 0xE8, 0xF5));

        assertThat(leer(pedir("DELETE", ADMIN_CAJA + "/carta/fondo", kelin, null), 200).path("cambiado").asBoolean())
                .isFalse();
        assertThat(pedirBytes(ADMIN_CAJA + "/" + beto + "/carta", kelin).statusCode()).isEqualTo(200);
        assertThat(pedirBytes(ADMIN_CAJA + "/" + mario + "/carta", kelin).statusCode()).as("no es aprendiz").isEqualTo(404);
    }

    @Test
    @DisplayName("seguridad: solo ADMIN activo opera; mentor ajeno, cuenta suspendida y /onboarding/answers → 403")
    void seguridad() throws Exception {
        UUID alquimista = usuario("ALQUIMISTA", "Alquimista", "ACTIVO");
        UUID adminSuspendido = usuario("ADMIN", "Admin suspendido", "SUSPENDIDO");
        List<String> rutas = List.of("GET " + ADMIN_CAJA, "GET " + ADMIN_CAJA + "/" + ana, "POST " + ADMIN_CAJA + "/" + beto
                + "/aprobar", "GET " + ADMIN_CAJA + "/export.csv", "GET " + ADMIN_CAJA + "/contenido",
                "GET " + ADMIN_CAJA + "/" + ana + "/carta", "POST " + ADMIN_CAJA + "/carta/fondo/upload-url");
        for (UUID quien : List.of(mario, alquimista, ana, adminSuspendido)) {
            String sesion = sesionDe(quien);
            for (String ruta : rutas) {
                String[] partes = ruta.split(" ");
                assertThat(pedirBytes(partes[0], partes[1], sesion).statusCode()).as(quien + " " + ruta).isEqualTo(403);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.etapas_onboarding_completadas WHERE usuario_id = ?",
                Integer.class, beto)).as("nadie aprobó a Beto").isZero();

        assertThat(leer(pedir("GET", "/api/v1/mentor/trainees/" + ana + "/caja", sesionDe(mario), null), 200)
                .path("estado").asString()).isEqualTo("POR_REVISAR");
        assertThat(pedir("GET", "/api/v1/mentor/trainees/" + ana + "/caja", sesionDe(nora), null).statusCode())
                .as("mentora de otro grupo").isEqualTo(403);
        assertThat(pedir("GET", "/api/v1/mentor/trainees/" + beto + "/caja", sesionDe(mario), null).statusCode())
                .as("Beto no es de su grupo").isEqualTo(403);

        String sesionDeAna = sesionDe(ana);
        int contenido = idDePregunta("caja_contenido");
        HttpResponse<String> marcarse = pedir("POST", "/api/v1/onboarding/answers", sesionDeAna,
                Map.of("questionId", contenido, "jsonValue", "[\"caja_verde\"]"));
        assertThat(marcarse.statusCode()).as("el aprendiz no se marca el checklist").isEqualTo(403);
        assertThat(pedir("POST", "/api/v1/onboarding/answers", sesionDeAna,
                Map.of("questionId", idDePregunta("caja_otra_direccion"), "textValue", "x")).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.respuestas_onboarding WHERE usuario_id = ? "
                + "AND pregunta_id = ?", Integer.class, ana, contenido)).isZero();
        assertThat(pedir("POST", "/api/v1/onboarding/answers", sesionDeAna,
                Map.of("questionId", idDePregunta("city"), "textValue", "Lima")).statusCode())
                .as("las demás preguntas siguen abiertas").isEqualTo(200);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana);
        assertThat(pedir("GET", "/api/v1/me/caja", sesionDeAna, null).statusCode()).isEqualTo(403);
        assertThat(pedir("POST", "/api/v1/me/caja/recibida", sesionDeAna, null).statusCode()).isEqualTo(403);
        assertThat(leer(pedir("GET", ADMIN_CAJA + "/" + ana, sesionDe(admin), null), 200).path("estado").asString())
                .isEqualTo("EN_PAUSA");
    }

    // ── Pasos ───────────────────────────────────────────────────────────────

    private JsonNode subirYConfirmar(String sesion, String cual, byte[] png) throws Exception {
        JsonNode subida = leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/" + cual + "/upload-url", sesion,
                Map.of("contentType", "image/png")), 200);
        String ruta = subida.path("ruta").asString();
        assertThat(ruta).startsWith("onboarding/" + ana + "/caja/");
        assertThat(pedir("POST", ADMIN_CAJA + "/" + ana + "/" + cual + "/confirm", sesion, Map.of("ruta", ruta)).statusCode())
                .as("todavía no se subió nada").isEqualTo(404);
        almacenamiento.guardar(ruta, png);
        return leer(pedir("POST", ADMIN_CAJA + "/" + ana + "/" + cual + "/confirm", sesion, Map.of("ruta", ruta)), 200);
    }

    private JsonNode item(JsonNode lista, UUID aprendiz) {
        for (JsonNode item : lista.path("items")) {
            if (item.path("aprendizId").asString().equals(aprendiz.toString())) {
                return item;
            }
        }
        throw new AssertionError(aprendiz + " no está en la lista: " + lista);
    }

    private int notificaciones(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? "
                + "AND tipo = 'HITO_PROGRAMA' AND titulo IN ('Tu Caja Renaser', 'Caja Renaser')", Integer.class, usuario);
    }

    private int mensajesDelSoporte() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?", Integer.class,
                soporteDeAna);
    }

    private String ultimoMensajeDelSoporte() {
        return jdbc.queryForObject("SELECT texto FROM renaser.mensajes WHERE conversacion_id = ? AND texto IS NOT NULL "
                + "ORDER BY creado_en DESC LIMIT 1", String.class, soporteDeAna);
    }

    private int idDePregunta(String clave) {
        return jdbc.queryForObject("SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?", Integer.class,
                clave);
    }

    private static void esperar(Supplier<Boolean> condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (!condicion.get()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("Los avisos no llegaron a tiempo");
            }
            Thread.sleep(200);
        }
    }

    private JsonNode leer(HttpResponse<String> respuesta, int esperado) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(esperado);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url(ruta))).header("X-Auth-Token", sesion);
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> pedirBytes(String ruta, String sesion) throws Exception {
        return pedirBytes("GET", ruta, sesion);
    }

    private HttpResponse<byte[]> pedirBytes(String metodo, String ruta, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url(ruta))).header("X-Auth-Token", sesion);
        if ("POST".equals(metodo)) {
            pedido.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
        } else {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] imagen(Color color, int ancho, int alto) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private String sesionDe(UUID usuario) {
        return guardarSesion(sesiones, usuario);
    }

    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }

    private UUID usuario(String rol, String nombre, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }

    private UUID mentor(String nombre) {
        UUID id = usuario("MENTOR", nombre, "ACTIVO");
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id);
        return id;
    }

    /** Un aprendiz con el programa activado desde {@code inicio} y su Ficha Inicial con los datos de envío. */
    private UUID aprendiz(String nombre, LocalDate inicio, String pais) {
        UUID id = usuario("APRENDIZ", nombre, "ACTIVO");
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en,
                                                            timezone)
                VALUES (?, 10, ?, ?, 'America/Lima')
                """, id, inicio, Timestamp.from(reloj.now().minus(Duration.ofDays(10))));
        Map<String, String> ficha = Map.of("full_name", nombre, "whatsapp", "+51 999 111 222", "country", pais,
                "city", "Lima", "district", "Miraflores", "address_reference", "Calle Los Pinos 456, puerta verde",
                "identity_document", "DNI-" + id.toString().substring(0, 8));
        ficha.forEach((clave, valor) -> jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                VALUES (?, (SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?), ?)
                """, id, clave, valor));
        return id;
    }

    private void registro(UUID participante, LocalDate inicio, LocalDate fecha, String estado) {
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                     tipo_dia, es_opcional, estado)
                VALUES (?, ?, ?, ?, ?, CAST('DISCIPLINA' AS renaser.tipo_dia), false,
                        CAST(? AS renaser.estado_registro))
                """, UUID.randomUUID(), participante, habito, fecha, (int) ChronoUnit.DAYS.between(inicio, fecha) + 1,
                estado);
    }
}
