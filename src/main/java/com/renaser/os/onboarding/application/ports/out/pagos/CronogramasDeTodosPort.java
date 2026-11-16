package com.renaser.os.onboarding.application.ports.out.pagos;

import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Los cronogramas de muchas personas a la vez (D-338), para la lectura de Administración que trae las cuotas de todos. Cada método es una consulta para todas
 * las personas, nunca una por persona.
 */
public interface CronogramasDeTodosPort {

    /**
     * Las personas «con cronograma», ordenadas por su id (un orden estable para paginar). Si el filtro recorta cuotas, solo las que tienen al menos una cuota que lo
     * cumple; sin filtro, también las que todavía no tienen cuotas.
     */
    List<UserId> personasConCronograma(FiltroDeCuotas filtro);

    /** El pago y TODAS las cuotas de cada una (sin filtrar: la confirmación se calcula sobre el cronograma entero). Las que no tienen fila no aparecen. */
    Map<UserId, CronogramaGuardado> deLasPersonas(Collection<UserId> personas);

    /** Cada campo {@code null} = sin ese recorte. Las fechas son del calendario e inclusivas. */
    record FiltroDeCuotas(LocalDate desde, LocalDate hasta, EstadoDeCuota estado) {

        public boolean recorta() {
            return desde != null || hasta != null || estado != null;
        }

        public boolean cumple(Cuota cuota) {
            return (desde == null || !cuota.venceEl().isBefore(desde))
                    && (hasta == null || !cuota.venceEl().isAfter(hasta))
                    && (estado == null || cuota.estado() == estado);
        }
    }

    /** @param cuotas de la fecha más cercana a la más lejana */
    record CronogramaGuardado(PagoDeLaPersona pago, List<Cuota> cuotas) {
    }
}
