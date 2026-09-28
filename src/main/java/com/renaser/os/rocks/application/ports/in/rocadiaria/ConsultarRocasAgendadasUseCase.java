package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Las acciones del dia ya agendadas de hoy en adelante, hasta el ultimo dia que hoy se puede
 * planificar (D-217). Existe para las alarmas del telefono: {@code /today} y {@code /tomorrow} solo
 * cubren dos dias, y desde E-208 se agenda hasta el domingo, asi que una accion del viernes agendada
 * el martes no tenia alarma hasta que la persona abria la app el jueves.
 */
public interface ConsultarRocasAgendadasUseCase {

    RocasAgendadas agendadas(UserId actorId);

    /**
     * @param desde hoy, en la zona de la persona
     * @param hasta inclusivo: el domingo de su semana de programa (el domingo, el lunes; en la 13, el dia
     *              90). Mañana si todavia no eligio su Dia 1; hoy si ya paso el dia 90
     * @param rocas las de esos dias, ordenadas por fecha y posicion. La app usa {@code desde}/{@code hasta}
     *              para saber que dias representa la lista aunque alguno venga vacio
     */
    record RocasAgendadas(LocalDate desde, LocalDate hasta, List<RocaDiaria> rocas) {
    }
}
