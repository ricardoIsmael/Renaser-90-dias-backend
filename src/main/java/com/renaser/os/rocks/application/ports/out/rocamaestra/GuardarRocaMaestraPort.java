package com.renaser.os.rocks.application.ports.out.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;

/**
 * Persiste una Roca Maestra, sea recien definida o corregida.
 *
 * <p>Separado de {@code LoadRocaMaestraPort} a proposito: quien solo consulta (el dashboard,
 * la lista del plan) no tiene por que recibir la capacidad de escribir.
 */
public interface GuardarRocaMaestraPort {

    RocaMaestra guardar(RocaMaestra rocaMaestra);
}
