package com.renaser.os.points.infrastructure.adapter.in.rest.home;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.HabitosDelDiaFinder;
import com.renaser.os.points.api.RocasDelDiaFinder;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.CumplimientoDeHoy;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mensaje del día del semáforo (D-277, spec 006) de punta a punta sobre el Tomcat real, Postgres y Redis
 * reales y un reloj que la prueba mueve: lo que ve la app en {@code GET /api/v1/home}, {@code semaforo.mensaje}.
 *
 * <p>Qué deja escrito: un mensaje por color y por caso sin color; el mismo {@code id} todo el día; otro al día
 * siguiente sin repetir en 14 días; el día de la persona es el de Lima aunque el reloj esté en la madrugada UTC;
 * si el color cambia entra un mensaje del color nuevo; {@code {faltan}} baja al cumplir un hábito; y el día 90
 * todavía no es graduado.
 *
 * <p>Las ventanas del semáforo se siembran directo en {@code semaforo_dias} (lo que deja el barrido) para
 * controlar el color; los hábitos de hoy, en {@code registros_habito}. Sin {@code @Transactional}: la lectura
 * pasa por HTTP, así que la prueba limpia lo que siembra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MedidorDeConsultas.class})
class HomeMensajeDelSemaforoIT {

    /** 10:00 en Lima del lunes 19 de octubre. */
    private static final Instant LUNES_10_LIMA = Instant.parse("2026-10-19T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);
    /** Día 20 el 19 de octubre. */
    private static final LocalDate INICIO_DIA_20 = HOY.minusDays(19);

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CatalogoDeMensajesPort catalogo;
    @Autowired
    private HabitosDelDiaFinder habitosDelDia;
    @Autowired
    private RocasDelDiaFinder rocasDelDia;
    @Autowired
    private SemaforoDelDiaFinder semaforoDelDia;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();

    @BeforeEach
    void relojAlLunes() {
        reloj.fijar(LUNES_10_LIMA);
    }

    @AfterEach
    void limpiar() {
        usuarios.reversed().forEach(id -> jdbcClient.sql("DELETE FROM renaser.usuarios WHERE id = :id")
                .param("id", id).update());
        habitos.forEach(id -> jdbcClient.sql("DELETE FROM renaser.habitos WHERE id = :id").param("id", id).update());
        jdbcClient.sql("UPDATE renaser.mensajes_semaforo SET activo = true").update();
        catalogo.recargar();
    }

    // ── los tres colores ────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}: mensaje del grupo, sin llaves, con sticker, y el mismo id todo el día")
    @CsvSource({"VERDE,10,v", "AMARILLO,7,a", "ROJO,3,r"})
    void cadaColorRecibeUnMensajeDeSuGrupo(String color, int cumplidosDeDiez, String prefijo) throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        ventanaTodosLosDias(persona, 10, cumplidosDeDiez);
        String sesion = sesionDe(persona);

        JsonNode semaforo = home(sesion).at("/semaforo");
        JsonNode mensaje = semaforo.get("mensaje");

        assertThat(semaforo.get("color").asText()).isEqualTo(color);
        assertThat(mensaje.get("id").asText()).startsWith(prefijo);
        assertThat(grupoDe(mensaje.get("id").asText())).isEqualTo(color);
        assertThat(mensaje.get("texto").asText()).isNotBlank().doesNotContain("{").doesNotContain("}");
        assertThat(mensaje.get("sticker").asText()).isNotBlank();
        assertThat(mensaje.get("texto").asText().length()).isLessThanOrEqualTo(300);

        String otraVez = home(sesion).at("/semaforo/mensaje/id").asText();
        assertThat(otraVez).isEqualTo(mensaje.get("id").asText());
        assertThat(filasElegidas(persona)).isEqualTo(1);
    }

    @Test
    @DisplayName("al día siguiente cambia de mensaje y no repite ninguno en 14 días")
    void noRepiteEnCatorceDias() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        ventanaTodosLosDias(persona, 10, 10);
        String sesion = sesionDe(persona);
        Set<String> vistos = new HashSet<>();

        for (int dia = 0; dia < 15; dia++) {
            String id = home(sesion).at("/semaforo/mensaje/id").asText();
            assertThat(vistos.add(id)).as("el día %d repitió %s", dia, id).isTrue();
            reloj.avanzar(Duration.ofDays(1));
        }
        assertThat(filasElegidas(persona)).isEqualTo(15);
    }

    @Test
    @DisplayName("con el reloj en la madrugada UTC, el día es el de Lima: la elección queda en el día local anterior")
    void elDiaEsElLocalEnLaMadrugadaUtc() throws Exception {
        reloj.fijar(Instant.parse("2026-10-19T03:00:00Z"));
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        ventanaTodosLosDias(persona, 10, 10);

        home(sesionDe(persona));

        LocalDate fecha = jdbcClient.sql(
                        "SELECT fecha FROM renaser.mensajes_semaforo_elegidos WHERE participante_id = :id")
                .param("id", persona).query(LocalDate.class).single();
        assertThat(fecha).as("03:00 UTC del 19 es el 18 a las 22:00 en Lima").isEqualTo(LocalDate.of(2026, 10, 18));
    }

    @Test
    @DisplayName("si el color cambia durante el día entra un mensaje del color nuevo, sin tocar el anterior")
    void elCambioDeColorEligeUnoNuevo() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        ventanaTodosLosDias(persona, 10, 10);
        String sesion = sesionDe(persona);
        String verde = home(sesion).at("/semaforo/mensaje/id").asText();

        jdbcClient.sql("UPDATE renaser.semaforo_dias SET habitos_cumplidos = 3 WHERE participante_id = :id")
                .param("id", persona).update();
        JsonNode despues = home(sesion).at("/semaforo");

        assertThat(despues.get("color").asText()).isEqualTo("ROJO");
        assertThat(despues.at("/mensaje/id").asText()).startsWith("r");
        assertThat(verde).startsWith("v");
        assertThat(filasElegidas(persona)).isEqualTo(2);
    }

    // ── {faltan} ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("{faltan} se escribe con lo que falta ahora: baja al cumplir un hábito y, con uno, dice «falta 1»")
    void faltanBajaAlCumplirUnHabito() throws Exception {
        jdbcClient.sql("UPDATE renaser.mensajes_semaforo SET activo = false WHERE grupo = 'AMARILLO' AND id <> 'a002'")
                .update();
        catalogo.recargar();
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        int[] cumplidosDeDiez = {5, 10, 8, 7, 7, 7, 7};
        for (int i = 0; i < cumplidosDeDiez.length; i++) {
            diaCerrado(persona, HOY.minusDays(7 - i), 10, cumplidosDeDiez[i]);
        }
        List<UUID> deHoy = hoyConTresHabitos(persona, 1);
        String sesion = sesionDe(persona);

        JsonNode antes = home(sesion).at("/semaforo");
        completar(persona, deHoy.get(1));
        JsonNode despues = home(sesion).at("/semaforo");

        assertThat(antes.get("color").asText()).isEqualTo("AMARILLO");
        assertThat(antes.at("/mensaje/id").asText()).isEqualTo("a002");
        assertThat(antes.at("/mensaje/texto").asText()).startsWith("Te faltan 2 para subir al siguiente color.");
        assertThat(despues.at("/mensaje/id").asText()).isEqualTo("a002");
        assertThat(despues.at("/mensaje/texto").asText()).startsWith("Te falta 1 para subir al siguiente color.");
    }

    // ── lo de hoy que /home le pasa al mensaje ──────────────────────────────

    /**
     * El {@code {faltan}} dejó de consultar el semáforo en vivo y usa lo que {@code /home} ya leyó para sus tarjetas.
     * Esta prueba es la que ata las dos cuentas: con opcionales sin cumplir (que no cuentan), opcionales cumplidos
     * (que sí) y objetivos del día, el avance armado desde las listas de {@code /home} es EXACTAMENTE el día en vivo
     * que calcula {@code SemaforoDelDiaFinder} sobre las mismas tablas.
     */
    @Test
    @DisplayName("el avance de hoy armado con las listas de /home es el mismo que calcula el semáforo en vivo")
    void loDeHoyDeHomeEsLoMismoQueElSemaforoEnVivo() {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        habitoDeHoy(persona, false, "COMPLETADO");
        habitoDeHoy(persona, false, "PENDIENTE");
        habitoDeHoy(persona, true, "PENDIENTE");
        habitoDeHoy(persona, true, "COMPLETADO");
        objetivoDeHoy(persona, 1, true);
        objetivoDeHoy(persona, 2, false);
        UserId id = UserId.of(persona);

        CumplimientoDelDia deHome = CumplimientoDeHoy.de(HOY, habitosDelDia.deHoy(id, HOY), rocasDelDia.deHoy(id));
        DiaDelSemaforo enVivo = semaforoDelDia.delDia(List.of(id), HOY).get(id);

        assertThat(enVivo.habitosProgramados()).as("3 de 4 hábitos cuentan: el opcional pendiente no").isEqualTo(3);
        assertThat(deHome.aDia()).isEqualTo(enVivo);
    }

    // ── costo en consultas (SC-004) ─────────────────────────────────────────

    @ParameterizedTest(name = "{0}: la primera lectura del día cuesta hasta 3 lecturas y 1 escritura; la siguiente, 1 lectura")
    @CsvSource({"VERDE", "AMARILLO", "ROJO"})
    void elMensajeCuestaPocasConsultas(String color) throws Exception {
        UUID persona = aprendizEn(color);
        soloUnMensajeConNombre(color);
        calentarElCatalogo();

        List<MedidorDeConsultas.Sentencia> primera = medirUnaLectura(sesionDe(persona), "primera " + color);
        List<MedidorDeConsultas.Sentencia> siguiente = medirUnaLectura(sesionDe(persona), "siguiente " + color);

        assertThat(lecturas(primera)).as("primera lectura del día: %s", primera).hasSizeLessThanOrEqualTo(3);
        assertThat(escrituras(primera)).as("primera lectura del día: %s", primera).hasSize(1);
        assertThat(lecturas(siguiente)).as("lecturas siguientes: %s", siguiente).hasSize(1);
        assertThat(escrituras(siguiente)).as("lecturas siguientes: %s", siguiente).isEmpty();
    }

    @Test
    @DisplayName("un día sin color (INICIO) cuesta 2 lecturas y 1 escritura la primera vez, y 1 lectura después")
    void unDiaSinColorCuestaMenos() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", HOY.plusDays(2));
        calentarElCatalogo();

        List<MedidorDeConsultas.Sentencia> primera = medirUnaLectura(sesionDe(persona), "primera INICIO");
        List<MedidorDeConsultas.Sentencia> siguiente = medirUnaLectura(sesionDe(persona), "siguiente INICIO");

        assertThat(lecturas(primera)).as("%s", primera).hasSizeLessThanOrEqualTo(2);
        assertThat(escrituras(primera)).as("%s", primera).hasSize(1);
        assertThat(lecturas(siguiente)).as("%s", siguiente).hasSize(1);
        assertThat(escrituras(siguiente)).as("%s", siguiente).isEmpty();
    }

    @Test
    @DisplayName("cumplir un hábito entre dos lecturas no suma consultas al mensaje: {faltan} sale de lo que /home ya leyó")
    void faltanNoCuestaConsultasPropias() throws Exception {
        UUID persona = aprendizEn("AMARILLO");
        soloUnMensajeConNombre("AMARILLO");
        List<UUID> deHoy = hoyConTresHabitos(persona, 1);
        calentarElCatalogo();
        String sesion = sesionDe(persona);
        medirUnaLectura(sesion, "primera AMARILLO");

        completar(persona, deHoy.get(1));
        List<MedidorDeConsultas.Sentencia> despues = medirUnaLectura(sesion, "tras cumplir un hábito");

        assertThat(lecturas(despues)).as("%s", despues).hasSize(1);
        assertThat(escrituras(despues)).as("%s", despues).isEmpty();
    }

    /** Un hábito cumplido y los demás pendientes; cada color con la ventana que lo produce y algo por hacer hoy. */
    private UUID aprendizEn(String color) {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        switch (color) {
            case "VERDE" -> ventanaTodosLosDias(persona, 10, 10);
            case "AMARILLO" -> ventanaDeSieteDias(persona, 5, 10, 8, 7, 7, 7, 7);
            default -> ventanaDeSieteDias(persona, 3, 6, 6, 6, 6, 6, 6);
        }
        hoyConTresHabitos(persona, 1);
        return persona;
    }

    private void ventanaDeSieteDias(UUID persona, int... cumplidosDeDiez) {
        for (int i = 0; i < cumplidosDeDiez.length; i++) {
            diaCerrado(persona, HOY.minusDays(cumplidosDeDiez.length - i), 10, cumplidosDeDiez[i]);
        }
    }

    /** Deja activo un solo mensaje del grupo, uno que pida el nombre (y {faltan} donde lo hay), para medir siempre lo mismo. */
    private void soloUnMensajeConNombre(String grupo) {
        String filtroDeFaltan = grupo.equals("VERDE") ? "" : " AND texto LIKE '%{faltan}%'";
        String elegido = jdbcClient.sql("SELECT id FROM renaser.mensajes_semaforo WHERE grupo = :grupo"
                        + " AND texto LIKE '%{nombre}%'" + filtroDeFaltan + " ORDER BY id LIMIT 1")
                .param("grupo", grupo).query(String.class).single();
        jdbcClient.sql("UPDATE renaser.mensajes_semaforo SET activo = (id = :id) WHERE grupo = :grupo")
                .param("id", elegido).param("grupo", grupo).update();
        catalogo.recargar();
    }

    /** El catálogo se guarda en memoria al primer uso: se carga con otra persona para no contarlo en la medida. */
    private void calentarElCatalogo() throws Exception {
        UUID otra = aprendiz("Marta Rojas", INICIO_DIA_20);
        ventanaTodosLosDias(otra, 10, 10);
        home(sesionDe(otra));
    }

    private List<MedidorDeConsultas.Sentencia> medirUnaLectura(String sesion, String que) throws Exception {
        MedidorDeConsultas.reiniciar();
        home(sesion);
        List<MedidorDeConsultas.Sentencia> deLaLectura = MedidorDeConsultas.sentencias().stream()
                .filter(MedidorDeConsultas.Sentencia::dentroDelMensaje).toList();
        long delPedido = MedidorDeConsultas.sentencias().stream()
                .filter(MedidorDeConsultas.Sentencia::esDeUnPedidoHttp).count();
        System.out.printf("[costo] %s: mensaje=%d lecturas + %d escrituras; /home completo=%d sentencias%n", que,
                lecturas(deLaLectura).size(), escrituras(deLaLectura).size(), delPedido);
        return deLaLectura;
    }

    private static List<MedidorDeConsultas.Sentencia> lecturas(List<MedidorDeConsultas.Sentencia> sentencias) {
        return sentencias.stream().filter(MedidorDeConsultas.Sentencia::esLectura).toList();
    }

    private static List<MedidorDeConsultas.Sentencia> escrituras(List<MedidorDeConsultas.Sentencia> sentencias) {
        return sentencias.stream().filter(MedidorDeConsultas.Sentencia::esEscritura).toList();
    }

    // ── los días sin color ──────────────────────────────────────────────────

    @Test
    @DisplayName("antes del Día 1 el mensaje es del caso INICIO")
    void antesDelDiaUno() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", HOY.plusDays(2));

        assertThat(grupoDe(home(sesionDe(persona)).at("/semaforo/mensaje/id").asText())).isEqualTo("INICIO");
    }

    @Test
    @DisplayName("pasada la primera semana, sin nada programado, el mensaje es del caso SIN_PROGRAMA")
    void sinNadaProgramado() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);

        assertThat(grupoDe(home(sesionDe(persona)).at("/semaforo/mensaje/id").asText())).isEqualTo("SIN_PROGRAMA");
    }

    @Test
    @DisplayName("con una pausa vigente (personal con programa propio) el mensaje es del caso PAUSA")
    void enPausa() throws Exception {
        UUID persona = usuario("Lucía Mendoza", "MENTOR");
        programa(persona, INICIO_DIA_20);
        jdbcClient.sql("""
                        INSERT INTO renaser.semaforo_pausas (id, usuario_id, motivo, desde, hasta, creada_en)
                        VALUES (:id, :u, 'PEDIDA_POR_LA_PERSONA', :desde, :hasta, :creada)
                        """)
                .param("id", UUID.randomUUID()).param("u", persona).param("desde", HOY).param("hasta", HOY.plusDays(5))
                .param("creada", Timestamp.from(LUNES_10_LIMA)).update();

        assertThat(grupoDe(home(sesionDe(persona)).at("/semaforo/mensaje/id").asText())).isEqualTo("PAUSA");
    }

    @Test
    @DisplayName("reactivada, con días de cuenta suspendida en la ventana y ninguno medido, es del caso CUENTA_PAUSADA")
    void cuentaReactivada() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", INICIO_DIA_20);
        jdbcClient.sql("""
                        INSERT INTO renaser.semaforo_pausas (id, usuario_id, motivo, desde, hasta, reanudada_el,
                                                             creada_en, reanudada_en)
                        VALUES (:id, :u, 'CUENTA_SUSPENDIDA', :desde, NULL, :reanudada, :creada, :reanudadaEn)
                        """)
                .param("id", UUID.randomUUID()).param("u", persona).param("desde", HOY.minusDays(6))
                .param("reanudada", HOY).param("creada", Timestamp.from(LUNES_10_LIMA.minusSeconds(7 * 86400L)))
                .param("reanudadaEn", Timestamp.from(LUNES_10_LIMA.minusSeconds(3600))).update();

        assertThat(grupoDe(home(sesionDe(persona)).at("/semaforo/mensaje/id").asText())).isEqualTo("CUENTA_PAUSADA");
    }

    @Test
    @DisplayName("el día 91 llega el semáforo con el mensaje GRADUADO, aunque no haya medición")
    void despuesDelDiaNoventa() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", HOY.minusDays(90));

        JsonNode semaforo = home(sesionDe(persona)).at("/semaforo");

        assertThat(semaforo.isObject()).as("sigue habiendo tarjeta después del día 90").isTrue();
        assertThat(grupoDe(semaforo.at("/mensaje/id").asText())).isEqualTo("GRADUADO");
        assertThat(semaforo.at("/mensaje/sticker").asText()).startsWith("dorado-");
    }

    @Test
    @DisplayName("el día 90 todavía no es graduado: sigue el color de la ventana")
    void elDiaNoventaSigueEnElPrograma() throws Exception {
        UUID persona = aprendiz("Lucía Mendoza", HOY.minusDays(89));
        ventanaTodosLosDias(persona, 10, 10);

        JsonNode semaforo = home(sesionDe(persona)).at("/semaforo");

        assertThat(grupoDe(semaforo.at("/mensaje/id").asText())).isEqualTo("VERDE");
    }

    @Test
    @DisplayName("quien no se mide (sin programa activado) no tiene semáforo y por lo tanto tampoco mensaje")
    void sinProgramaNoHayTarjeta() throws Exception {
        UUID persona = usuario("Lucía Mendoza", "APRENDIZ");
        jdbcClient.sql("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (:id, 0)")
                .param("id", persona).update();

        assertThat(home(sesionDe(persona)).path("semaforo").isContainerNode()).isFalse();
    }

    // ── pedido y semilla ────────────────────────────────────────────────────

    private JsonNode home(String sesion) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/home"))
                .header("X-Auth-Token", sesion).GET().build();
        HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
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

    private UUID aprendiz(String nombre, LocalDate diaUno) {
        UUID id = usuario(nombre, "APRENDIZ");
        programa(id, diaUno);
        return id;
    }

    private UUID usuario(String nombre, String rol) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").param("nombre", nombre).param("rol", rol)
                .update();
        usuarios.add(id);
        return id;
    }

    /** El programa activado con su Día 1 en {@code diaUno}; la columna del día es la que daría el reloj de hoy. */
    private void programa(UUID persona, LocalDate diaUno) {
        long dia = Math.clamp(ChronoUnit.DAYS.between(diaUno, HOY) + 1, 0, 90);
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, :dia, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", persona).param("dia", dia).param("inicio", diaUno)
                .param("activado", Timestamp.from(Instant.parse("2026-09-25T15:00:00Z"))).update();
    }

    /** Los días que pida la prueba mover el reloj (de 8 días antes a 20 después de hoy), todos iguales. */
    private void ventanaTodosLosDias(UUID persona, int programados, int cumplidos) {
        for (LocalDate dia = HOY.minusDays(8); !dia.isAfter(HOY.plusDays(20)); dia = dia.plusDays(1)) {
            diaCerrado(persona, dia, programados, cumplidos);
        }
    }

    private void diaCerrado(UUID persona, LocalDate fecha, int programados, int cumplidos) {
        jdbcClient.sql("""
                        INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados,
                                                           habitos_cumplidos, objetivos_programados,
                                                           objetivos_cumplidos, calculado_en)
                        VALUES (:id, :fecha, :programados, :cumplidos, 0, 0, :calculado)
                        """)
                .param("id", persona).param("fecha", fecha).param("programados", programados)
                .param("cumplidos", cumplidos).param("calculado", Timestamp.from(LUNES_10_LIMA)).update();
    }

    /** Tres hábitos de hoy: los primeros {@code cumplidos} ya cumplidos. Devuelve sus ids, en orden. */
    private List<UUID> hoyConTresHabitos(UUID persona, int cumplidos) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID habito = UUID.randomUUID();
            jdbcClient.sql("""
                            INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                            VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                            """)
                    .param("id", habito).param("titulo", "HABITO " + habito).update();
            habitos.add(habito);
            jdbcClient.sql("""
                            INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion,
                                                                 dia_programa, tipo_dia, es_opcional, estado)
                            VALUES (:id, :pid, :hid, :fecha, 20, CAST('DISCIPLINA' AS renaser.tipo_dia), false,
                                    CAST(:estado AS renaser.estado_registro))
                            """)
                    .param("id", UUID.randomUUID()).param("pid", persona).param("hid", habito).param("fecha", HOY)
                    .param("estado", i < cumplidos ? "COMPLETADO" : "PENDIENTE").update();
            ids.add(habito);
        }
        return ids;
    }

    private void habitoDeHoy(UUID persona, boolean opcional, String estado) {
        UUID habito = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                        """)
                .param("id", habito).param("titulo", "HABITO " + habito).update();
        habitos.add(habito);
        jdbcClient.sql("""
                        INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion,
                                                             dia_programa, tipo_dia, es_opcional, estado)
                        VALUES (:id, :pid, :hid, :fecha, 20, CAST('DISCIPLINA' AS renaser.tipo_dia), :opcional,
                                CAST(:estado AS renaser.estado_registro))
                        """)
                .param("id", UUID.randomUUID()).param("pid", persona).param("hid", habito).param("fecha", HOY)
                .param("opcional", opcional).param("estado", estado).update();
    }

    private void objetivoDeHoy(UUID persona, int posicion, boolean completada) {
        jdbcClient.sql("""
                        INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color,
                                                           puntaje_impacto, eje, completada)
                        VALUES (:id, :pid, :fecha, :posicion, 'Objetivo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo), :completada)
                        """)
                .param("id", UUID.randomUUID()).param("pid", persona).param("fecha", HOY)
                .param("posicion", posicion).param("completada", completada).update();
    }

    private void completar(UUID persona, UUID habito) {
        jdbcClient.sql("""
                        UPDATE renaser.registros_habito SET estado = CAST('COMPLETADO' AS renaser.estado_registro)
                        WHERE participante_id = :pid AND habito_id = :hid AND fecha_ejecucion = :fecha
                        """)
                .param("pid", persona).param("hid", habito).param("fecha", HOY).update();
    }

    private String grupoDe(String mensajeId) {
        return jdbcClient.sql("SELECT grupo FROM renaser.mensajes_semaforo WHERE id = :id")
                .param("id", mensajeId).query(String.class).single();
    }

    private int filasElegidas(UUID persona) {
        return jdbcClient.sql("SELECT count(*) FROM renaser.mensajes_semaforo_elegidos WHERE participante_id = :id")
                .param("id", persona).query(Integer.class).single();
    }
}
