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

    /**
     * Variante para el dia en curso: genera SOLO los habitos que el aprendiz todavia puede
     * completar, descartando aquellos cuya ventana de entrega ya se cerro a esta hora (en SU
     * zona horaria, no la del servidor). Un habito sin {@code horaLimite} no vence en el dia,
     * asi que siempre entra.
     *
     * <p>Por que existe, y por que el filtro es por hora y no solo por fecha: alguien que
     * activa su programa a las 11 de la manana no puede hacer la ducha fria que cerraba a las
     * 08:00. Generarsela igual la dejaria PENDIENTE hasta la noche, cuando el barrido la marca
     * fallada — el aprendiz arrancaria su primer dia con hábitos perdidos que nunca tuvo forma
     * de completar, y perdiendo coherencia por ello. Decision del dueno del proyecto
     * (2026-09-02): esos habitos no se generan ese primer dia parcial.
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
