package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-16 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): el limite
 * de 60 solicitudes/hora por IP se chequeaba con un {@code COUNT} de Postgres y DESPUES se
 * insertaba -- check-then-act, sin nada que impidiera que varios envios concurrentes desde la
 * misma IP pasaran el chequeo a la vez y superaran el limite en rafaga. Requiere Redis real
 * (Testcontainers): el {@code INCR} atomico de {@code LimitarSolicitudesResetRedisAdapter} no se
 * puede probar con mocks, que es justo lo que hace atomico al chequeo-y-registro.
 *
 * <p>Se autowirea {@link SubmitAccountRequestUseCase} por su interfaz publica (bean real) para
 * ejercitar el limite tal como lo ve produccion, con {@code AccountRequestService} completo
 * (rate limit + verificacion de token + persistencia) en cada intento.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountRequestRateLimitConcurrenciaTest {

    /** Espeja {@code AccountRequestService.RATE_LIMIT_PER_HOUR} (privado a proposito, CLAUDE.MD
     * §5.4.8: el limite es un detalle de implementacion del servicio, no algo que un test deba
     * poder inyectar). Si ese numero cambia, este test hay que actualizarlo a mano. */
    private static final int LIMITE_POR_HORA = 60;
    private static final int INTENTOS_CONCURRENTES = LIMITE_POR_HORA + 15;

    @Autowired
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;
    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ip;
    private String prefijoEmail;

    @BeforeEach
    void nuevaIpYPrefijoPorTest() {
        // Un valor unico por test: la clave de Redis es "account-request:ip:" + ip, y el
        // contador vive en el contenedor compartido entre tests -- sin esto, tests que corran
        // en el mismo Redis interferirian entre si.
        // Tiene que ser una IP de verdad: solicitudes_cuenta.ip_solicitud es de tipo inet en
        // Postgres, asi que un identificador inventado revienta el INSERT antes de que el
        // limitador entre en juego. Usamos 2001:db8::/32 (RFC 3849, rango reservado para
        // documentacion), que es inet valido y deja espacio de sobra para una IP por test.
        String sufijo = UUID.randomUUID().toString().replace("-", "");
        ip = "2001:db8:" + sufijo.substring(0, 4) + ":" + sufijo.substring(4, 8)
                + ":" + sufijo.substring(8, 12) + ":" + sufijo.substring(12, 16)
                + ":" + sufijo.substring(16, 20) + ":" + sufijo.substring(20, 24);
        prefijoEmail = "ratelimit-" + UUID.randomUUID() + "-";
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.solicitudes_cuenta WHERE email LIKE ?", prefijoEmail + "%");
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE email LIKE ?", prefijoEmail + "%");
    }

    @Test
    @DisplayName("C-16: el limite de 60 solicitudes/hora por IP se respeta con envios concurrentes "
            + "(el check-then-act sobre Postgres permitia rafagas por encima del limite)")
    void elLimitePorIpSeRespetaBajoConcurrencia() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Boolean>>mapToObj(i -> () -> intentarSubmit(i))
                    .toList();
            resultados = pool.invokeAll(intentos, 60, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        long exitosos = resultados.stream().filter(AccountRequestRateLimitConcurrenciaTest::obtenerResultado).count();

        // El bug (C-16): con el COUNT de Postgres, una rafaga concurrente podia dejar pasar mas
        // de 60 antes de que ninguna hubiera hecho commit todavia. Con el INCR atomico de Redis,
        // nunca puede haber mas de 60 intentos que "vean" margen para la MISMA IP.
        assertThat(exitosos).as("nunca deben aceptarse mas de %s solicitudes por hora para la misma IP",
                LIMITE_POR_HORA).isEqualTo(LIMITE_POR_HORA);

        Long filasRealmenteInsertadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.solicitudes_cuenta WHERE email LIKE ?", Long.class,
                prefijoEmail + "%");
        assertThat(filasRealmenteInsertadas).as("las filas que realmente quedaron en la base no superan el limite")
                .isEqualTo((long) LIMITE_POR_HORA);
    }

    private boolean intentarSubmit(int i) {
        String email = prefijoEmail + i + "@renaser.test";
        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(10));
        try {
            submitAccountRequestUseCase.submit(SubmitAccountRequestCommand.porFormulario(email, "Fixture " + i,
                    null, null, token, "una-contrasena-de-12-o-mas", ip));
            return true;
        } catch (RateLimitExceededException e) {
            return false;
        }
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado (no RateLimitExceededException) en un intento concurrente",
                    e);
        }
    }
}
