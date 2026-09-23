package com.renaser.os.rag.application.ports.out.enfoque;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para las practicas de foco del dia: Espiritu, Santuario y Dia sin
 * celular (herramientas {@code consultar_espiritu_de_hoy}, {@code proponer_resumen_espiritu},
 * {@code proponer_iniciar_santuario} y {@code proponer_iniciar_dia_sin_celular}, 2026-09-23).
 *
 * <p>Las tablas son de {@code habits}: el adaptador delega en {@code habits.api.EnfoqueDiarioPort}
 * (D-41). Mismo criterio que {@code GestionarPlanDeHabitosPort}: records propios, el contrato
 * ajeno no llega a {@code rag.application}.
 *
 * <p>Las escrituras SOLO las llaman las {@code AccionConfirmable}, cuando la persona toca
 * "Confirmar". Lanzan las excepciones del negocio; traducirlas a un texto es de quien llama.
 */
public interface EnfoqueDiarioDelAprendizPort {

    /** Ojo: avanza la maquina perezosa de Espiritu, igual que abrir Training en la app (idempotente). */
    EspirituDeHoy espirituDeHoy(UserId participanteId);

    /** @return {@code true} si la entrega fue a tiempo */
    boolean entregarResumenEspiritu(UserId actorId, int diaAudio, String resumen);

    List<SantuarioDeHoy> santuariosDeHoy(UserId participanteId);

    void iniciarSantuario(UserId actorId, UUID registroId);

    DiaSinCelularDeHoy diaSinCelularDeHoy(UserId participanteId);

    void iniciarDiaSinCelular(UserId actorId, UUID registroId, int horasObjetivo);

    enum EstadoEspiritu {
        ANTES_DE_LA_HORA_DE_DESBLOQUEO,
        SIN_AUDIO_HOY,
        PENDIENTE,
        ENTREGADO_A_TIEMPO,
        ENTREGADO_FUERA_DE_PLAZO,
        PERDIDO
    }

    /**
     * @param audio                 el de hoy, o {@code null}
     * @param puntosPastillaRenacer lo que paga ahora "Pastilla Renacer" de hoy, o {@code null}
     */
    record EspirituDeHoy(ZoneId zona, LocalDate hoy, LocalTime horaDesbloqueo, LocalTime horaLimite,
                         EstadoEspiritu estado, AudioDeHoy audio, Integer puntosPastillaRenacer) {
    }

    /** @param dia el dia de AUDIO, el que pide la entrega */
    record AudioDeHoy(int dia, String titulo, Instant fechaLimite, Instant entregadoEn) {
    }

    /** @param iniciableDesde {@code null} si el habito no tiene franja horaria */
    record SantuarioDeHoy(ZoneId zona, UUID registroId, String titulo, boolean iniciable, Instant iniciableDesde) {
    }

    /** @param registroId {@code null} si hoy no tiene el habito "Dia sin celular" */
    record DiaSinCelularDeHoy(ZoneId zona, UUID registroId, String titulo, boolean iniciable,
                              Instant rachaEnCursoDesde, Integer rachaEnCursoHoras, List<Integer> metasValidas) {
    }
}
