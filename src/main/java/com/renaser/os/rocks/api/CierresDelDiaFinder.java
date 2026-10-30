package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * «Lo que la persona dijo al cerrar cada día de una semana», visto desde otro módulo (spec 017): lo que leen las fichas del mentor,
 * la guía, el líder, Administración y Alquimista. Una sola regla para todos (FR-006), como {@code HuecosDeHorarioFinder}.
 *
 * <p><b>No autoriza</b>, igual que {@link AccionesDelDiaFinder}: quien llama ya estableció que el actor puede ver a esa persona.
 * Responde por el id que le dan y por SU zona (nunca la del servidor, regla 02).
 *
 * <p><b>Dato personal.</b> Las notas son texto que escribió la persona: quien las reciba no las loguea. Los valores de {@code estado},
 * {@code motivo} y {@code manana} viajan como el nombre de la constante, sin filtrar tipos del dominio de {@code rocks}.
 */
public interface CierresDelDiaFinder {

    /**
     * @param lunes el lunes de la semana pedida; {@code null} = la semana en curso de la persona, en SU zona
     * @throws IllegalArgumentException si no es lunes, es una semana futura o anterior a la primera de la persona
     * @throws java.util.NoSuchElementException si no tiene participación en el programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si su cuenta está suspendida
     */
    CierresDeLaSemana semanaDe(UserId personaId, LocalDate lunes);

    /**
     * @param semana        el lunes que de verdad se leyó (resuelto aunque el pedido no trajera uno)
     * @param primeraSemana el lunes de la semana de su primer día del programa; {@code null} si todavía no eligió su Día 1
     * @param dias          del Día 1 (o el lunes) a hoy (o el domingo), en orden; vacío sin Día 1. Nunca un día que viene
     */
    record CierresDeLaSemana(LocalDate semana, LocalDate primeraSemana, List<DiaDelCierre> dias, ResumenDeCierres resumen) {
    }

    /**
     * @param estado    {@code ABIERTO}, {@code CERRADO} o {@code SIN_CERRAR}
     * @param cerradoEn el instante del PRIMER cierre; {@code null} si no se cerró
     * @param nota      la nota del día; {@code null} si no hay
     * @param acciones  las acciones de ese día, por hora y posición
     */
    record DiaDelCierre(LocalDate fecha, String estado, Instant cerradoEn, String nota, List<AccionDelCierre> acciones) {
    }

    /**
     * @param horaInicio hora local; {@code null} si no se fijó
     * @param cumplidaEn el instante en que se cumplió; {@code null} si sigue pendiente
     * @param cierre     lo que dijo de ella; {@code null} si se cumplió o no lo contó (una respuesta vieja de una acción que se
     *                   cumplió después de cerrar no se muestra)
     */
    record AccionDelCierre(UUID id, String titulo, LocalTime horaInicio, boolean cumplida, Instant cumplidaEn,
                           RespuestaDelCierre cierre) {
    }

    /** {@code motivo} y {@code manana} son los nombres de las constantes; {@code nota} puede ser {@code null}. */
    record RespuestaDelCierre(String motivo, String manana, String nota) {
    }

    /**
     * @param cerrados  días listados con estado {@code CERRADO}
     * @param sinCerrar días listados con estado {@code SIN_CERRAR} (el de hoy, abierto, no cuenta)
     * @param motivos   cuántas veces aparece cada motivo en las acciones pendientes de esos días, de más a menos
     */
    record ResumenDeCierres(int cerrados, int sinCerrar, List<MotivoRepetido> motivos) {
    }

    record MotivoRepetido(String motivo, int veces) {
    }
}
