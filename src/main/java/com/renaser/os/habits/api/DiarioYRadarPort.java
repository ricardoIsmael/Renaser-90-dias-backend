package com.renaser.os.habits.api;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * La Bitacora Nocturna de hoy y el Codigo Renaser del aprendiz vistos desde otro modulo
 * (2026-09-23).
 *
 * <p>Primer consumidor: las herramientas {@code consultar_bitacora_de_hoy},
 * {@code proponer_bitacora_de_hoy}, {@code consultar_ultimo_radar} y
 * {@code proponer_check_in_radar} del acompanante de {@code rag}. Se expone aca por la regla de
 * siempre (D-41, regla 01): {@code rag} no lee {@code entradas_diario} ni {@code registros_radar}
 * por su cuenta, y las escrituras pasan por los MISMOS casos de uso que
 * {@code PUT /api/v1/journal/today} y {@code POST /api/v1/radar}, con todas sus guardas (cuenta
 * suspendida, autoservicio, Codigo Renaser solo con el programa andando, uno por hora).
 *
 * <p>"Hoy" y la hora local se resuelven aca, en la zona del participante (regla 02, E-91): el
 * llamador nunca los calcula. Ningun tipo de {@code habits.domain} cruza esta frontera.
 *
 * <p><b>Todo lo que viaja aca es texto personal que escribio la persona.</b> Quien lo reciba no lo
 * loguea.
 *
 * <p>Las escrituras lanzan las mismas excepciones que esos casos de uso
 * ({@code NoSuchElementException} sin participacion, {@code NotAuthorizedException} si la cuenta
 * esta suspendida o, en el radar, si no le corresponde el Codigo Renaser,
 * {@code IllegalArgumentException} o {@code ConstraintViolationException} si el contenido no es
 * valido): traducirlas a un texto es del llamador.
 */
public interface DiarioYRadarPort {

    /** Largo maximo de cada respuesta del Codigo Renaser: el mismo que valida {@code habits}. */
    int RADAR_TEXTO_MAXIMO = RegistroRadar.TEXTO_MAX_LENGTH;
    int RADAR_ENERGIA_MINIMA = RegistroRadar.NIVEL_ENERGIA_MIN;
    int RADAR_ENERGIA_MAXIMA = RegistroRadar.NIVEL_ENERGIA_MAX;

    /** Delega en {@code ConsultarBitacoraNocturnaUseCase} (el de {@code GET /api/v1/journal/today}). */
    BitacoraDeHoy bitacoraDeHoy(UserId participanteId);

    /**
     * Upsert del texto de la bitacora de hoy: si ya habia una, su texto se REEMPLAZA (el audio que
     * tuviera se conserva). Delega en {@code EscribirBitacoraNocturnaUseCase}, el del PUT.
     */
    BitacoraDeHoy escribirBitacoraDeHoy(UserId actorId, String texto);

    /** Delega en {@code ConsultarUltimoRadarUseCase} (autoservicio: siempre sobre el propio actor). */
    Optional<CheckInRadar> ultimoCheckInRadar(UserId participanteId);

    /**
     * Delega en {@code RegistrarCheckInRadarUseCase}, el de {@code POST /api/v1/radar}. Si ya habia
     * uno en la hora en curso, el caso de uso devuelve ESE y no guarda estas respuestas:
     * {@link CheckInRadarRegistrado#yaExistia()} lo dice.
     */
    CheckInRadarRegistrado registrarCheckInRadar(UserId actorId, RespuestasRadar respuestas);

    /**
     * @param fecha      "hoy" en la zona del participante
     * @param texto      {@code null} si no existe o si solo tiene audio
     * @param tieneAudio la entrada tiene un audio adjunto
     */
    record BitacoraDeHoy(LocalDate fecha, boolean existe, String texto, boolean tieneAudio) {
    }

    /**
     * @param registradoEn hora local, en la zona del participante
     * @param deEstaHora   ya ocupa la franja de la hora en curso: registrar otro ahora no guarda nada
     */
    record CheckInRadar(LocalDateTime registradoEn, boolean deEstaHora, RespuestasRadar respuestas) {
    }

    record RespuestasRadar(String queHago, String quePienso, String queSiento, int nivelEnergia, String queEvito) {
    }

    /**
     * @param registradoEn hora local del registro que quedo (el nuevo, o el que ya existia)
     * @param yaExistia    habia uno en esta hora: se devolvio ese y NO se guardaron las respuestas
     */
    record CheckInRadarRegistrado(LocalDateTime registradoEn, boolean yaExistia) {
    }
}
