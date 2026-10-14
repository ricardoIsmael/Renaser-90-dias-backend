package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;

/**
 * Si una funcionalidad está prendida para alguien y, si no, con qué aviso y por cuál nivel (ella misma o su madre).
 *
 * @param aviso        el aviso a mostrar; nunca vacío si no está prendida (si nadie escribió uno, el genérico)
 * @param apagadaEn    la clave del nivel que la apagó o la dejó fuera ({@code chat} cuando se pidió
 *                     {@code chat.grupos}), o {@code null} si está prendida
 * @param automatico   si la apagó el circuito de la IA y no una persona
 * @param noDisponible si no está apagada sino que a esta persona no le corresponde (fase 2: despliegue progresivo o rol
 *                     personalizado); la app la esconde en vez de mostrar el aviso de mantenimiento
 */
public record ResultadoDeEvaluacion(boolean prendida, String aviso, String apagadaEn, boolean automatico,
                                    boolean noDisponible) {

    private static final ResultadoDeEvaluacion PRENDIDA = new ResultadoDeEvaluacion(true, null, null, false, false);

    public static ResultadoDeEvaluacion sinApagar() {
        return PRENDIDA;
    }

    public static ResultadoDeEvaluacion apagada(String clave, String aviso, boolean automatico) {
        String avisoVisible = aviso == null || aviso.isBlank() ? FuncionalidadApagadaException.AVISO_GENERICO : aviso;
        return new ResultadoDeEvaluacion(false, avisoVisible, clave, automatico, false);
    }

    public static ResultadoDeEvaluacion noDisponible(String clave) {
        return new ResultadoDeEvaluacion(false, FuncionalidadNoDisponibleException.MENSAJE, clave, false, true);
    }
}
