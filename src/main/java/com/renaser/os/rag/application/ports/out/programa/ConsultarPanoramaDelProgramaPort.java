package com.renaser.os.rag.application.ports.out.programa;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que la herramienta {@code consultar_resumen_del_programa} suma al dia y la fase: la zona
 * horaria de la persona, su coherencia de la semana y su proximo evento (2026-09-23).
 *
 * <p><b>Por que no alcanza con {@code ConsultarSituacionDelAprendizPort}.</b> Ese puerto da el
 * dia y la fase, y se reusa tal cual. Pero no trae la zona, y sin la zona no hay "que hora es
 * para ti" ni "tu evento es manana" correctos (regla 02 §1: entre las 00:00 y las 05:00 UTC la
 * fecha del servidor ya es la del dia siguiente en Lima).
 *
 * <p>Son los mismos datos que muestra {@code GET /home} ({@code HomeAgregadoService}), leidos
 * por los mismos contratos publicos ({@code users.api.ParticipacionProgramaFinder},
 * {@code points.api.PorcentajeRocasFinder}, {@code points.api.ProximoEventoFinder}). Puerto
 * propio de {@code rag} con tipos propios, mismo criterio que {@code ConsultarAgendaHabitosPort}.
 */
public interface ConsultarPanoramaDelProgramaPort {

    /**
     * @param ahora el instante de la consulta; se recibe en vez de leer un reloj propio para que
     *              la fecha local con la que se pide la coherencia sea la MISMA que la herramienta
     *              le muestra a la persona
     * @return vacio solo si la persona no existe en {@code users}
     */
    Optional<Panorama> de(UserId participanteId, Instant ahora);

    /**
     * @param zona          zona horaria del participante, nunca {@code null}
     * @param coherencia    porcentaje de acciones diarias cumplidas sobre las planificadas en los
     *                      ultimos 7 dias, con un decimal. Vacio si no planifico ninguna: no es un
     *                      cero ni un cien, es que no hay dato (D-128)
     * @param proximoEvento el evento futuro mas cercano visible para la persona, si hay
     */
    record Panorama(ZoneId zona, Optional<BigDecimal> coherencia, Optional<ProximoEvento> proximoEvento) {

        public Panorama {
            Objects.requireNonNull(zona, "zona es obligatoria");
            Objects.requireNonNull(coherencia, "coherencia es obligatoria (vacia si no hay dato)");
            Objects.requireNonNull(proximoEvento, "proximoEvento es obligatorio (vacio si no hay)");
        }
    }

    record ProximoEvento(String titulo, Instant iniciaEn) {

        public ProximoEvento {
            Objects.requireNonNull(titulo, "titulo es obligatorio");
            Objects.requireNonNull(iniciaEn, "iniciaEn es obligatorio");
        }
    }
}
