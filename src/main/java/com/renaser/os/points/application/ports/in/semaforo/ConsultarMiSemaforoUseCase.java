package com.renaser.os.points.application.ports.in.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** El semáforo de la propia persona: {@code GET /api/v1/me/semaforo} y la tarjeta de Hoy. */
public interface ConsultarMiSemaforoUseCase {

    /** Autoconsulta: exige cuenta activa. {@code semanas} se acota a 1..13. */
    DetalleDelSemaforo consultar(UserId actorId, int semanas);

    /** Lo mínimo para la tarjeta de Hoy; vacío si la persona no se mide. */
    Optional<ResumenParaHoy> resumenParaHoy(UserId actorId);

    /**
     * @param porcentaje null si la ventana vigente no tuvo días con datos
     * @param pausado    si hoy rige una pausa (las pausas empiezan siempre el mismo día que se piden)
     * @param dias       los 7 días de la ventana vigente: la tarjeta de Hoy los dibuja sin pedir el detalle
     */
    record ResumenParaHoy(ColorSemaforo color, BigDecimal porcentaje, int diasConDatos, boolean pausado,
                          List<DiaDelSemaforo> dias) {
    }
}
