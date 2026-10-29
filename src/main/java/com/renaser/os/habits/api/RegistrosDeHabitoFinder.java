package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los registros de hábito de UNA persona entre dos fechas, con el instante en que se cumplió cada uno (spec 015: «lo
 * real» frente a lo planificado). Sale de {@code registros_habito}, que es historia y no una proyección del catálogo de
 * hoy: guarda qué hábitos le tocaron cada día y cuándo los cumplió.
 *
 * <p>Es un segundo contrato junto a {@link ObligacionesHistoricasFinder} porque pregunta otra cosa: aquel arma las
 * obligaciones de un grupo para el seguimiento del mentor (sin hábito ni instante); este responde por una persona y
 * dice exactamente a qué hora cumplió cada hábito.
 *
 * <p><b>No autoriza ni mira el estado de la cuenta</b>, igual que los otros finders de este paquete: quien llama ya
 * estableció que el actor puede ver a esa persona. Sin {@code @Transactional}: es una sola lectura.
 */
public interface RegistrosDeHabitoFinder {

    /**
     * @param desde inclusivo, fecha local de la persona ya resuelta por quien llama
     * @param hasta inclusivo
     * @return ordenados por fecha; vacío si no hay nada, nunca null
     */
    List<RegistroDeHabito> deParticipanteEntre(UserId participanteId, LocalDate desde, LocalDate hasta);

    /**
     * @param habitoId     el del catálogo o el propio de la persona
     * @param opcional     ese día no era exigible: sin cumplirse, no cuenta como falta
     * @param completadoEn el instante en que se cumplió; {@code null} si no se cumplió
     */
    record RegistroDeHabito(UUID habitoId, LocalDate fecha, EstadoObligacion estado, boolean opcional,
                            Instant completadoEn) {
    }
}
