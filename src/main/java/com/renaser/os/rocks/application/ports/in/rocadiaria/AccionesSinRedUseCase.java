package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;

import java.util.List;

/**
 * Spec 048 (FR-019, fase 3): agendar las acciones del día y marcar una acción hechas SIN RED, con la hora real en que se hicieron. Mismas
 * reglas que con red corridas con esa hora; la regla de choques de D13. Un mismo {@code idCliente} se registra una sola vez.
 */
public interface AccionesSinRedUseCase {

    /**
     * @return las acciones del día tal como quedaron (201, como con red)
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException {@code FUERA_DE_SU_DIA} (el día ya pasó al llegar, P2),
     *         {@code YA_ESTABA_HECHO} (el día en curso ya estaba armado) o {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR} (alguna acción
     *         de ese día cambió después de lo que hiciste)
     */
    List<RocaDiaria> agendar(CrearPlanDiarioCommand command, HechoSinRed sinRed);

    /**
     * Nada rebota (D14): lo que no está en regla se registra igual, sin puntos, y {@code motivoSinPuntos} lo dice.
     *
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException {@code RECURSO_YA_NO_EXISTE} si la acción ya no está
     *         (se reemplazó al replanificar el día)
     */
    AccionMarcada marcar(CompletarRocaDiariaCommand command, HechoSinRed sinRed);

    /** @param motivoSinPuntos nulo cuando sumó sus puntos; {@code YA_ESTABA_HECHO} si ya estaba cumplida (no se toca) */
    record AccionMarcada(RocaDiaria roca, MotivoSinPuntos motivoSinPuntos) {
    }
}
