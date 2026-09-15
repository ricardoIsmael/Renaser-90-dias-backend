package com.renaser.os.habits.application.ports.out.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadRenombreHabitoPort {

    Optional<RenombreHabito> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);

    /**
     * Todos los renombres de un participante, para resolver la agenda del dia sin una consulta por
     * habito (2026-09-15, D-133). Son como mucho dos filas —solo el jugo verde y el agua con
     * limon se pueden reemplazar—, pero la consulta va igual en lote: el resto de la proyeccion
     * del dia ya lo hace asi, y una query por registro es justamente lo que costo el incidente de
     * conexiones agotadas de D-43.
     */
    List<RenombreHabito> deParticipante(UserId participanteId);
}
