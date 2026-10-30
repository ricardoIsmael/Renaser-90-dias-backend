package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * "Las acciones del día de esta persona entre estas fechas, con la evidencia con la que se cumplieron": lo que la ficha
 * del líder suma a cada día (spec 005, R-5). Una acción es lo que la app llama «Tus acciones»: la roca diaria.
 *
 * <p><b>Delega, no reimplementa.</b> Lee las mismas rocas que sirven {@code GET /rocks/upcoming} y el dashboard, y
 * pregunta a {@code evidence} por la evidencia de cada una. Una roca se completa SOLO entregando evidencia (R-02),
 * así que {@code completada} sin {@code evidenciaId} no pasa en datos reales; la ficha lo trata igual como «Ya no está
 * disponible» si el archivo se borró.
 *
 * <p><b>No autoriza</b>, igual que {@code RocasDelAprendizFinder}: quien llama ya estableció que el actor puede ver a
 * esa persona. Responde por el id que le dan.
 */
public interface AccionesDelDiaFinder {

    /**
     * @param desde inclusivo, fecha local de la persona ya resuelta por quien llama
     * @param hasta inclusivo
     * @return ordenadas por fecha y posición; vacía si no hay nada, nunca null
     */
    List<AccionDelDia> accionesEntre(UserId aprendizId, LocalDate desde, LocalDate hasta);

    /**
     * @param id          el de {@code rocas_diarias}
     * @param evidenciaId la PRIMERA evidencia de la acción; null si no tiene
     * @param horaInicio  spec 018: la hora local en que la persona agendó la acción; null si no la agendó. Es lo que
     *                    cuenta el «¿Programó?» de la ficha del líder
     */
    record AccionDelDia(UUID id, LocalDate fecha, String titulo, boolean completada, UUID evidenciaId,
                        LocalTime horaInicio) {

        /** Sin la hora: la forma de antes de la spec 018. */
        public AccionDelDia(UUID id, LocalDate fecha, String titulo, boolean completada, UUID evidenciaId) {
            this(id, fecha, titulo, completada, evidenciaId, null);
        }

        /** Si la persona le puso hora a la acción (la «agendó»). */
        public boolean agendada() {
            return horaInicio != null;
        }
    }
}
