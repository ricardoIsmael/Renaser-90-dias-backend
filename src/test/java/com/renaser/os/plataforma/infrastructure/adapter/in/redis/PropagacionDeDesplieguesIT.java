package com.renaser.os.plataforma.infrastructure.adapter.in.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
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
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propagación de los despliegues entre instancias (contrato fase 2 §3.2): una segunda copia en memoria —otra
 * «instancia», con su propia suscripción al canal y sin recarga programada— ve empezar y terminar un despliegue en
 * menos de 5 s. Sin el aviso por Redis esta copia seguiría con lo de antes hasta la recarga de 30 s.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PropagacionDeDesplieguesIT {

    private static final String DESPLIEGUE = "/api/v1/soporte-ti/funcionalidades/radar/despliegue";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeDespliegues copiaDeEstaInstancia;
    @Autowired
    private CargarDesplieguesPort base;
    @Autowired
    private RedisConnectionFactory conexiones;

    private PedidosDePrueba pedidos;
    private CopiaDeDespliegues copiaDeOtraInstancia;
    private RedisMessageListenerContainer suscripcionDeOtraInstancia;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armarOtraInstancia() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        copiaDeOtraInstancia = new CopiaDeDespliegues(base);
        copiaDeOtraInstancia.recargar();
        suscripcionDeOtraInstancia = new RedisMessageListenerContainer();
        suscripcionDeOtraInstancia.setConnectionFactory(conexiones);
        suscripcionDeOtraInstancia.addMessageListener(SuscripcionDeDespliegues.oyentePara(copiaDeOtraInstancia),
                new ChannelTopic(CanalDeFuncionalidades.NOMBRE));
        suscripcionDeOtraInstancia.afterPropertiesSet();
        suscripcionDeOtraInstancia.start();
    }

    @AfterEach
    void limpiar() throws Exception {
        suscripcionDeOtraInstancia.stop();
        suscripcionDeOtraInstancia.destroy();
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue");
        copiaDeEstaInstancia.recargar();
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("empezar y terminar un despliegue llega a la otra instancia por el canal Redis en menos de 5 s")
    void llegaPorRedis() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));
        assertThat(copiaDeOtraInstancia.foto().de("radar")).isEmpty();

        assertThat(pedidos.enviar("PUT", DESPLIEGUE, sesion, cuerpo(25), consola).statusCode()).isEqualTo(200);

        assertThat(copiaDeEstaInstancia.foto().de("radar")).as("esta instancia recargó al confirmar").isPresent();
        assertThat(esperar(() -> copiaDeOtraInstancia.foto().de("radar").isPresent())).as("la otra, por Redis").isTrue();

        assertThat(pedidos.enviar("PUT", DESPLIEGUE, sesion, cuerpo(100), consola).statusCode()).isEqualTo(200);

        assertThat(esperar(() -> copiaDeOtraInstancia.foto().de("radar").isEmpty()))
                .as("terminar también llega, aunque la versión «mayor» baje").isTrue();
    }

    private static Map<String, Object> cuerpo(int porcentaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("porcentaje", porcentaje);
        cuerpo.put("motivo", "Prueba de propagación");
        return cuerpo;
    }

    private static boolean esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
