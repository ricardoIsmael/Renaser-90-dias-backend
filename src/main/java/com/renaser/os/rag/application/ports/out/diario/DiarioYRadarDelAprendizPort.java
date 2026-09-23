package com.renaser.os.rag.application.ports.out.diario;

import com.renaser.os.habits.api.DiarioYRadarPort;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Puerto propio de {@code rag} para leer y escribir la Bitacora Nocturna de hoy y el Codigo
 * Renaser del aprendiz (herramientas {@code consultar_bitacora_de_hoy},
 * {@code proponer_bitacora_de_hoy}, {@code consultar_ultimo_radar} y
 * {@code proponer_check_in_radar}, 2026-09-23).
 *
 * <p>Las tablas son de {@code habits}: el adaptador delega en {@code habits.api.DiarioYRadarPort}
 * (D-41). Mismo criterio que {@code GestionarPlanDeHabitosPort}: records propios. Los limites del
 * radar se toman de aquel contrato y no se copian los numeros: la regla vive en {@code habits}.
 *
 * <p><b>Todo el texto que pasa por aca es personal.</b> Nunca se loguea.
 *
 * <p>Las escrituras SOLO las llaman las {@code AccionConfirmable}, cuando la persona toca
 * "Confirmar". Lanzan las excepciones del negocio; traducirlas a un texto legible es de quien llama.
 */
public interface DiarioYRadarDelAprendizPort {

    int RADAR_TEXTO_MAXIMO = DiarioYRadarPort.RADAR_TEXTO_MAXIMO;
    int RADAR_ENERGIA_MINIMA = DiarioYRadarPort.RADAR_ENERGIA_MINIMA;
    int RADAR_ENERGIA_MAXIMA = DiarioYRadarPort.RADAR_ENERGIA_MAXIMA;

    /** @throws RuntimeException si no hay participacion o la cuenta esta suspendida */
    BitacoraDeHoy bitacoraDeHoy(UserId participanteId);

    /** Upsert: si ya habia bitacora hoy, su texto se reemplaza (el audio se conserva). */
    BitacoraDeHoy escribirBitacoraDeHoy(UserId actorId, String texto);

    /** @throws RuntimeException si no hay participacion, esta suspendida o no le corresponde el radar */
    Optional<CheckInRadar> ultimoCheckInRadar(UserId participanteId);

    CheckInRadarRegistrado registrarCheckInRadar(UserId actorId, RespuestasRadar respuestas);

    /** @param fecha "hoy" en la zona del participante, resuelto por {@code habits} */
    record BitacoraDeHoy(LocalDate fecha, boolean existe, String texto, boolean tieneAudio) {
    }

    /**
     * @param registradoEn hora local del participante
     * @param deEstaHora   ya ocupa la franja de la hora en curso (uno por hora)
     */
    record CheckInRadar(LocalDateTime registradoEn, boolean deEstaHora, RespuestasRadar respuestas) {
    }

    record RespuestasRadar(String queHago, String quePienso, String queSiento, int nivelEnergia, String queEvito) {
    }

    /** @param yaExistia habia uno en esta hora: {@code habits} devolvio ese y no guardo las respuestas */
    record CheckInRadarRegistrado(LocalDateTime registradoEn, boolean yaExistia) {
    }
}
