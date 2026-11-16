package com.renaser.os.shared.infrastructure.cache;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.ContadorDeVentanas;
import com.renaser.os.shared.domain.ContadorDeVentanas.Resultado;
import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contador del límite por cuenta contra <b>Redis real</b> (Testcontainers; spec 050, FR-012 y FR-015): el tope exacto, el
 * vencimiento que no se renueva, el rechazo que no consume las otras ventanas, dos pedidos a la vez y el {@code Retry-After}. Con un
 * doble del puerto se estaría probando el doble: lo que se afirma aquí es el script Lua.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ContadorDeVentanasRedisIT {

    private static final GrupoDeLimite GRUPO = GrupoDeLimite.MURO;

    @Autowired
    private ContadorDeVentanas contador;
    @Autowired
    private StringRedisTemplate redis;

    private static SujetoDeLimite sujetoNuevo() {
        return SujetoDeLimite.cuenta(UUID.randomUUID().toString());
    }

    private static String clave(SujetoDeLimite sujeto, String ventana) {
        return "prot:lim:" + GRUPO.clave() + ":" + ventana + ":" + sujeto.clave();
    }

    private Resultado intentar(SujetoDeLimite sujeto, Ventana... ventanas) {
        return contador.intentar(GRUPO, sujeto, List.of(ventanas));
    }

    // spec 050 FR-012
    @Test
    @DisplayName("el tope es exacto: pasan N pedidos y el N+1 se rechaza, y lo rechazado sigue contando para medir cuánto se pasó")
    void elTopeEsExacto() {
        SujetoDeLimite sujeto = sujetoNuevo();
        Ventana minuto = new Ventana("1m", Duration.ofMinutes(1), 3);

        for (int i = 1; i <= 3; i++) {
            Resultado resultado = intentar(sujeto, minuto);
            assertThat(resultado.paso()).as("pedido %s", i).isTrue();
            assertThat(resultado.usos().getFirst().usado()).as("lo contado tras el pedido %s", i).isEqualTo(i);
        }
        Resultado cuarto = intentar(sujeto, minuto);
        Resultado quinto = intentar(sujeto, minuto);

        assertThat(cuarto.paso()).isFalse();
        assertThat(cuarto.ventanaPasada()).isEqualTo("1m");
        assertThat(cuarto.usos()).singleElement().satisfies(uso -> {
            assertThat(uso.usado()).isEqualTo(4);
            assertThat(uso.proporcion()).isGreaterThan(1.0);
        });
        assertThat(quinto.usos().getFirst().usado()).as("el rechazo sigue contando").isEqualTo(5);
        assertThat(redis.opsForValue().get(clave(sujeto, "1m"))).isEqualTo("5");
    }

    // spec 050 FR-012
    @Test
    @DisplayName("cada cuenta tiene su contador: lo de una no gasta lo de otra")
    void cadaCuentaTieneElSuyo() {
        Ventana minuto = new Ventana("1m", Duration.ofMinutes(1), 1);
        SujetoDeLimite una = sujetoNuevo();
        SujetoDeLimite otra = sujetoNuevo();

        assertThat(intentar(una, minuto).paso()).isTrue();
        assertThat(intentar(una, minuto).paso()).isFalse();
        assertThat(intentar(otra, minuto).paso()).isTrue();
    }

    // spec 050 FR-012
    @Test
    @DisplayName("el vencimiento se fija en el primer pedido y NO se renueva con los siguientes: es «N por ventana», no «N seguidos»")
    void elVencimientoNoSeRenueva() throws Exception {
        SujetoDeLimite sujeto = sujetoNuevo();
        Ventana ventana = new Ventana("1m", Duration.ofSeconds(5), 100);

        intentar(sujeto, ventana);
        Long alPrincipio = redis.getExpire(clave(sujeto, "1m"), TimeUnit.MILLISECONDS);
        Thread.sleep(1_200);
        intentar(sujeto, ventana);
        Long despues = redis.getExpire(clave(sujeto, "1m"), TimeUnit.MILLISECONDS);

        assertThat(alPrincipio).isBetween(3_000L, 5_000L);
        assertThat(despues).as("el segundo pedido no reinició la ventana").isLessThan(alPrincipio - 1_000);
    }

    // spec 050 FR-012
    @Test
    @DisplayName("al vencer la ventana, el contador vuelve a cero y la cuenta pasa otra vez")
    void alVencerLaVentanaVuelveAPasar() throws Exception {
        SujetoDeLimite sujeto = sujetoNuevo();
        Ventana ventana = new Ventana("1m", Duration.ofSeconds(2), 1);

        assertThat(intentar(sujeto, ventana).paso()).isTrue();
        assertThat(intentar(sujeto, ventana).paso()).isFalse();
        Thread.sleep(2_300);

        assertThat(intentar(sujeto, ventana).paso()).isTrue();
    }

    // spec 050 FR-012
    @Test
    @DisplayName("una clave que quedó sin vencimiento se repara sola con el siguiente pedido (el defecto C-8)")
    void unaClaveSinVencimientoSeRepara() {
        SujetoDeLimite sujeto = sujetoNuevo();
        redis.opsForValue().set(clave(sujeto, "1m"), "1");
        assertThat(redis.getExpire(clave(sujeto, "1m"), TimeUnit.MILLISECONDS)).isEqualTo(-1L);

        intentar(sujeto, new Ventana("1m", Duration.ofMinutes(1), 10));

        assertThat(redis.getExpire(clave(sujeto, "1m"), TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);
    }

    // spec 050 FR-012
    @Test
    @DisplayName("un pedido rechazado por la ventana de la hora NO consume la del minuto, y al revés")
    void unRechazoNoConsumeLasOtrasVentanas() {
        SujetoDeLimite porHora = sujetoNuevo();
        Ventana minuto = new Ventana("1m", Duration.ofMinutes(1), 100);
        Ventana hora = new Ventana("1h", Duration.ofHours(1), 2);

        intentar(porHora, minuto, hora);
        intentar(porHora, minuto, hora);
        Resultado rechazado = intentar(porHora, minuto, hora);
        intentar(porHora, minuto, hora);

        assertThat(rechazado.paso()).isFalse();
        assertThat(rechazado.ventanaPasada()).isEqualTo("1h");
        assertThat(redis.opsForValue().get(clave(porHora, "1m"))).as("el minuto solo contó los 2 que pasaron").isEqualTo("2");
        assertThat(redis.opsForValue().get(clave(porHora, "1h"))).as("la hora sigue contando lo que se pasó").isEqualTo("4");

        SujetoDeLimite porMinuto = sujetoNuevo();
        Ventana minutoCorto = new Ventana("1m", Duration.ofMinutes(1), 2);
        Ventana horaLarga = new Ventana("1h", Duration.ofHours(1), 100);
        intentar(porMinuto, minutoCorto, horaLarga);
        intentar(porMinuto, minutoCorto, horaLarga);
        Resultado pasado = intentar(porMinuto, minutoCorto, horaLarga);

        assertThat(pasado.ventanaPasada()).isEqualTo("1m");
        assertThat(redis.opsForValue().get(clave(porMinuto, "1h"))).as("la hora no gastó el pedido rechazado por el minuto").isEqualTo("2");
    }

    // spec 050 FR-015
    @Test
    @DisplayName("Retry-After: los segundos que faltan para que venza la ventana que se pasó, enteros y nunca menos de 1")
    void elRetryAfterSaleDelVencimiento() {
        SujetoDeLimite sujeto = sujetoNuevo();
        Ventana minuto = new Ventana("1m", Duration.ofMinutes(1), 1);
        Ventana dia = new Ventana("1d", Duration.ofHours(24), 300);

        intentar(sujeto, minuto, dia);
        Resultado rechazado = intentar(sujeto, minuto, dia);

        assertThat(rechazado.paso()).isFalse();
        assertThat(rechazado.milisParaVencer()).isBetween(1L, 60_000L);
        assertThat(rechazado.segundosParaReintentar()).isBetween(1, 60);
        assertThat(redis.getExpire(clave(sujeto, "1d"), TimeUnit.SECONDS)).as("la ventana diaria dura 24 h")
                .isBetween(86_390L, 86_400L);
    }

    // spec 050 FR-012
    @Test
    @DisplayName("dos pedidos a la vez: con 16 hilos y 200 pedidos contra un tope de 25 pasan exactamente 25")
    void pedidosSimultaneosNoSePasanDelTope() throws Exception {
        SujetoDeLimite sujeto = sujetoNuevo();
        Ventana ventana = new Ventana("1m", Duration.ofMinutes(1), 25);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                Callable<Boolean> pedido = () -> intentar(sujeto, ventana).paso();
                resultados.add(hilos.submit(pedido));
            }
            int pasaron = 0;
            for (Future<Boolean> resultado : resultados) {
                pasaron += resultado.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(pasaron).isEqualTo(25);
            assertThat(redis.opsForValue().get(clave(sujeto, "1m"))).isEqualTo("200");
        } finally {
            hilos.shutdownNow();
        }
    }
}
