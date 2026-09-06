package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Traduce lo que lanza el SDK de Google a lo unico que el resto del sistema sabe manejar:
 * {@link ProveedorIaNoDisponibleException} cuando el fallo es del proveedor y transitorio, y la
 * excepcion original en cualquier otro caso.
 *
 * <p><b>Por que hace falta (auditoria NFR 2026-09-06).</b> Spring AI trae un
 * {@code RetryUtils.DEFAULT_RETRY_TEMPLATE} que solo reintenta {@code TransientAiException}, y el
 * modulo de Google GenAI <b>no traduce</b> las excepciones de su SDK a ese tipo: un 429 llega como
 * {@code com.google.genai.errors.ClientException}, que no es transitoria para Spring AI, asi que
 * ese template nunca se activa. El error del SDK subia crudo hasta {@code GlobalExceptionHandler},
 * que no lo conocia, y salia como <b>500</b>. Y los 500 el cliente los reintenta enseguida.
 *
 * <p>Se busca la {@code ApiException} en toda la cadena de causas y no solo en el primer nivel:
 * el modelo de embeddings de Spring AI envuelve la llamada al SDK y no esta garantizado que la
 * excepcion salga desnuda.
 *
 * <p>Los tiempos de espera son deliberadamente distintos: una cuota agotada no vuelve en
 * segundos (por eso 60 s), un 5xx o un timeout del proveedor suele ser cosa de un momento (10 s).
 * Son el valor del {@code Retry-After} que ve el cliente; el codigo, siempre 503.
 */
final class TraduccionErroresGoogleGenAi {

    static final Duration ESPERA_TRAS_CUOTA = Duration.ofSeconds(60);
    static final Duration ESPERA_TRAS_FALLO_DEL_PROVEEDOR = Duration.ofSeconds(10);

    static final String MENSAJE_CUOTA =
            "El asistente alcanzo su limite de uso por ahora. Intenta de nuevo en unos minutos.";
    static final String MENSAJE_PROVEEDOR =
            "El asistente no esta disponible en este momento. Intenta de nuevo en unos segundos.";

    private static final int HTTP_REQUEST_TIMEOUT = 408;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int PRIMER_CODIGO_DE_SERVIDOR = 500;

    private TraduccionErroresGoogleGenAi() {
    }

    /**
     * La excepcion de dominio equivalente si el fallo es del proveedor y transitorio; si no, la
     * misma excepcion que llego (un 400 de Google es un bug NUESTRO en la solicitud, y tiene que
     * seguir sonando como tal en el log, no disfrazarse de "proveedor caido").
     */
    static RuntimeException traducir(Throwable error) {
        ApiException api = apiExceptionEn(error);
        if (api != null) {
            return traducirCodigo(api.code(), error);
        }
        if (esFalloDeRed(error)) {
            return new ProveedorIaNoDisponibleException(MENSAJE_PROVEEDOR, ESPERA_TRAS_FALLO_DEL_PROVEEDOR);
        }
        return error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
    }

    private static RuntimeException traducirCodigo(int codigo, Throwable original) {
        if (codigo == HTTP_TOO_MANY_REQUESTS) {
            return new ProveedorIaNoDisponibleException(MENSAJE_CUOTA, ESPERA_TRAS_CUOTA);
        }
        if (codigo == HTTP_REQUEST_TIMEOUT || codigo >= PRIMER_CODIGO_DE_SERVIDOR) {
            return new ProveedorIaNoDisponibleException(MENSAJE_PROVEEDOR, ESPERA_TRAS_FALLO_DEL_PROVEEDOR);
        }
        return original instanceof RuntimeException runtime ? runtime : new IllegalStateException(original);
    }

    private static ApiException apiExceptionEn(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ApiException api) {
                return api;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return null;
    }

    /**
     * Timeout o conexion rechazada hacia Google: tambien es "el proveedor no esta", no un bug.
     * {@code GenAiIOException} es como el SDK envuelve sus fallos de E/S (incluido el timeout que
     * ahora esta configurado en {@code GoogleGenAiClientesConfig}); se contempla ademas la
     * excepcion de red desnuda por si llega sin envolver.
     */
    private static boolean esFalloDeRed(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof GenAiIOException || causa instanceof SocketTimeoutException
                    || causa instanceof InterruptedIOException || causa instanceof ConnectException) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }
}
