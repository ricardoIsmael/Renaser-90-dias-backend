package com.renaser.os.plataforma.infrastructure.adapter.in.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.plataforma.infrastructure.adapter.out.redis.CanalDeFuncionalidades;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propagación entre instancias (spec 002, FR-006; contrato §9): una segunda copia en memoria —otra «instancia», con su
 * propia suscripción al canal y sin recarga programada— se pone al día por Redis en menos de 5 s cuando la consola
 * cambia algo. Si el aviso no viajara por el canal, esta copia seguiría en la versión vieja para siempre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PropagacionEntreInstanciasIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copiaDeEstaInstancia;
    @Autowired
    private CargarEstadosPort base;
    @Autowired
    private RedisConnectionFactory conexiones;
    @Autowired
    private StringRedisTemplate redis;

    private PedidosDePrueba pedidos;
    private CopiaDeEstados copiaDeOtraInstancia;
    private RedisMessageListenerContainer suscripcionDeOtraInstancia;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armarOtraInstancia() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        copiaDeOtraInstancia = new CopiaDeEstados(base);
        copiaDeOtraInstancia.recargar();
        suscripcionDeOtraInstancia = new RedisMessageListenerContainer();
        suscripcionDeOtraInstancia.setConnectionFactory(conexiones);
        suscripcionDeOtraInstancia.addMessageListener(
                SuscripcionAlCanalDeFuncionalidades.oyentePara(copiaDeOtraInstancia),
                new ChannelTopic(CanalDeFuncionalidades.NOMBRE));
        suscripcionDeOtraInstancia.afterPropertiesSet();
        suscripcionDeOtraInstancia.start();
    }

    @AfterEach
    void limpiar() throws Exception {
        suscripcionDeOtraInstancia.stop();
        suscripcionDeOtraInstancia.destroy();
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado");
        copiaDeEstaInstancia.recargar();
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("un cambio en la consola llega a la otra instancia por el canal Redis en menos de 5 s")
    void elCambioLlegaPorRedis() throws Exception {
        long antes = copiaDeOtraInstancia.foto().version();
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        String consola = pedidos.desbloquear(sesion);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prendidaGlobal", true);
        cuerpo.put("porRol", Map.of("TRAINEE", false));
        cuerpo.put("motivo", "Prueba de propagación");
        cuerpo.put("version", 0);

        assertThat(pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/chat.fotos", sesion, cuerpo,
                Map.of("X-Consola-Token", consola)).statusCode()).isEqualTo(200);

        long nueva = copiaDeEstaInstancia.foto().version();
        assertThat(nueva).as("esta instancia recargó al confirmar el cambio").isGreaterThan(antes);
        assertThat(esperarVersion(nueva, 5_000)).as("la otra instancia se puso al día por Redis").isTrue();
        assertThat(copiaDeOtraInstancia.foto().estadoDe("chat.fotos")).isPresent();
    }

    @Test
    @DisplayName("un aviso con una versión que no es más nueva no recarga; uno que no es un número se descarta")
    void avisosViejosOMalformadosNoRecargan() throws Exception {
        jdbc.update("""
                INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en)
                VALUES ('radar', false, nextval('renaser.funcionalidades_version_seq'), gen_random_uuid(), now())
                """);
        long version = copiaDeOtraInstancia.foto().version();

        redis.convertAndSend(CanalDeFuncionalidades.NOMBRE, Long.toString(version));
        redis.convertAndSend(CanalDeFuncionalidades.NOMBRE, "no-es-un-numero");
        Thread.sleep(500);

        assertThat(copiaDeOtraInstancia.foto().estadoDe("radar")).as("no recargó: la fila nueva no se ve").isEmpty();
    }

    private boolean esperarVersion(long version, long milisegundos) throws InterruptedException {
        long limite = System.currentTimeMillis() + milisegundos;
        while (System.currentTimeMillis() < limite) {
            if (copiaDeOtraInstancia.foto().version() >= version) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
