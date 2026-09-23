package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Las practicas de foco del dia vistas desde otro modulo: el audio de Espiritu, el Santuario y el
 * Dia sin celular (2026-09-23).
 *
 * <p>Primer consumidor: las herramientas {@code consultar_espiritu_de_hoy},
 * {@code proponer_resumen_espiritu}, {@code proponer_iniciar_santuario} y
 * {@code proponer_iniciar_dia_sin_celular} del acompanante de {@code rag}. Se expone aca por la
 * regla de siempre (D-41, regla 01): {@code rag} no lee {@code registros_espiritu},
 * {@code sesiones_bloqueo} ni {@code rachas_sin_celular} por su cuenta, y cada escritura pasa por
 * el MISMO caso de uso que la app ({@code EntregarResumenEspirituUseCase},
 * {@code IniciarSesionBloqueoUseCase}, {@code IniciarRachaUseCase}), con todas sus guardas.
 *
 * <p>Solo INICIA el Santuario y la racha. Completarlos pide evidencia o una declaracion de
 * honestidad que vive en la app, y romperlos no es algo que un acompanante deba ofrecer: esas
 * operaciones no estan en este contrato a proposito.
 *
 * <p>Ningun tipo de {@code habits.domain} cruza esta frontera. Las escrituras lanzan las mismas
 * excepciones que esos casos de uso ({@code IllegalStateException}, {@code IllegalArgumentException},
 * {@code NoSuchElementException}, {@code NotAuthorizedException}): traducirlas a un texto es del
 * llamador. "Hoy" siempre se resuelve puertas adentro, en la zona del participante (E-91).
 */
public interface EnfoqueDiarioPort {

    /**
     * El Espiritu de hoy. <b>No es una lectura pura:</b> pasa por {@code ConsultarEstadoEspirituUseCase},
     * el mismo de {@code GET /spirit-audio/status}, que avanza la maquina de estados perezosa
     * (desbloquea el audio del dia, da por perdido el de un dia ya cerrado). Es exactamente lo que
     * ocurre cuando la persona abre Training, e idempotente: repetirlo no cambia nada.
     *
     * @throws java.util.NoSuchElementException si no tiene participacion en el programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si esta suspendida o no cursa el programa
     */
    EspirituDeHoy espirituDeHoy(UserId participanteId);

    /**
     * Entrega el resumen del audio {@code diaAudio}. Delega en {@code EntregarResumenEspirituUseCase}:
     * ademas completa el habito "Pastilla Renacer" de hoy, que otorga sus puntos.
     *
     * @return {@code true} si fue a tiempo (antes de la hora limite)
     */
    boolean entregarResumenEspiritu(UserId actorId, int diaAudio, String resumen);

    /** Los habitos de Santuario (tipo BLOQUEO) de hoy. Lista vacia si no tiene ninguno. */
    List<SantuarioDeHoy> santuariosDeHoy(UserId participanteId);

    /** Inicia una sesion de Santuario. Delega en {@code IniciarSesionBloqueoUseCase}. */
    void iniciarSantuario(UserId actorId, UUID registroId);

    DiaSinCelularDeHoy diaSinCelularDeHoy(UserId participanteId);

    /** Inicia una racha sin celular. Delega en {@code IniciarRachaUseCase}. */
    void iniciarDiaSinCelular(UserId actorId, UUID registroId, int horasObjetivo);

    /** Como esta el Espiritu de hoy, en el vocabulario de quien pregunta. */
    enum EstadoEspirituDeHoy {
        /** Todavia no son las {@code horaDesbloqueo} en su zona: el audio de hoy (si le toca) no se abrio. */
        ANTES_DE_LA_HORA_DE_DESBLOQUEO,
        /** Hoy no tiene audio desbloqueado (no le toca, o el catalogo no tiene ese dia). */
        SIN_AUDIO_HOY,
        PENDIENTE,
        ENTREGADO_A_TIEMPO,
        /** Lo mando despues de la hora limite: quedo guardado, pero no cuenta como a tiempo. */
        ENTREGADO_FUERA_DE_PLAZO,
        PERDIDO
    }

    /**
     * @param hoy                   el dia de hoy en la zona del participante
     * @param horaDesbloqueo        hora local desde la que se abre el audio del dia
     * @param horaLimite            hora local limite de entrega a tiempo
     * @param audio                 el audio de hoy, o {@code null} si hoy no hay ninguno desbloqueado
     * @param puntosPastillaRenacer lo que paga ahora el habito "Pastilla Renacer" de hoy, que se
     *                              completa al entregar; {@code null} si no aplica (no esta pendiente,
     *                              no lo tiene hoy, o el audio no esta pendiente)
     */
    record EspirituDeHoy(ZoneId zona, LocalDate hoy, LocalTime horaDesbloqueo, LocalTime horaLimite,
                         EstadoEspirituDeHoy estado, AudioDeHoy audio, Integer puntosPastillaRenacer) {
    }

    /**
     * @param dia         el dia de AUDIO (no el del programa): el que pide la entrega
     * @param fechaLimite hasta cuando la entrega cuenta como a tiempo
     * @param entregadoEn cuando lo mando, o {@code null}
     */
    record AudioDeHoy(int dia, String titulo, Instant fechaLimite, Instant entregadoEn) {
    }

    /**
     * @param zona           la zona del participante, para decir {@code iniciableDesde} en su hora
     * @param iniciable      el registro esta pendiente y todavia no tiene sesion
     * @param iniciableDesde desde cuando {@code habits} deja iniciarlo, o {@code null} si el habito
     *                       no tiene franja horaria (se puede en cualquier momento)
     */
    record SantuarioDeHoy(ZoneId zona, UUID registroId, String titulo, boolean iniciable, Instant iniciableDesde) {
    }

    /**
     * @param zona                la zona del participante
     * @param registroId          el registro de hoy del habito "Dia sin celular", o {@code null} si hoy no lo tiene
     * @param iniciable           tiene registro pendiente hoy y ninguna racha en curso
     * @param rachaEnCursoDesde   inicio de la racha ACTIVA (a lo sumo una), o {@code null}
     * @param rachaEnCursoHoras   la meta de esa racha, o {@code null}
     * @param metasValidas        las metas en horas que acepta {@code IniciarRachaUseCase}
     */
    record DiaSinCelularDeHoy(ZoneId zona, UUID registroId, String titulo, boolean iniciable,
                              Instant rachaEnCursoDesde, Integer rachaEnCursoHoras, List<Integer> metasValidas) {

        public DiaSinCelularDeHoy {
            metasValidas = metasValidas == null ? List.of() : List.copyOf(metasValidas);
        }
    }
}
