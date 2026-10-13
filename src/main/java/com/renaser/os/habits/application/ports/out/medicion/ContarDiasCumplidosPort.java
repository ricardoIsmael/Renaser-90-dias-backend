package com.renaser.os.habits.application.ports.out.medicion;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Cuántos días cumplió cada participante un hábito del catálogo, desde su primer registro hasta {@code hasta}
 * inclusive (D-266: los días de gimnasio del programa). Un día = un registro COMPLETADO: la UNIQUE
 * {@code (participante_id, habito_id, fecha_ejecucion)} de {@code registros_habito} impide contar dos veces el mismo.
 *
 * <p><b>En lote</b> (D-43): una sola consulta para todo el padrón. Quien no cumplió ninguno no aparece en el mapa.
 */
public interface ContarDiasCumplidosPort {

    Map<UserId, Integer> diasPorParticipante(Collection<UserId> participantes, String claveSistema, LocalDate hasta);
}
