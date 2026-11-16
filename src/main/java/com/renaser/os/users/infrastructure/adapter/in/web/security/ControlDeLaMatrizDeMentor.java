package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.DenegacionDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * El interruptor y el registro de la matriz estricta de MENTOR y TRAINEE_GUIDE (spec 050, tramo H, FR-041): en qué modo está
 * ({@link ModoDeLaMatriz#SOMBRA} o {@link ModoDeLaMatriz#APLICAR}) y dónde queda lo que negó o habría negado.
 *
 * <p><b>El modo.</b> Rige el de la configuración ({@code renaser.security.mentor-enforcement}, {@code false} = sombra) salvo que haya
 * uno fijado en Redis (la clave que escribe {@code scripts/despliegue/permisos-modo.sh}), que manda sobre aquella: así el dueño lo
 * cambia, o vuelve atrás, <b>sin reiniciar</b> (un reinicio son 1–2 minutos de 502). Se relee cada {@link #CADA_LECTURA}, en el
 * primer pedido que pasa después de ese tiempo (sin hilo aparte); si Redis no responde se conserva lo último que se supo. Con Redis
 * caído la sesión tampoco se lee y la API no atiende, así que no es una dependencia nueva.
 *
 * <p><b>El registro.</b> Una línea estructurada en el log y un contador por día en Redis (sobrevive a los despliegues). Ninguno lleva
 * la identidad de la cuenta ni un token: el rol, el permiso y el endpoint alcanzan para corregir la matriz. Es auxiliar: si falla,
 * el pedido sigue.
 *
 * <p>Los colaboradores llegan envueltos en {@link ObjectProvider} por la misma razón que {@code UserSummaryFinder} en el interceptor:
 * un {@code @WebMvcTest} de otro módulo no los tiene, y entonces rige la configuración y no se anota en Redis.
 */
final class ControlDeLaMatrizDeMentor {

    static final Duration CADA_LECTURA = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(ControlDeLaMatrizDeMentor.class);

    private final ModoDeLaMatriz porConfiguracion;
    private final ObjectProvider<ModoDeLaMatrizFijadoPort> modoFijado;
    private final ObjectProvider<AnotarDenegacionDeLaMatrizPort> anotador;
    private final long cadaLecturaEnNanos;
    private final LongSupplier relojMonotono;
    private final AtomicLong proximaLectura;
    private volatile ModoDeLaMatriz fijadoEnRedis;

    ControlDeLaMatrizDeMentor(boolean aplicarPorConfiguracion, ObjectProvider<ModoDeLaMatrizFijadoPort> modoFijado,
                              ObjectProvider<AnotarDenegacionDeLaMatrizPort> anotador, Duration cadaLectura) {
        this(aplicarPorConfiguracion, modoFijado, anotador, cadaLectura, System::nanoTime);
    }

    /** El reloj es monotónico (solo mide cuánto pasó desde la última lectura) y entra por aquí para probar el intervalo. */
    ControlDeLaMatrizDeMentor(boolean aplicarPorConfiguracion, ObjectProvider<ModoDeLaMatrizFijadoPort> modoFijado,
                              ObjectProvider<AnotarDenegacionDeLaMatrizPort> anotador, Duration cadaLectura,
                              LongSupplier relojMonotono) {
        this.porConfiguracion = aplicarPorConfiguracion ? ModoDeLaMatriz.APLICAR : ModoDeLaMatriz.SOMBRA;
        this.modoFijado = modoFijado;
        this.anotador = anotador;
        this.cadaLecturaEnNanos = cadaLectura.toNanos();
        this.relojMonotono = relojMonotono;
        this.proximaLectura = new AtomicLong(relojMonotono.getAsLong());
    }

    /** Sin Redis ni registro: solo el modo de la configuración. Para los contextos reducidos y las pruebas que no lo ejercen. */
    static ControlDeLaMatrizDeMentor soloConfiguracion(boolean aplicar) {
        StaticListableBeanFactory vacia = new StaticListableBeanFactory();
        return new ControlDeLaMatrizDeMentor(aplicar, vacia.getBeanProvider(ModoDeLaMatrizFijadoPort.class),
                vacia.getBeanProvider(AnotarDenegacionDeLaMatrizPort.class), CADA_LECTURA);
    }

    /** El modo vigente ahora: el de Redis si hay uno, y si no el de la configuración. */
    ModoDeLaMatriz modo() {
        releerSiToca();
        ModoDeLaMatriz fijado = fijadoEnRedis;
        return fijado != null ? fijado : porConfiguracion;
    }

    /** Anota lo que la matriz negó (o habría negado): la línea del log y el contador del día. Nunca lanza. */
    void anotar(DenegacionDeLaMatriz denegacion, String handler) {
        log.warn("matriz-estricta {} rol={} permiso={} metodo={} ruta={} handler={}",
                denegacion.estado(), denegacion.rol(), denegacion.permiso(), denegacion.metodo(), denegacion.ruta(), handler);
        try {
            AnotarDenegacionDeLaMatrizPort puerto = anotador.getIfAvailable();
            if (puerto != null) {
                puerto.anotar(denegacion);
            }
        } catch (RuntimeException e) {
            log.warn("matriz-estricta: no se pudo anotar la denegación en Redis; queda solo la línea del log", e);
        }
    }

    /** Solo un pedido relee (el que gana la carrera por el turno): los demás siguen con lo último que se supo. */
    private void releerSiToca() {
        long ahora = relojMonotono.getAsLong();
        long turno = proximaLectura.get();
        if (ahora - turno >= 0 && proximaLectura.compareAndSet(turno, ahora + cadaLecturaEnNanos)) {
            releer();
        }
    }

    private void releer() {
        try {
            ModoDeLaMatrizFijadoPort puerto = modoFijado.getIfAvailable();
            if (puerto != null) {
                Optional<ModoDeLaMatriz> fijado = puerto.fijado();
                fijadoEnRedis = fijado.orElse(null);
            }
        } catch (RuntimeException e) {
            log.warn("matriz-estricta: no se pudo leer el modo de Redis; se conserva el último ({})",
                    fijadoEnRedis == null ? "el de la configuración" : fijadoEnRedis, e);
        }
    }
}
