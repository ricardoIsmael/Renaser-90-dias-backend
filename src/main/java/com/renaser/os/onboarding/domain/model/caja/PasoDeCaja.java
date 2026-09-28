package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Un paso de la caja, tal como queda en {@code etapas_onboarding_completadas} (V82): quién, cuándo, en qué
 * envío y con qué datos ({@code detalle}).
 *
 * @param envio      1 para la primera caja; un reenvío después de un problema es el 2, y así
 * @param marcadaPor {@code null} si se borró la cuenta de quien lo marcó
 * @param detalle    los datos del paso, como texto (las claves están abajo); nunca {@code null}
 */
public record PasoDeCaja(UserId aprendizId, int envio, TipoPasoCaja tipo, Instant en, UserId marcadaPor,
                         Map<String, String> detalle) {

    public static final String PREFIJO_FLUJO = "caja:";

    public static final String MEDIO = "medio";
    public static final String COURIER = "courier";
    public static final String CODIGO = "codigo";
    public static final String COSTO = "costo";
    public static final String MEDIA_ID = "media_id";
    public static final String RUTA = "ruta";
    public static final String FOTO_MEDIA_ID = "foto_media_id";
    public static final String FOTO_RUTA = "foto_ruta";
    public static final String COMPROBANTE_MEDIA_ID = "comprobante_media_id";
    public static final String COMPROBANTE_RUTA = "comprobante_ruta";
    public static final String MOTIVO = "motivo";
    public static final String NOTA = "nota";
    public static final String PREVIA = "previa";

    public PasoDeCaja {
        Objects.requireNonNull(aprendizId, "aprendizId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(en, "en");
        if (envio < 1) {
            throw new IllegalArgumentException("El envío empieza en 1: " + envio);
        }
        detalle = sinVacios(detalle);
    }

    /** {@code caja:<envío>:<paso>}: la clave de la fila. */
    public String flujo() {
        return PREFIJO_FLUJO + envio + ":" + tipo.name();
    }

    public Optional<String> dato(String clave) {
        return Optional.ofNullable(detalle.get(clave));
    }

    /**
     * Reconstruye un paso desde su fila. Vacío si el flujo no es de la caja o es de un paso que este código
     * no conoce (una fila de una versión más nueva): se ignora en vez de romper la lectura.
     */
    public static Optional<PasoDeCaja> desdeFila(UserId aprendizId, String flujo, Instant en, UserId marcadaPor,
                                                 Map<String, String> detalle) {
        if (flujo == null || !flujo.startsWith(PREFIJO_FLUJO)) {
            return Optional.empty();
        }
        String[] partes = flujo.substring(PREFIJO_FLUJO.length()).split(":", 2);
        if (partes.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PasoDeCaja(aprendizId, Integer.parseInt(partes[0]),
                    TipoPasoCaja.valueOf(partes[1]), en, marcadaPor, detalle));
        } catch (IllegalArgumentException desconocido) {
            return Optional.empty();
        }
    }

    private static Map<String, String> sinVacios(Map<String, String> detalle) {
        Map<String, String> limpio = new LinkedHashMap<>();
        if (detalle != null) {
            detalle.forEach((clave, valor) -> {
                if (clave != null && valor != null && !valor.isBlank()) {
                    limpio.put(clave, valor);
                }
            });
        }
        return Map.copyOf(limpio);
    }
}
