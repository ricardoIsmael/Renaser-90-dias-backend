package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.DenegacionDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El interruptor de la matriz de MENTOR y TRAINEE_GUIDE (spec 050, tramo H, FR-041): rige la configuración salvo que el dueño haya
 * fijado un modo en Redis, que se relee cada tanto sin reiniciar, y si Redis falla se conserva lo último que se supo. El tiempo
 * entra por un reloj de mentira para probar el intervalo sin esperarlo.
 */
class ControlDeLaMatrizDeMentorTest {

    private static final Duration CADA_30_S = Duration.ofSeconds(30);

    private final ModoDeLaMatrizFijadoPort modoFijado = mock(ModoDeLaMatrizFijadoPort.class);
    private final AnotarDenegacionDeLaMatrizPort anotador = mock(AnotarDenegacionDeLaMatrizPort.class);
    private final AtomicLong nanos = new AtomicLong(1_000);

    private ControlDeLaMatrizDeMentor control(boolean aplicarPorConfiguracion) {
        return new ControlDeLaMatrizDeMentor(aplicarPorConfiguracion, proveedor(ModoDeLaMatrizFijadoPort.class, modoFijado),
                proveedor(AnotarDenegacionDeLaMatrizPort.class, anotador), CADA_30_S, nanos::get);
    }

    private static <T> ObjectProvider<T> proveedor(Class<T> tipo, T bean) {
        StaticListableBeanFactory fabrica = new StaticListableBeanFactory();
        fabrica.addBean("el-unico", bean);
        return fabrica.getBeanProvider(tipo);
    }

    private static DenegacionDeLaMatriz denegacion(ModoDeLaMatriz modo) {
        return new DenegacionDeLaMatriz(modo, UserRole.MENTOR, Permission.MODERATE_WALL, "GET", "/api/v1/admin/x/{id}");
    }

    private void pasaron(Duration tiempo) {
        nanos.addAndGet(tiempo.toNanos());
    }

    // spec 050 FR-041
    @Test
    @DisplayName("sin nada en Redis rige la configuración: false es SOMBRA y true es APLICAR")
    void sinNadaEnRedisRigeLaConfiguracion() {
        when(modoFijado.fijado()).thenReturn(Optional.empty());

        assertThat(control(false).modo()).isEqualTo(ModoDeLaMatriz.SOMBRA);
        assertThat(control(true).modo()).isEqualTo(ModoDeLaMatriz.APLICAR);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("lo fijado en Redis manda sobre la configuración, en los dos sentidos, desde el primer pedido")
    void redisMandaSobreLaConfiguracion() {
        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.APLICAR));
        assertThat(control(false).modo()).as("sombra por configuración, aplicar por Redis").isEqualTo(ModoDeLaMatriz.APLICAR);

        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.SOMBRA));
        assertThat(control(true).modo()).as("aplicar por configuración, sombra por Redis (la vuelta atrás sin reiniciar)")
                .isEqualTo(ModoDeLaMatriz.SOMBRA);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("se relee cada 30 s y no antes: un cambio en Redis se ve sin reiniciar, y quitar la clave devuelve a la configuración")
    void seReleeCadaTreintaSegundos() {
        ControlDeLaMatrizDeMentor control = control(false);
        when(modoFijado.fijado()).thenReturn(Optional.empty());
        assertThat(control.modo()).isEqualTo(ModoDeLaMatriz.SOMBRA);

        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.APLICAR));
        pasaron(Duration.ofSeconds(29));
        assertThat(control.modo()).as("a los 29 s todavía no se leyó de nuevo").isEqualTo(ModoDeLaMatriz.SOMBRA);
        pasaron(Duration.ofSeconds(2));
        assertThat(control.modo()).as("a los 31 s sí").isEqualTo(ModoDeLaMatriz.APLICAR);

        when(modoFijado.fijado()).thenReturn(Optional.empty());
        pasaron(CADA_30_S.plusSeconds(1));
        assertThat(control.modo()).as("sin la clave, de vuelta a la configuración").isEqualTo(ModoDeLaMatriz.SOMBRA);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("si Redis falla se conserva lo último que se supo, y si nunca se supo nada rige la configuración")
    void siRedisFallaSeConservaLoUltimo() {
        ControlDeLaMatrizDeMentor control = control(false);
        doThrow(new IllegalStateException("Redis no responde")).when(modoFijado).fijado();
        assertThat(control.modo()).as("nunca se supo nada: la configuración").isEqualTo(ModoDeLaMatriz.SOMBRA);

        doReturn(Optional.of(ModoDeLaMatriz.APLICAR)).when(modoFijado).fijado();
        pasaron(CADA_30_S.plusSeconds(1));
        assertThat(control.modo()).isEqualTo(ModoDeLaMatriz.APLICAR);

        doThrow(new IllegalStateException("Redis no responde")).when(modoFijado).fijado();
        pasaron(CADA_30_S.plusSeconds(1));
        assertThat(control.modo()).as("Redis cayó: sigue lo último que se supo, no se vuelve a sombra por accidente")
                .isEqualTo(ModoDeLaMatriz.APLICAR);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("con el intervalo en cero se relee en cada pedido (lo usan las pruebas de integración)")
    void conIntervaloCeroSeReleeSiempre() {
        ControlDeLaMatrizDeMentor control = new ControlDeLaMatrizDeMentor(false, proveedor(ModoDeLaMatrizFijadoPort.class, modoFijado),
                proveedor(AnotarDenegacionDeLaMatrizPort.class, anotador), Duration.ZERO, nanos::get);
        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.APLICAR));
        assertThat(control.modo()).isEqualTo(ModoDeLaMatriz.APLICAR);

        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.SOMBRA));
        assertThat(control.modo()).isEqualTo(ModoDeLaMatriz.SOMBRA);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("muchos pedidos a la vez leen Redis una sola vez por intervalo")
    void unaSolaLecturaPorIntervalo() throws Exception {
        ControlDeLaMatrizDeMentor control = control(false);
        AtomicInteger lecturas = new AtomicInteger();
        when(modoFijado.fijado()).thenAnswer(invocacion -> {
            lecturas.incrementAndGet();
            return Optional.of(ModoDeLaMatriz.APLICAR);
        });
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<ModoDeLaMatriz>> modos = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Callable<ModoDeLaMatriz> pedido = () -> {
                    salida.await();
                    return control.modo();
                };
                modos.add(hilos.submit(pedido));
            }
            salida.countDown();
            for (Future<ModoDeLaMatriz> modo : modos) {
                modo.get(10, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(lecturas.get()).as("el reloj no se movió: una sola lectura").isEqualTo(1);
        assertThat(control.modo()).isEqualTo(ModoDeLaMatriz.APLICAR);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("anotar manda la denegación al puerto, y si el puerto falla (Redis) el pedido no se entera")
    void anotarNuncaLanza() {
        ControlDeLaMatrizDeMentor control = control(false);
        DenegacionDeLaMatriz denegacion = denegacion(ModoDeLaMatriz.SOMBRA);

        control.anotar(denegacion, "ControladorX#metodo");
        verify(anotador).anotar(denegacion);

        doThrow(new IllegalStateException("Redis no responde")).when(anotador).anotar(any());
        assertThatCode(() -> control.anotar(denegacion, "ControladorX#metodo")).doesNotThrowAnyException();
    }

    // spec 050 FR-041
    @Test
    @DisplayName("sin Redis en el contexto (un @WebMvcTest de otro módulo) no se rompe nada: rige la configuración y no se anota")
    void sinColaboradoresRigeLaConfiguracion() {
        ControlDeLaMatrizDeMentor sombra = ControlDeLaMatrizDeMentor.soloConfiguracion(false);
        ControlDeLaMatrizDeMentor aplicar = ControlDeLaMatrizDeMentor.soloConfiguracion(true);

        assertThat(sombra.modo()).isEqualTo(ModoDeLaMatriz.SOMBRA);
        assertThat(aplicar.modo()).isEqualTo(ModoDeLaMatriz.APLICAR);
        assertThatCode(() -> sombra.anotar(denegacion(ModoDeLaMatriz.SOMBRA), "X#y")).doesNotThrowAnyException();
        verify(anotador, never()).anotar(any());
    }

    // spec 050 FR-041
    @Test
    @DisplayName("el modo se escribe a mano: SOMBRA y APLICAR en cualquier caso y con espacios; un error de tipeo no es un modo")
    void interpretarElValorEscritoAMano() {
        assertThat(ModoDeLaMatriz.interpretar("SOMBRA")).contains(ModoDeLaMatriz.SOMBRA);
        assertThat(ModoDeLaMatriz.interpretar("  aplicar\n")).contains(ModoDeLaMatriz.APLICAR);
        assertThat(ModoDeLaMatriz.interpretar("APLICA")).isEmpty();
        assertThat(ModoDeLaMatriz.interpretar("")).isEmpty();
        assertThat(ModoDeLaMatriz.interpretar(null)).isEmpty();
    }
}
