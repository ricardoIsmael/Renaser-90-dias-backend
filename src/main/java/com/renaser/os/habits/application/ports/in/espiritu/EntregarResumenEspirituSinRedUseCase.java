package com.renaser.os.habits.application.ports.in.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (issue #186, FR-008, FR-010, FR-015): el resumen de la Pastilla Renacer que la persona entregó SIN RED, con la hora real en
 * que lo hizo. Las mismas reglas de {@link EntregarResumenEspirituUseCase} (plazo del mediodía, tardía, día que ya cerró) corridas con
 * esa hora y no con la de llegada: la entrega de las 11:00 que llega a las 13:00 cuenta a tiempo. Un mismo {@code idCliente} se
 * registra una sola vez y un reintento devuelve lo mismo (FR-008). Sin cabeceras el pedido no pasa por aquí.
 *
 * <p>Va aparte de {@link EntregarResumenEspirituUseCase} a propósito: quien ya lo llama (la app con red, SER) no cambia.
 */
public interface EntregarResumenEspirituSinRedUseCase {

    /**
     * @return si la entrega cuenta a tiempo y, si el hábito «Pastilla Renacer» quedó sin puntos, por qué (D14: nada rebota)
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException {@code RECURSO_YA_NO_EXISTE} (ese día no está desbloqueado),
     *         {@code FUERA_DE_SU_DIA} (la hora real no cae en el día de ese audio: el día ya cerró), {@code YA_ESTABA_HECHO}
     *         (ese día ya estaba entregado) o {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR} (se entregó desde otro lado después de lo
     *         que hiciste)
     */
    ResultadoEntregaSinRed entregar(EntregarResumenEspirituCommand command, HechoSinRed sinRed);

    /** @param motivoSinPuntos {@code HORA_FUTURA} o {@code LLEGO_DESPUES_DE_48_H}; nulo si el hábito sumó sus puntos */
    record ResultadoEntregaSinRed(boolean aTiempo, String motivoSinPuntos) {
    }
}
