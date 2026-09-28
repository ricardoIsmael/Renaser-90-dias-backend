package com.renaser.os.onboarding.domain.model.caja;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Por dónde salió la caja (spec §6). Olva y Shalom no tienen una API abierta: el Admin escribe el medio y
 * el código (obligatorio también en inDrive: la placa o el número de pedido) y la persona confirma. Si el
 * courier es Olva o Shalom, la app abre su página oficial de rastreo.
 *
 * @param medio   por dónde se envió, texto libre (obligatorio)
 * @param courier la empresa, si la hay
 * @param codigo  el código para seguirla (obligatorio)
 * @param costo   lo que costó el envío, en soles; opcional
 */
public record DatosDelEnvio(String medio, String courier, String codigo, BigDecimal costo) {

    public static final String RASTREO_OLVA = "https://tracking.olvaexpress.pe/";
    public static final String RASTREO_SHALOM = "https://shalom.com.pe/rastrea";
    private static final BigDecimal COSTO_MAXIMO = BigDecimal.valueOf(100_000);

    /** @throws IllegalArgumentException si falta el medio o el código, o algo no tiene sentido (400) */
    public static DatosDelEnvio de(String medio, String courier, String codigo, BigDecimal costo) {
        if (costo != null && (costo.signum() < 0 || costo.compareTo(COSTO_MAXIMO) >= 0)) {
            throw new IllegalArgumentException("El costo tiene que ser un monto entre 0 y " + COSTO_MAXIMO + ".");
        }
        return new DatosDelEnvio(TextoCorto.obligatorio(medio, 80, "El medio de envío"),
                TextoCorto.opcional(courier, 80, "El courier"), TextoCorto.obligatorio(codigo, 120, "El código"),
                costo);
    }

    /** Reconstruye los datos desde el detalle del paso {@code ENVIADA}, sin volver a validar. */
    public static DatosDelEnvio desde(PasoDeCaja enviada) {
        BigDecimal costo = enviada.dato(PasoDeCaja.COSTO).map(BigDecimal::new).orElse(null);
        return new DatosDelEnvio(enviada.dato(PasoDeCaja.MEDIO).orElse(null),
                enviada.dato(PasoDeCaja.COURIER).orElse(null), enviada.dato(PasoDeCaja.CODIGO).orElse(null), costo);
    }

    /** La página oficial de rastreo si el envío es por Olva o por Shalom; si no, vacío. */
    public Optional<String> rastreoUrl() {
        String donde = TextoCorto.normalizado(courier) + " " + TextoCorto.normalizado(medio);
        if (donde.contains("olva")) {
            return Optional.of(RASTREO_OLVA);
        }
        return donde.contains("shalom") ? Optional.of(RASTREO_SHALOM) : Optional.empty();
    }

    public Map<String, String> comoDetalle() {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put(PasoDeCaja.MEDIO, medio);
        detalle.put(PasoDeCaja.COURIER, courier);
        detalle.put(PasoDeCaja.CODIGO, codigo);
        detalle.put(PasoDeCaja.COSTO, costo == null ? null : costo.toPlainString());
        return detalle;
    }
}
