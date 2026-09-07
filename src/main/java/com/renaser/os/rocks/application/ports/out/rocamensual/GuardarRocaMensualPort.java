package com.renaser.os.rocks.application.ports.out.rocamensual;

import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;

/**
 * Persiste una Roca Mensual, sea recien definida o corregida.
 *
 * <p>Separado de {@code LoadRocaMensualPort} por el mismo motivo que en la Roca Maestra: quien
 * solo consulta (el dashboard, la lista del plan) no tiene por que recibir la capacidad de
 * escribir.
 */
public interface GuardarRocaMensualPort {

    RocaMensual guardar(RocaMensual rocaMensual);
}
