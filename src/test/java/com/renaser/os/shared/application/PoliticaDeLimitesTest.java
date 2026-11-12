package com.renaser.os.shared.application;

import com.renaser.os.shared.application.DecisionDeLimite.Accion;
import com.renaser.os.shared.application.ports.out.AnotarCuentaQueHabriaBloqueadoPort;
import com.renaser.os.shared.application.ports.out.ModoDeLosLimitesPort;
import com.renaser.os.shared.application.ports.out.RegistrarMetricaDeLimitesPort;
import com.renaser.os.shared.application.ports.out.TopesDeLimites;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ContadorDeVentanas;
import com.renaser.os.shared.domain.ContadorDeVentanas.Resultado;
import com.renaser.os.shared.domain.ContadorDeVentanas.Uso;
import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import com.renaser.os.shared.domain.ResultadoDelLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La política del límite por cuenta (spec 050, FR-016 y FR-017): los tres modos, que {@code REGISTRAR} nunca rechaza, el modo que
 * cambia sin reiniciar y el fallo de Redis que deja pasar. Sin Spring y sin Redis: los puertos son dobles.
 */
class PoliticaDeLimitesTest {

    private static final GrupoDeLimite MURO = GrupoDeLimite.MURO;
    private static final SujetoDeLimite CUENTA = SujetoDeLimite.cuenta("3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f");
    private static final List<Ventana> VENTANAS = List.of(new Ventana("1m", Duration.ofMinutes(1), 10));

    private final TopesDePrueba topes = new TopesDePrueba();
    private final RelojManual reloj = new RelojManual();
    private final Map<GrupoDeLimite, ModoDeLimite> enRedis = new EnumMap<>(GrupoDeLimite.class);
    private final AtomicInteger lecturasDelModo = new AtomicInteger();
    private final RegistrarMetricaDeLimitesPort metricas = mock(RegistrarMetricaDeLimitesPort.class);
    private final AnotarCuentaQueHabriaBloqueadoPort anotador = mock(AnotarCuentaQueHabriaBloqueadoPort.class);
    private final AtomicInteger llamadasAlContador = new AtomicInteger();
    private ContadorDeVentanas contador;
    private boolean redisLeEstaFallando;
    private PoliticaDeLimites politica;

    @BeforeEach
    void armar() {
        contador = (grupo, sujeto, ventanas) -> {
            llamadasAlContador.incrementAndGet();
            return new Resultado(true, "", 0, List.of(new Uso("1m", 3, 10)));
        };
        ModoDeLosLimitesPort modos = () -> {
            lecturasDelModo.incrementAndGet();
            if (redisLeEstaFallando) {
                throw new IllegalStateException("Redis no responde");
            }
            return new EnumMap<>(enRedis);
        };
        politica = new PoliticaDeLimites(topes, (grupo, sujeto, ventanas) -> contador.intentar(grupo, sujeto, ventanas), modos,
                metricas, anotador, reloj);
    }

    private void elContadorDice(Resultado resultado) {
        contador = (grupo, sujeto, ventanas) -> {
            llamadasAlContador.incrementAndGet();
            return resultado;
        };
    }

    private static Resultado seDioPor(long milisParaVencer) {
        return new Resultado(false, "1m", milisParaVencer, List.of(new Uso("1m", 11, 10)));
    }

    // spec 050 FR-016
    @Test
    @DisplayName("REGISTRAR: si se pasó, el pedido PASA, queda anotado como «habría bloqueado» y nunca se rechaza")
    void registrarNuncaRechaza() {
        topes.modo = ModoDeLimite.REGISTRAR;
        elContadorDice(seDioPor(42_000));

        DecisionDeLimite decision = politica.decidir(MURO, CUENTA);

        assertThat(decision.accion()).isEqualTo(Accion.PASAR_Y_ANOTAR);
        assertThat(decision.rechaza()).isFalse();
        assertThat(decision.habriaBloqueado()).isTrue();
        assertThat(decision.ventanaPasada()).isEqualTo("1m");
        verify(metricas).pedido(MURO, ResultadoDelLimite.HABRIA_BLOQUEADO);
        verify(metricas).uso(MURO, "1m", 1.1);
        verify(anotador).anotar(MURO, CUENTA);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("APLICAR: si se pasó, rechaza con los segundos que faltan (redondeados hacia arriba, nunca menos de 1)")
    void aplicarRechazaConRetryAfter() {
        topes.modo = ModoDeLimite.APLICAR;

        elContadorDice(seDioPor(42_000));
        assertThat(politica.decidir(MURO, CUENTA).reintentarEnSegundos()).isEqualTo(42);
        elContadorDice(seDioPor(42_001));
        assertThat(politica.decidir(MURO, CUENTA).reintentarEnSegundos()).isEqualTo(43);
        elContadorDice(seDioPor(0));
        assertThat(politica.decidir(MURO, CUENTA).reintentarEnSegundos()).as("nunca 0").isEqualTo(1);
        elContadorDice(seDioPor(1));
        DecisionDeLimite decision = politica.decidir(MURO, CUENTA);

        assertThat(decision.rechaza()).isTrue();
        assertThat(decision.reintentarEnSegundos()).isEqualTo(1);
        verify(metricas, org.mockito.Mockito.times(4)).pedido(MURO, ResultadoDelLimite.BLOQUEADO);
        verify(anotador, never()).anotar(any(), any());
    }

    // spec 050 FR-016
    @Test
    @DisplayName("con margen, el pedido pasa, se cuenta como permitido y se mide qué parte del tope se usó")
    void conMargenPasa() {
        DecisionDeLimite decision = politica.decidir(MURO, CUENTA);

        assertThat(decision.accion()).isEqualTo(Accion.PASAR);
        verify(metricas).pedido(MURO, ResultadoDelLimite.PERMITIDO);
        verify(metricas).uso(MURO, "1m", 0.3);
        verifyNoInteractions(anotador);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("DESACTIVADO, el filtro apagado y un grupo sin topes no cuentan nada: ni tocan el contador")
    void loDesactivadoNoCuenta() {
        topes.modo = ModoDeLimite.DESACTIVADO;
        assertThat(politica.decidir(MURO, CUENTA).accion()).isEqualTo(Accion.PASAR);

        topes.modo = ModoDeLimite.APLICAR;
        topes.habilitado = false;
        assertThat(politica.decidir(MURO, CUENTA).accion()).isEqualTo(Accion.PASAR);

        topes.habilitado = true;
        topes.ventanas = List.of();
        assertThat(politica.decidir(MURO, CUENTA).accion()).isEqualTo(Accion.PASAR);

        assertThat(llamadasAlContador).hasValue(0);
        verifyNoInteractions(metricas);
    }

    // spec 050 FR-017
    @Test
    @DisplayName("si el contador falla, el pedido pasa y se cuenta como error_de_redis: nunca una excepción")
    void siRedisFallaElPedidoPasa() {
        topes.modo = ModoDeLimite.APLICAR;
        contador = (grupo, sujeto, ventanas) -> {
            throw new IllegalStateException("Redis no responde");
        };

        DecisionDeLimite decision = politica.decidir(MURO, CUENTA);

        assertThat(decision.accion()).isEqualTo(Accion.PASAR);
        verify(metricas).pedido(MURO, ResultadoDelLimite.ERROR_DE_REDIS);
        verify(metricas, never()).pedido(MURO, ResultadoDelLimite.BLOQUEADO);
    }

    // spec 050 FR-017
    @Test
    @DisplayName("si no se puede anotar quién habría bloqueado, el pedido igual pasa")
    void siElRegistroDurableFallaElPedidoPasa() {
        topes.modo = ModoDeLimite.REGISTRAR;
        elContadorDice(seDioPor(5_000));
        doThrow(new IllegalStateException("Redis no responde")).when(anotador).anotar(any(), any());

        assertThat(politica.decidir(MURO, CUENTA).accion()).isEqualTo(Accion.PASAR_Y_ANOTAR);
        verify(metricas).pedido(MURO, ResultadoDelLimite.HABRIA_BLOQUEADO);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("el modo de Redis manda sobre el de la configuración y se relee cada cache-del-modo, sin reiniciar")
    void elModoSeCambiaSinReiniciar() {
        topes.modo = ModoDeLimite.REGISTRAR;
        elContadorDice(seDioPor(5_000));
        assertThat(politica.decidir(MURO, CUENTA).rechaza()).isFalse();

        enRedis.put(MURO, ModoDeLimite.APLICAR);
        reloj.avanzar(Duration.ofSeconds(29));
        assertThat(politica.decidir(MURO, CUENTA).rechaza()).as("antes de los 30 s sigue el modo que ya leyó").isFalse();
        assertThat(lecturasDelModo).hasValue(1);

        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(politica.decidir(MURO, CUENTA).rechaza()).as("pasados los 30 s ya ve el nuevo").isTrue();
        assertThat(lecturasDelModo).hasValue(2);
        assertThat(politica.modoDe(GrupoDeLimite.CHAT)).as("los otros grupos siguen con el de la configuración")
                .isEqualTo(ModoDeLimite.REGISTRAR);

        enRedis.remove(MURO);
        reloj.avanzar(Duration.ofSeconds(31));
        assertThat(politica.modoDe(MURO)).as("quitar el valor de Redis devuelve al de la configuración").isEqualTo(ModoDeLimite.REGISTRAR);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("si Redis no deja leer el modo, sigue con el último que supo; y si nunca supo, con el de la configuración")
    void sinRedisSeSigueConLoUltimoQueSePudoLeer() {
        topes.modo = ModoDeLimite.REGISTRAR;
        redisLeEstaFallando = true;
        assertThat(politica.modoDe(MURO)).isEqualTo(ModoDeLimite.REGISTRAR);

        redisLeEstaFallando = false;
        enRedis.put(MURO, ModoDeLimite.APLICAR);
        reloj.avanzar(Duration.ofSeconds(31));
        assertThat(politica.modoDe(MURO)).isEqualTo(ModoDeLimite.APLICAR);

        redisLeEstaFallando = true;
        reloj.avanzar(Duration.ofSeconds(31));
        assertThat(politica.modoDe(MURO)).as("Redis falla: se queda con APLICAR").isEqualTo(ModoDeLimite.APLICAR);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("si el reloj retrocede, el modo se relee en vez de quedar congelado hasta que el reloj alcance el instante viejo")
    void unRelojQueRetrocedeObligaARelear() {
        enRedis.put(MURO, ModoDeLimite.APLICAR);
        reloj.avanzar(Duration.ofDays(400));
        assertThat(politica.modoDe(MURO)).isEqualTo(ModoDeLimite.APLICAR);

        enRedis.clear();
        reloj.avanzar(Duration.ofDays(-400));

        assertThat(politica.modoDe(MURO)).isEqualTo(ModoDeLimite.REGISTRAR);
        assertThat(lecturasDelModo).hasValue(2);
    }

    private static final class TopesDePrueba implements TopesDeLimites {

        boolean habilitado = true;
        ModoDeLimite modo = ModoDeLimite.REGISTRAR;
        List<Ventana> ventanas = VENTANAS;

        @Override
        public boolean habilitado() {
            return habilitado;
        }

        @Override
        public Duration cacheDelModo() {
            return Duration.ofSeconds(30);
        }

        @Override
        public ModoDeLimite modoPorDefecto(GrupoDeLimite grupo) {
            return modo;
        }

        @Override
        public List<Ventana> ventanasDe(GrupoDeLimite grupo) {
            return ventanas;
        }
    }

    private static final class RelojManual implements Clock {

        private Instant ahora = Instant.parse("2026-11-12T03:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
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
}
