package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Genera los registros PENDIENTE de un participante para una fecha, a partir
 * del catalogo (habitos SISTEMA activos) + sus habitos PERSONAL activos, cuyo
 * {@code HorarioHabito.aplicaEnDia(diaPrograma, tipoDia)} de cada uno de estos
 * habitos aplica ese dia.
 *
 * <p>Simplificacion deliberada de esta primera version (ver docs/MODULO_HABITS.md
 * "que quedo simplificado"): NO incluye el escalonamiento por lotes
 * (`habitStaggering.ts`), NI el filtro de "eleccion de dia semanal"
 * (`weeklyChoice.ts`) — un habito con {@code eleccionDiaSemanal=true} genera
 * track TODOS los dias que apliquen su horario en este caso de uso, en vez de
 * solo el dia elegido por el aprendiz. Documentado como deuda explicita, no
 * como comportamiento final.
 */
public interface GenerarTracksDelDiaUseCase {

    List<RegistroHabito> generar(UserId participanteId, LocalDate fecha);
}
