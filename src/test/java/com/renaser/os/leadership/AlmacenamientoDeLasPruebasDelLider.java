package com.renaser.os.leadership;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * El almacenamiento de las pruebas de integración del líder (spec 056): uno en memoria que guarda de verdad, anota cada subida y firma un enlace
 * que dice cuánto vence ({@code ?vence=600}). El de las pruebas (noop) no guarda nada que después se pueda leer, y la spec 056 tiene que comprobar
 * qué dice el documento que se subió.
 *
 * <p><b>Vive en el escenario común ({@link EscenarioDelLiderIT}) y no en una prueba suelta</b> para no partir el caché de contextos de Spring
 * (E-649): un {@code @TestConfiguration} propio de una sola clase es un contexto más, con su Postgres, su Redis y su pool, y la suite ya anda
 * justa de memoria. Así todas las pruebas del líder siguen compartiendo UN contexto. Ninguna otra prueba lo ve.
 */
@TestConfiguration(proxyBeanMethods = false)
class AlmacenamientoDeLasPruebasDelLider {

    @Bean
    @Primary
    AlmacenamientoDelLiderEnMemoria almacenamientoDelLiderEnMemoria() {
        return new AlmacenamientoDelLiderEnMemoria();
    }

    /** Guarda de verdad, en memoria, y firma un enlace que dice cuánto vence. */
    static class AlmacenamientoDelLiderEnMemoria implements AlmacenamientoPort {
        final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
        final List<String> subidas = new CopyOnWriteArrayList<>();
        final Map<String, String> tipos = new ConcurrentHashMap<>();

        void vaciar() {
            objetos.clear();
            subidas.clear();
            tipos.clear();
        }

        @Override
        public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI firmarLectura(String ruta, Duration validez) {
            return URI.create("https://almacen.test/" + ruta + "?vence=" + validez.toSeconds());
        }

        @Override
        public URI urlPublica(String ruta) {
            throw new UnsupportedOperationException("un documento privado nunca tiene enlace público");
        }

        @Override
        public void subir(String ruta, byte[] contenido, String tipoContenido) {
            objetos.put(ruta, contenido);
            subidas.add(ruta);
            tipos.put(ruta, tipoContenido);
        }

        @Override
        public void borrar(String ruta) {
            objetos.remove(ruta);
        }

        @Override
        public Optional<byte[]> leer(String ruta, long pesoMaximo) {
            return Optional.ofNullable(objetos.get(ruta));
        }
    }
}
