package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * Quien ya tiene su dia armado (E-556): con tener algun registro de esa fecha alcanza. En lote para que el barrido
 * horario no haga una consulta por persona.
 */
public interface ConsultarJornadasGeneradasPort {

    /** @return de esos participantes, los que tienen al menos un registro con esa {@code fecha_ejecucion}. */
    Set<UserId> conRegistrosEn(Collection<UserId> participantes, LocalDate fecha);
}
