package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Spec 048 (FR-007, FR-013, fase 3): arma el {@link HechoSinRed} de las tres cabeceras que manda la app con lo hecho sin red.
 * Es el único lugar que las lee, para las ~25 escrituras de Plan, Cierra tu día, Radar y planificar hábitos: el controller
 * declara un parámetro {@code HechoSinRed sinRed} y se lo pasa al caso de uso.
 *
 * <ul>
 *   <li>{@code X-Id-Cliente}: el UUID del envío. {@code X-Hecho-En}: instante ISO-8601 (la hora real).
 *       {@code X-Origen-De-La-Hora}: {@code RELOJ_DEL_SISTEMA} o {@code TELEFONO_CORREGIDO} (otro valor se lee como el segundo).</li>
 *   <li>Falta alguna de las dos primeras (o viene en blanco): el pedido es uno normal y el parámetro llega {@code null}.
 *       Un servidor o una app anteriores ignoran las cabeceras, y sin ellas cada endpoint responde exactamente lo de hoy.</li>
 *   <li>{@code X-Id-Cliente} que no es un UUID, o {@code X-Hecho-En} que no es un instante: 400 de siempre.</li>
 * </ul>
 */
@Component
public class HechoSinRedArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String CABECERA_ID_CLIENTE = "X-Id-Cliente";
    public static final String CABECERA_HECHO_EN = "X-Hecho-En";
    public static final String CABECERA_ORIGEN_DE_LA_HORA = "X-Origen-De-La-Hora";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return HechoSinRed.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return request == null ? null : leer(request);
    }

    /** Público para los tests: sin las dos cabeceras, {@code null}. */
    public static HechoSinRed leer(HttpServletRequest request) {
        String id = request.getHeader(CABECERA_ID_CLIENTE);
        String hechoEn = request.getHeader(CABECERA_HECHO_EN);
        if (esVacia(id) || esVacia(hechoEn)) {
            return null;
        }
        return new HechoSinRed(instante(hechoEn.trim()), uuid(id.trim()),
                OrigenDeLaHora.leer(request.getHeader(CABECERA_ORIGEN_DE_LA_HORA)));
    }

    private static boolean esVacia(String valor) {
        return valor == null || valor.isBlank();
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException malo) {
            throw new IllegalArgumentException("El valor de '" + CABECERA_ID_CLIENTE + "' no tiene el formato esperado");
        }
    }

    private static Instant instante(String valor) {
        try {
            return Instant.parse(valor);
        } catch (DateTimeParseException malo) {
            throw new IllegalArgumentException("El valor de '" + CABECERA_HECHO_EN + "' no tiene el formato esperado");
        }
    }
}
