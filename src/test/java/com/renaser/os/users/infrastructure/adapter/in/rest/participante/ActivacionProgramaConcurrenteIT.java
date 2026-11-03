package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos HTTP reales coinciden en la base. Desde la spec 036 la única fecha válida es mañana, así que hay dos situaciones:
 * <ul>
 *   <li><b>La misma fecha</b> (las dos válidas): se serializan en el cerrojo de la cuenta y las dos responden 200 con la
 *       misma fecha (idempotente), sobre una sola fila.</li>
 *   <li><b>Fechas distintas</b>: una es mañana y la otra no. Queda UNA fila con mañana; la que no es mañana nunca da 200 y
 *       responde 400 si llegó primero al cerrojo (el dominio la rechaza sin escribir) o 409 si llegó después de que la fila
 *       ya estaba activada. Cuál de las dos pasa primero lo decide el azar de los hilos y la prueba no depende de eso.</li>
 * </ul>
 *
 * <p><b>Por qué la prueba ya no exige que las dos esperen el candado externo (#64).</b> El cerrojo de la cuenta
 * ({@code SELECT … FOR UPDATE} sobre {@code usuarios}) es lo primero que hace el servicio y la fecha se valida DESPUÉS, ya
 * dentro de la sección crítica. Con fila, el candado externo de la prueba retiene la fila de {@code participantes_programa},
 * que solo toca el UPDATE de quien activa. Una fecha inválida que gana el cerrojo se rechaza al leer la fila (una lectura
 * normal no espera a un {@code FOR UPDATE}) y suelta el cerrojo sin escribir: nunca espera ese candado. Antes de la 036
 * las dos fechas eran válidas y las dos llegaban al UPDATE; ahora solo la válida, así que contar «≥2 esperando» fallaba
 * cuando la inválida ganaba el cerrojo. No era una carrera del servidor, sino una suposición de la prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ActivacionProgramaConcurrenteIT {
    private static final String RUTA = "/api/v1/onboarding/activate-program";
    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private SessionRepository<? extends Session> sesiones;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    static Stream<Arguments> casos() {
        return Stream.of("APRENDIZ", "MENTOR", "ADMIN", "ALQUIMISTA", "LIDER_MENTORES")
                .flatMap(rol -> Stream.of(true, false).filter(conFila -> conFila || !rol.equals("APRENDIZ"))
                        .flatMap(conFila -> Stream.of(true, false)
                                .map(mismaFecha -> Arguments.of(rol, conFila, mismaFecha))));
    }

    @AfterEach void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    // spec 036 FR-006
    @ParameterizedTest(name = "{0}, fila={1}, misma fecha={2}: una elección por cuenta")
    @MethodSource("casos")
    void dosConfirmacionesConservanLaPrimeraFecha(String rol, boolean conFila, boolean mismaFecha) throws Exception {
        UUID usuario = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Día 1 concurrente', CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, usuario, usuario + "@renaser.test", rol);
        usuarios.add(usuario);
        if (conFila) jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)", usuario);
        String token = sesionDe(sesiones, usuario);
        tokens.add(token);
        HttpResponse<String> consulta = http.send(pedido("GET", token, null), HttpResponse.BodyHandlers.ofString());
        assertThat(consulta.statusCode()).isEqualTo(200);
        var fechas = json.readTree(consulta.body()).get("validStartDates");
        assertThat(fechas).as("spec 036: una sola fecha, mañana").hasSize(1);
        String primera = fechas.get(0).asText();
        String segunda = mismaFecha ? primera : LocalDate.parse(primera).plusDays(2).toString();

        // El candado externo fuerza el solapamiento real en SQL, sin sleeps para adivinarlo.
        // Con fila bloquea su UPDATE; sin fila bloquea el padre de la FK. La implementación
        // anterior confirmaba dos fechas distintas cuando las dos habían leído «sin activar».
        var tomado = new CountDownLatch(1);
        var soltar = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try (var procesos = Executors.newVirtualThreadPerTaskExecutor()) {
            var candado = procesos.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                jdbc.queryForList(conFila
                        ? "SELECT usuario_id FROM renaser.participantes_programa WHERE usuario_id = ? FOR UPDATE"
                        : "SELECT id FROM renaser.usuarios WHERE id = ? FOR UPDATE", usuario);
                tomado.countDown();
                try {
                    if (!soltar.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó el fixture");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertThat(tomado.await(5, TimeUnit.SECONDS)).isTrue();
                var a = http.sendAsync(pedido("POST", token, primera), HttpResponse.BodyHandlers.ofString());
                var b = http.sendAsync(pedido("POST", token, segunda), HttpResponse.BodyHandlers.ofString());
                esperarAQueLasDosLleguenALaBase(pid.get(), mismaFecha ? null : b);
                soltar.countDown();
                candado.get(5, TimeUnit.SECONDS);
                var respuestas = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
                var codigos = respuestas.stream().map(HttpResponse::statusCode).toList();
                if (mismaFecha) {
                    assertThat(codigos).containsExactly(200, 200);
                } else {
                    assertThat(codigos.stream().filter(codigo -> codigo == 200)).hasSize(1);
                    assertThat(codigos.stream().filter(codigo -> codigo != 200)).hasSize(1).allMatch(
                            codigo -> codigo == 400 || codigo == 409);
                    // El 400 dice cuál es la fecha que sí vale; el 409 es «ya activado con otra».
                    respuestas.stream().filter(respuesta -> respuesta.statusCode() == 400)
                            .forEach(respuesta -> assertThat(respuesta.body()).contains(primera));
                }
                for (var respuesta : respuestas) if (respuesta.statusCode() == 200) {
                    String guardada = json.readTree(respuesta.body()).get("startDate").asText();
                    assertThat(guardada).isEqualTo(primera);
                    assertThat(jdbc.queryForObject("SELECT fecha_inicio::text FROM renaser.participantes_programa "
                            + "WHERE usuario_id = ?", String.class, usuario)).isEqualTo(guardada);
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participantes_programa WHERE usuario_id = ?",
                        Integer.class, usuario)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT dias_ajuste_programa FROM renaser.participantes_programa "
                        + "WHERE usuario_id = ?", Integer.class, usuario)).isZero();
            } finally {
                soltar.countDown();
            }
        }
    }

    /**
     * Espera a que las dos peticiones hayan llegado a la base con el candado externo puesto, sin depender de qué hilo corre
     * primero. Cuenta como «llegó» la que está esperando (directo, o detrás de la otra) y, solo cuando hay una fecha inválida
     * ({@code invalida} no es nulo), también la que ya terminó porque el dominio la rechazó: esa nunca espera el candado
     * (ver el javadoc de la clase). La válida tiene que estar esperando siempre, así que con una inválida alcanza con una
     * bloqueada MÁS la inválida ya respondida, o con dos bloqueadas si la inválida quedó en la cola del cerrojo.
     */
    private void esperarAQueLasDosLleguenALaBase(int pid, CompletableFuture<?> invalida) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        int esperando;
        boolean listas;
        do {
            esperando = jdbc.queryForObject("""
                    WITH RECURSIVE esperando AS (
                      SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                      UNION
                      SELECT a.pid FROM pg_stat_activity a JOIN esperando e ON e.pid = ANY(pg_blocking_pids(a.pid))
                    ) SELECT count(*) FROM esperando
                    """, Integer.class, pid);
            listas = esperando >= 2 || (invalida != null && invalida.isDone() && esperando >= 1);
            if (listas) break;
            Thread.sleep(20);
        } while (System.nanoTime() < limite);
        assertThat(listas).as("ambas peticiones llegaron a la base antes del commit (esperando: %d)", esperando).isTrue();
    }

    private HttpRequest pedido(String metodo, String token, String fecha) {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                .timeout(Duration.ofSeconds(20)).header("X-Auth-Token", token);
        return fecha == null ? pedido.GET().build() : pedido.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"startDate\":\"" + fecha + "\"}")).build();
    }

    private static <S extends Session> String sesionDe(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }
}
