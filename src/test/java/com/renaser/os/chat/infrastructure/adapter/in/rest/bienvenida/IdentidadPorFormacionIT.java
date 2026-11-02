package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La identidad por formación (spec 035, D-308) de punta a punta: Tomcat real con sesión por {@code X-Auth-Token}, Spring Security e interceptor delante y Postgres
 * con V159. Cada formación tiene sus mensajes, su portada y su imagen, y NADA se cruza: una persona del Diplomado recibe la bienvenida del Diplomado y nunca la de
 * la Escuela, y la imagen de su comunidad es la suya.
 *
 * <p>Comparte la configuración (y el contexto) de {@code BienvenidaEditableIT}: bienvenidas prendidas y puerto aleatorio. En las pruebas el almacenamiento es de
 * marcador: la bienvenida sale sin tarjeta (solo el formal, G-5) y las imágenes no se pueden subir (409); las que se prueban acá se siembran en la bitácora.
 *
 * <p>La base se comparte con las demás pruebas de integración: no se supone que la bitácora esté vacía ni se cuentan filas de toda la tabla; al terminar se borra
 * solo lo que esta prueba sembró (lo que dejó cada cambio de sus cuentas) y las cuentas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "renaser.chat.bienvenida.activa=true")
@Import(TestcontainersConfiguration.class)
class IdentidadPorFormacionIT {

    private static final String BASE = "/api/v1/admin/bienvenida";
    private static final String IMAGEN = "/api/v1/admin/formaciones/%s/imagen";
    private static final String FORMAL_DIPLOMADO = "Hola, {nombre}. Bienvenida al Diplomado: tu ingreso está confirmado.";
    private static final Instant AHORA = Instant.parse("2027-03-10T03:00:00Z");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private DarBienvenidaEnSoporteUseCase darBienvenida;
    @Autowired
    private ListarConversacionesUseCase conversaciones;
    @Autowired
    private VerFotosDelChatUseCase fotos;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> soportes = new ArrayList<>();
    private UUID kelin;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        usuarios.clear();
        soportes.clear();
        kelin = usuario("ADMIN", "Kelin Rojas");
    }

    @AfterEach
    void limpiar() {
        // La bitácora es append-only para el código, no para la prueba: se borra solo lo que dejaron las cuentas de ESTA prueba.
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.cambios_bienvenida WHERE cambiado_por = ?", id));
        soportes.forEach(id -> jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    // ── La bienvenida de cada formación ─────────────────────────────────────

    @Test
    @DisplayName("cada persona recibe la bienvenida de SU formación: lo editado en el Diplomado no llega a la Escuela ni a la Certificación, y volver al original es al del Diplomado")
    void cadaSoporteRecibeLoDeSuFormacion() throws Exception {
        String sesion = sesionDe(kelin);
        String formalDeLaEscuela = texto(pedir("GET", BASE, sesion, null), "SOPORTE_FORMAL").path("original").asString();

        HttpResponse<String> guardado = pedir("PUT", BASE + "/textos/SOPORTE_FORMAL?formation=DIPLOMADO", sesion, Map.of("texto", FORMAL_DIPLOMADO));

        assertThat(guardado.statusCode()).isEqualTo(200);
        assertThat(json.readTree(guardado.body()).path("formation").asString()).isEqualTo("DIPLOMADO");
        assertThat(texto(guardado, "SOPORTE_FORMAL").path("cambiado").asBoolean()).isTrue();
        JsonNode laEscuela = texto(pedir("GET", BASE, sesion, null), "SOPORTE_FORMAL");
        assertThat(laEscuela.path("cambiado").asBoolean()).as("sin formation es la Escuela, como la app anterior").isFalse();
        assertThat(laEscuela.path("texto").asString()).isEqualTo(formalDeLaEscuela);
        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.cambios_bienvenida WHERE cambiado_por = ?", String.class, kelin))
                .isEqualTo("DIPLOMADO");

        String delaEscuela = formalQueRecibe("Ana Pérez", Formacion.ESCUELA);
        String delDiplomado = formalQueRecibe("Beto Díaz", Formacion.DIPLOMADO);
        String delaCertificacion = formalQueRecibe("Cleo Vega", Formacion.CERTIFICACION);

        assertThat(delaEscuela).isEqualTo(formalDeLaEscuela.replace("{nombre}", "Ana")).contains("Formación Renaser");
        assertThat(delDiplomado).isEqualTo(FORMAL_DIPLOMADO.replace("{nombre}", "Beto"));
        assertThat(delaCertificacion).contains("Certificación Renaser").doesNotContain("Diplomado").doesNotContain("Formación Renaser");

        HttpResponse<String> vuelta = pedir("DELETE", BASE + "/textos/SOPORTE_FORMAL?formation=DIPLOMADO", sesion, null);

        assertThat(vuelta.statusCode()).isEqualTo(200);
        assertThat(texto(vuelta, "SOPORTE_FORMAL").path("cambiado").asBoolean()).isFalse();
        assertThat(formalQueRecibe("Dora Soto", Formacion.DIPLOMADO)).as("el original DEL Diplomado, no el de la Escuela")
                .contains("al Diplomado Renaser").doesNotContain("Formación Renaser");
    }

    @Test
    @DisplayName("la portada del Diplomado: sin la suya sigue a la Escuela (heredada); la Escuela nunca hereda")
    void laPortadaSeHereda() throws Exception {
        String sesion = sesionDe(kelin);

        JsonNode delDiplomado = json.readTree(pedir("GET", BASE + "?formation=DIPLOMADO", sesion, null).body());
        JsonNode delaEscuela = json.readTree(pedir("GET", BASE + "?formation=ESCUELA", sesion, null).body());

        assertThat(delDiplomado.path("portada").path("heredada").asBoolean()).isTrue();
        assertThat(delDiplomado.path("portada").path("cambiada").asBoolean()).isFalse();
        assertThat(delaEscuela.path("portada").path("heredada").asBoolean()).isFalse();
        HttpResponse<byte[]> tarjeta = http.send(HttpRequest.newBuilder(URI.create(url(BASE + "/tarjeta?formation=DIPLOMADO&nombre=Beto")))
                .header("X-Auth-Token", sesion).header("Accept", "image/jpeg, application/json").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(tarjeta.statusCode()).isEqualTo(200);
        assertThat(tarjeta.body()).startsWith((byte) 0xFF, (byte) 0xD8);
    }

    // ── La imagen de cada formación ─────────────────────────────────────────

    @Test
    @DisplayName("la imagen: la del Diplomado llega solo a los chats del Diplomado; la Certificación sigue a la Escuela cuando esta tiene; la foto de otra formación es 404")
    void laImagenDeCadaFormacion() throws Exception {
        UUID ana = aprendiz("Ana de la Escuela", Formacion.ESCUELA, 90);
        UUID beto = aprendiz("Beto del Diplomado", Formacion.DIPLOMADO, 365);
        UUID cleo = aprendiz("Cleo de la Certificación", Formacion.CERTIFICACION, 90);
        sembrarImagen(Formacion.DIPLOMADO, "formaciones/imagenes/it-diplomado");

        ConversacionResumen deAna = general(conversaciones.listar(UserId.of(ana)));
        ConversacionResumen deBeto = general(conversaciones.listar(UserId.of(beto)));
        ConversacionResumen deCleo = general(conversaciones.listar(UserId.of(cleo)));

        assertThat(deBeto.conversacion().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(deBeto.imagenDeLaFormacionDesde()).as("el Diplomado tiene la suya").isNotNull();
        assertThat(deAna.imagenDeLaFormacionDesde()).as("la Escuela no tiene: usa la de la app").isNull();
        assertThat(deCleo.imagenDeLaFormacionDesde()).as("la Certificación no sigue al Diplomado").isNull();
        JsonNode global = general(json.readTree(pedir("GET", "/api/v1/chat/conversations", sesionDe(beto), null).body()));
        assertThat(global.path("conversation").path("photoPath").asString())
                .startsWith("/api/v1/chat/conversations/" + deBeto.conversacion().id().value() + "/foto?v=");
        JsonNode fotoDeAna = general(json.readTree(pedir("GET", "/api/v1/chat/conversations", sesionDe(ana), null).body()))
                .path("conversation").path("photoPath");
        assertThat(fotoDeAna.isNull() || fotoDeAna.isMissingNode()).as("la Escuela sin imagen: la app usa el fénix").isTrue();

        sembrarImagen(Formacion.ESCUELA, "formaciones/imagenes/it-escuela");

        assertThat(general(conversaciones.listar(UserId.of(cleo))).imagenDeLaFormacionDesde()).as("ahora sigue a la Escuela").isNotNull();
        assertThat(general(conversaciones.listar(UserId.of(ana))).imagenDeLaFormacionDesde()).isNotNull();

        ConversacionId delDiplomado = deBeto.conversacion().id();
        assertThatThrownBy(() -> fotos.fotoDeLaConversacion(UserId.of(ana), delDiplomado))
                .as("la comunidad de otra formación es como si no existiera").isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> fotos.fotoDeLaConversacion(UserId.of(beto), delDiplomado))
                .as("la imagen sembrada no existe en el almacenamiento de marcador: 404, no 500").isInstanceOf(NoSuchElementException.class);
        assertThat(pedir("GET", "/api/v1/chat/conversations/" + delDiplomado.value() + "/foto", sesionDe(ana), null).statusCode())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Administración ve la imagen de cada formación (propia, heredada o ninguna); sin almacenamiento de verdad no se sube (409) y la vista previa sin imagen es 404")
    void administracionVeLaImagen() throws Exception {
        String sesion = sesionDe(kelin);
        sembrarImagen(Formacion.DIPLOMADO, "formaciones/imagenes/it-diplomado");

        JsonNode diplomado = json.readTree(pedir("GET", imagen("DIPLOMADO"), sesion, null).body());
        JsonNode escuela = json.readTree(pedir("GET", imagen("ESCUELA"), sesion, null).body());
        JsonNode certificacion = json.readTree(pedir("GET", imagen("CERTIFICACION"), sesion, null).body());

        assertThat(diplomado.path("formation").asString()).isEqualTo("DIPLOMADO");
        assertThat(diplomado.path("hayImagen").asBoolean()).isTrue();
        assertThat(diplomado.path("cambiada").asBoolean()).isTrue();
        assertThat(diplomado.path("heredada").asBoolean()).isFalse();
        assertThat(diplomado.path("sePuedeCambiar").asBoolean()).isFalse();
        assertThat(diplomado.path("ultimoCambio").path("por").asString()).isEqualTo("Kelin Rojas");
        assertThat(escuela.path("hayImagen").asBoolean()).isFalse();
        assertThat(certificacion.path("hayImagen").asBoolean()).isFalse();

        JsonNode subida = json.readTree(pedir("POST", imagen("DIPLOMADO") + "/upload-url", sesion, Map.of("contentType", "image/png")).body());
        assertThat(subida.path("ruta").asString()).startsWith("formaciones/imagenes/");
        assertThat(subida.path("url").asString()).startsWith("about:blank#pendiente-s3/formaciones/imagenes/");
        assertThat(pedir("POST", imagen("DIPLOMADO") + "/confirm", sesion, Map.of("ruta", subida.path("ruta").asString())).statusCode())
                .isEqualTo(409);
        assertThat(pedir("POST", imagen("DIPLOMADO") + "/confirm", sesion, Map.of("ruta", "bienvenida/portadas/x")).statusCode())
                .as("solo rutas de imágenes de formaciones").isEqualTo(400);
        assertThat(pedir("POST", imagen("DIPLOMADO") + "/upload-url", sesion, Map.of("contentType", "image/gif")).statusCode()).isEqualTo(400);
        assertThat(pedir("GET", imagen("ESCUELA") + "/vista", sesion, null).statusCode()).as("sin imagen, la app muestra la suya").isEqualTo(404);
        assertThat(pedir("GET", imagen("MAESTRIA"), sesion, null).statusCode()).as("una formación que no existe").isEqualTo(404);
        assertThat(pedir("GET", BASE + "?formation=MAESTRIA", sesion, null).statusCode()).isEqualTo(400);

        HttpResponse<String> vuelta = pedir("DELETE", imagen("DIPLOMADO"), sesion, null);

        assertThat(vuelta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(vuelta.body()).path("cambiada").asBoolean()).isFalse();
        assertThat(json.readTree(vuelta.body()).path("hayImagen").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("autorización negativa: MENTOR, LÍDER, APRENDIZ, un ADMIN SUSPENDIDO y sin sesión reciben 403 en cada ruta nueva y en las de bienvenida con formación; no queda nada")
    void losDemasNo() throws Exception {
        List<String> rechazados = new ArrayList<>();
        rechazados.add(sesionDe(usuario("MENTOR", "Carlos Mentor")));
        rechazados.add(sesionDe(usuario("LIDER_MENTORES", "Lía Líder")));
        rechazados.add(sesionDe(usuario("APRENDIZ", "Ana Pérez")));
        rechazados.add(sesionDe(usuarioConEstado("ADMIN", "SUSPENDIDO", "Kelin Suspendida")));
        rechazados.add(null);

        for (String sesion : rechazados) {
            assertThat(pedir("GET", imagen("DIPLOMADO"), sesion, null).statusCode()).isEqualTo(403);
            assertThat(pedir("POST", imagen("DIPLOMADO") + "/upload-url", sesion, Map.of("contentType", "image/png")).statusCode()).isEqualTo(403);
            assertThat(pedir("POST", imagen("DIPLOMADO") + "/confirm", sesion, Map.of("ruta", "formaciones/imagenes/x")).statusCode()).isEqualTo(403);
            assertThat(pedir("DELETE", imagen("DIPLOMADO"), sesion, null).statusCode()).isEqualTo(403);
            assertThat(pedir("GET", imagen("DIPLOMADO") + "/vista", sesion, null).statusCode()).isEqualTo(403);
            assertThat(pedir("GET", BASE + "?formation=DIPLOMADO", sesion, null).statusCode()).isEqualTo(403);
            assertThat(pedir("PUT", BASE + "/textos/SOPORTE_FORMAL?formation=DIPLOMADO", sesion, Map.of("texto", FORMAL_DIPLOMADO)).statusCode())
                    .isEqualTo(403);
            assertThat(pedir("DELETE", BASE + "/portada?formation=DIPLOMADO", sesion, null).statusCode()).isEqualTo(403);
        }
        assertThat(cambiosDeEstaPrueba()).isZero();
    }

    @Test
    @DisplayName("V159: la formación de un cambio existe, la imagen solo acepta su prefijo y la carta de la caja es única; sin formación es de la Escuela")
    void laTablaDeV159() {
        jdbc.update("INSERT INTO renaser.cambios_bienvenida (pieza, texto, cambiado_por) VALUES ('GRUPO', '¡{nombre}! {mentor}', ?)", kelin);
        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.cambios_bienvenida WHERE cambiado_por = ?", String.class, kelin))
                .as("lo que no dice formación es de la Escuela (como todo lo anterior a V159)").isEqualTo("ESCUELA");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, texto, cambiado_por) "
                + "VALUES ('MAESTRIA', 'GRUPO', '¡{nombre}! {mentor}', ?)", kelin)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, portada_ruta, cambiado_por) "
                + "VALUES ('DIPLOMADO', 'IMAGEN_DE_LA_FORMACION', 'bienvenida/portadas/x', ?)", kelin))
                .as("la imagen no usa la ruta de las portadas").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, texto, cambiado_por) "
                + "VALUES ('DIPLOMADO', 'IMAGEN_DE_LA_FORMACION', 'x', ?)", kelin)).as("la imagen no trae texto")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, portada_ruta, cambiado_por) "
                + "VALUES ('DIPLOMADO', 'CARTA_CAJA', 'caja/cartas/x', ?)", kelin)).as("la carta de la caja es solo de la Escuela")
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, portada_ruta, cambiado_por) "
                + "VALUES ('CERTIFICACION', 'IMAGEN_DE_LA_FORMACION', 'formaciones/imagenes/ok', ?)", kelin);
    }

    // ── Siembra y pedidos ───────────────────────────────────────────────────

    private void sembrarImagen(Formacion formacion, String ruta) {
        jdbc.update("INSERT INTO renaser.cambios_bienvenida (formacion, pieza, portada_ruta, cambiado_por, cambiado_en) "
                + "VALUES (?, 'IMAGEN_DE_LA_FORMACION', ?, ?, now())", formacion.codigo(), ruta, kelin);
    }

    private int cambiosDeEstaPrueba() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida WHERE cambiado_por IN ("
                + usuarios.stream().map(id -> "'" + id + "'").reduce((a, b) -> a + "," + b).orElse("NULL") + ")", Integer.class);
    }

    /** Nace el soporte de una persona nueva en su formación y recibe la bienvenida: devuelve el texto que le llegó. */
    private String formalQueRecibe(String nombre, Formacion formacion) {
        UUID aprendiz = aprendiz(nombre, formacion, 90);
        UUID soporte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre, formacion) VALUES (?, 'SOPORTE', ?, ?, ?)",
                soporte, "soporte:" + aprendiz, nombre + " – " + formacion, formacion.codigo());
        soportes.add(soporte);

        darBienvenida.darBienvenida(ConversacionId.of(soporte), UserId.of(aprendiz));

        return jdbc.queryForObject("SELECT texto FROM renaser.mensajes WHERE conversacion_id = ? AND tipo = 'SISTEMA'", String.class, soporte);
    }

    private static ConversacionResumen general(List<ConversacionResumen> lista) {
        return lista.stream().filter(r -> r.conversacion().tipo() == TipoConversacion.GLOBAL).findFirst().orElseThrow();
    }

    private static JsonNode general(JsonNode lista) {
        for (JsonNode fila : lista) {
            if ("GLOBAL".equals(fila.path("conversation").path("type").asString())) {
                return fila;
            }
        }
        throw new AssertionError("Sin comunidad general en " + lista);
    }

    private static String imagen(String formacion) {
        return IMAGEN.formatted(formacion);
    }

    private JsonNode texto(HttpResponse<String> respuesta, String clave) throws Exception {
        for (JsonNode texto : json.readTree(respuesta.body()).path("textos")) {
            if (clave.equals(texto.path("clave").asString())) {
                return texto;
            }
        }
        throw new AssertionError("Sin el texto " + clave + " en " + respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url(ruta))).header("Accept", "application/json, image/jpeg");
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

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

    private UUID usuario(String rol, String nombre) {
        return usuarioConEstado(rol, "ACTIVO", nombre);
    }

    private UUID usuarioConEstado(String rol, String estado, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@identidad035.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }

    private UUID aprendiz(String nombre, Formacion formacion, int duracion) {
        UUID id = usuario("APRENDIZ", nombre);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, "
                + "formacion, duracion_dias) VALUES (?, DATE '2027-03-01', 5, TIMESTAMPTZ '2027-02-28 12:00:00+00', 'America/Lima', ?, ?)",
                id, formacion.codigo(), duracion);
        return id;
    }
}
