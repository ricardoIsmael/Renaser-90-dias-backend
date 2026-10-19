package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.util.Optional;

/**
 * A qué grupo del catálogo pertenece el mensaje de hoy (spec 006, research R3). Gana el primero que se cumple:
 * <ol>
 *   <li>{@code CUENTA_PAUSADA}: la ventana tiene días con la cuenta suspendida (D-209) y ninguno medido; es quien
 *       acaba de volver.</li>
 *   <li>{@code GRADUADO}: ya pasó el día 90.</li>
 *   <li>{@code PAUSA}: rige una pausa que pidió la persona.</li>
 *   <li>Sin color: {@code INICIO} si está antes del Día 1 o en la primera semana; {@code SIN_PROGRAMA} después.</li>
 *   <li>El color: {@code VERDE}, {@code AMARILLO} o {@code ROJO}.</li>
 * </ol>
 * Los casos 1 a 3 mandan aunque la ventana todavía tenga color: el mensaje habla de lo que le pasa hoy a la persona,
 * y mostrarle «sigue así» a quien terminó el programa o está en pausa estaría fuera de lugar.
 */
public final class GrupoDelDia {

    private static final int ULTIMO_DIA_DE_INICIO = 7;

    private GrupoDelDia() {
    }

    public static GrupoDelMensaje de(SituacionDelSemaforo situacion) {
        if (vuelveDeUnaSuspension(situacion)) {
            return GrupoDelMensaje.CUENTA_PAUSADA;
        }
        if (situacion.programaTerminado()) {
            return GrupoDelMensaje.GRADUADO;
        }
        if (situacion.pausado()) {
            return GrupoDelMensaje.PAUSA;
        }
        return switch (situacion.color()) {
            case VERDE -> GrupoDelMensaje.VERDE;
            case AMARILLO -> GrupoDelMensaje.AMARILLO;
            case ROJO -> GrupoDelMensaje.ROJO;
            case SIN_DATOS -> situacion.diaPrograma() <= ULTIMO_DIA_DE_INICIO
                    ? GrupoDelMensaje.INICIO : GrupoDelMensaje.SIN_PROGRAMA;
        };
    }

    /**
     * El grupo de un día que ya tiene color (D-281: la tarjeta del chat conoce el color de su día y no la situación
     * completa de la persona). Vacío para {@code SIN_DATOS}: sin color no hay grupo de color.
     */
    public static Optional<GrupoDelMensaje> delColor(ColorSemaforo color) {
        return switch (color) {
            case VERDE -> Optional.of(GrupoDelMensaje.VERDE);
            case AMARILLO -> Optional.of(GrupoDelMensaje.AMARILLO);
            case ROJO -> Optional.of(GrupoDelMensaje.ROJO);
            case SIN_DATOS -> Optional.empty();
        };
    }

    private static boolean vuelveDeUnaSuspension(SituacionDelSemaforo situacion) {
        boolean conSuspension = situacion.ventana().stream()
                .anyMatch(dia -> dia.estado() == EstadoDiaSemaforo.CUENTA_SUSPENDIDA);
        boolean conMedidos = situacion.ventana().stream().anyMatch(dia -> dia.estado() == EstadoDiaSemaforo.MEDIDO);
        return conSuspension && !conMedidos;
    }
}
