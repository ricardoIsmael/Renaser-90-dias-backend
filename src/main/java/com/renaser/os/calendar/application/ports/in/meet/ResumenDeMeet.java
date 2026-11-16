package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;

/**
 * Cómo van las salas de Meet de un evento, para quien administra el calendario (spec 040, FR-019). Es el peor estado de sus fechas: una sola con
 * problema hace que el evento diga {@code FALLIDO}.
 *
 * @param acceso            lo que Administración eligió para el evento
 * @param accesoAplicado    Google ya dejó todas las salas como se eligió
 * @param mensaje           una frase en español para la persona, o {@code null} si todo está bien
 * @param fechasConSala     cuántas fechas tienen su sala
 * @param fechasConProblema cuántas no
 * @param avisoDelPlan      si Google no dejó grabar, transcribir o tomar notas porque la cuenta conectada no tiene el plan, una frase que lo dice
 *                          (FR-037); {@code null} si no hay nada que avisar. No es un error: el evento y la sala están bien.
 * @param coanfitriones     cómo quedó hacer coanfitrión a quien dirige la sesión (FR-038), el peor de las fechas: {@code OK}, {@code PARCIAL},
 *                          {@code FALLIDO}, {@code SIN_CORREO}; {@code null} si no se pidió (opción apagada o todavía no)
 * @param avisoDeCoanfitriones una frase en español si {@code coanfitriones} no es {@code OK}: sin coanfitrión Meet puede no grabar
 */
public record ResumenDeMeet(EstadoSalaMeet estado, AccesoSalaMeet acceso, boolean accesoAplicado, String mensaje,
                            int fechasConSala, int fechasConProblema, String avisoDelPlan, String coanfitriones,
                            String avisoDeCoanfitriones) {

    /** Sin datos de coanfitriones. */
    public ResumenDeMeet(EstadoSalaMeet estado, AccesoSalaMeet acceso, boolean accesoAplicado, String mensaje, int fechasConSala,
                         int fechasConProblema, String avisoDelPlan) {
        this(estado, acceso, accesoAplicado, mensaje, fechasConSala, fechasConProblema, avisoDelPlan, null, null);
    }

    /** Sin aviso del plan. */
    public ResumenDeMeet(EstadoSalaMeet estado, AccesoSalaMeet acceso, boolean accesoAplicado, String mensaje, int fechasConSala,
                         int fechasConProblema) {
        this(estado, acceso, accesoAplicado, mensaje, fechasConSala, fechasConProblema, null);
    }
}
