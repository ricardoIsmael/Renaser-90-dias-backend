package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Genera los registros PENDIENTE de un participante para una fecha, a partir
 * del catalogo (habitos SISTEMA activos) + sus habitos PERSONAL activos, cuyo
 * {@code HorarioHabito.aplicaEnDia(diaPrograma, tipoDia)} de cada uno de estos
 * habitos aplica ese dia. Desde D-200 la pregunta la contesta
 * {@code HorariosDelHabito}: un habito que ya corrio y quedo por debajo del inicio de su
 * horario (retroceso de dia) se sigue generando, como en el primer dia de ese horario.
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

    /**
     * Variante para el dia en curso (la red de seguridad de {@code GET /habit-tracks/today} y el barrido horario que
     * llega tarde): genera el dia COMPLETO de hoy en SU zona, salvo en su primer dia del programa
     * ({@code fecha_inicio}), en que descarta los habitos cuya ventana ya se cerro a esta hora. Un habito sin
     * {@code horaLimite} no vence en el dia, asi que siempre entra.
     *
     * <p>Por que el primer dia es distinto: alguien que activa su programa a las 11 de la manana (hoy, el staff con
     * {@code activarSeguimientoPersonal}) no hizo la ducha fria que cerraba a las 08:00 — no existia para el.
     * Decision del dueno del proyecto (2026-09-02): esos habitos no se generan ese primer dia parcial.
     *
     * <p><b>Corregido 2026-10-06 (D-259).</b> Este javadoc decia que el corte por hora valia para cualquier dia. Con la
     * regla del dueño del 2026-10-06 («un habito se puede registrar durante su dia aunque se le haya pasado la hora:
     * vencer la hora solo afecta los puntos») un dia armado tarde no puede perder habitos: la persona que los hizo
     * tiene que poder anotarlos, con 0 puntos.
     */
    List<RegistroHabito> generarDisponiblesAhora(UserId participanteId);

    /**
     * Variante para el barrido nocturno ({@code GenerarTracksDelDiaScheduler}): genera la
     * jornada COMPLETA (sin filtro de hora — el dia todavia no empezo para el participante)
     * para la fecha de HOY en SU zona horaria, no la del servidor ni una fecha fija que el
     * llamador tenga que calcular.
     *
     * <p>Por que resuelve la zona aca adentro y no en el scheduler: el mismo lookup de
     * {@code ConsultarProgresoParticipanteHabitsPort.deParticipante} que da la zona ya lo
     * hace internamente esta implementacion para validar pertenencia/suspension — pedirla
     * tambien desde el scheduler antes de llamar aca seria una consulta de mas por
     * participante sin necesidad. El puerto de listado en lote
     * ({@code participantesInscritosActivos}) a proposito NO expone la zona, para no tentar
     * a otro llamador a hacer ese N+1 (ver javadoc del puerto).
     */
    List<RegistroHabito> generarDiaCompletoEnSuZona(UserId participanteId);
}
