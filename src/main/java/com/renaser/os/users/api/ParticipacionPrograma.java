package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Proyeccion PUBLICA de `participantes_programa` + `usuarios` — el contrato que
 * `points`, `phasecontracts`, `habits`, `rocks`, `calendar` y `community` deberian usar
 * en vez de su propia query nativa contra la BD ajena (CLAUDE.MD: "expongo un endpoint,
 * la forma senior, no quiero exponer la BD de frente"). Ninguno de esos 6 modulos fue
 * refactorizado todavia para consumir esto — lo hace el dueño del repo en otra sesion,
 * una vez que los agentes en paralelo terminen.
 *
 * <p><b>{@code inscrito=false} es un caso legitimo y frecuente, NUNCA {@code Optional.empty()}.</b>
 * El programa de 90 dias es obligatorio solo para APRENDIZ — opcional para el resto de
 * roles (comentario de `participantes_programa` en el baseline). {@link ParticipacionProgramaFinder#deParticipante}
 * devuelve {@code Optional.empty()} SOLO cuando el {@link UserId} no existe en `usuarios`;
 * un usuario real sin fila de programa aparece igual, con {@code inscrito=false} y
 * defaults seguros. Ya causo un bug real cuando `calendar` hacia INNER JOIN: un ADMIN sin
 * fila de participante desaparecia entero y el endpoint devolvia 404.
 *
 * <p>{@code fechaInicio}/{@code celulaId}/{@code mentorId} son {@code null} cuando
 * {@code inscrito=false} — no tienen un default seguro que no sea enganoso. {@code
 * diaPrograma} (0), {@code zona} ('America/Lima', el mismo default de la columna) y
 * {@code fase} (fase inicial) si tienen default seguro y nunca son null: los consumidores
 * que ya los usan sin chequear {@code inscrito} (ver `ConsultarProgresoParticipanteAcademyPort`)
 * no revientan.
 *
 * @param participanteId id del usuario (= `usuarios.id` = `participantes_programa.usuario_id`)
 * @param inscrito       si existe fila en `participantes_programa`
 * @param diaPrograma    `participantes_programa.dia_programa`, 0 si no inscrito
 * @param fechaInicio    `participantes_programa.fecha_inicio`, null si no inscrito
 * @param zona           `participantes_programa.timezone`, 'America/Lima' si no inscrito
 * @param fase           `participantes_programa.fase`, fase inicial si no inscrito
 * @param celulaId       `participantes_programa.celula_id`, null si no inscrito o sin celula
 * @param mentorId       `participantes_programa.mentor_id`, null si no inscrito o sin mentor asignado
 * @param rol            `usuarios.rol` — existe para CUALQUIER usuario, inscrito o no
 * @param suspendido     `usuarios.estado = 'SUSPENDIDO'` — idem
 */
public record ParticipacionPrograma(
        UserId participanteId,
        boolean inscrito,
        int diaPrograma,
        LocalDate fechaInicio,
        ZoneId zona,
        FasePrograma fase,
        UUID celulaId,
        UserId mentorId,
        UserRole rol,
        boolean suspendido) {
}
