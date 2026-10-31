package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.ProgramacionDelDia;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.mentoring.api.ResumenSemana;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La semana de una persona de un grupo, día por día, con todo lo que la respalda, para el líder (spec 005, FR-009 a
 * FR-013). <b>Registra una consulta</b> (FR-016) cada vez que se llama: cambiar de semana es otra llamada y otra fila.
 *
 * <p>Solo cumplimiento y evidencias (FR-013): ni el Mapa de Renacimiento, ni los objetivos escritos, ni los chats ni
 * la ficha inicial de la persona.
 */
public interface ConsultarSemanaDePersonaUseCase {

    SemanaDeLaPersona semanaDe(ConsultaSemanaDePersona consulta);

    /**
     * La misma semana, sin pasar por un grupo (spec 021): la persona es cualquier aprendiz que se muestra, tenga grupo,
     * esté en la recepción o tenga un grupo sin mentor. Deja el mismo registro de consulta.
     */
    SemanaDeLaPersona semanaDeUnaPersona(ConsultaSemanaDeUnaPersona consulta);

    /**
     * @param semanaHasta null = la ventana vigente del resumen por grupos (los 7 días que terminan ayer); si viene, un
     *                    viernes que ya llegó: la semana de sábado a viernes que termina ese día
     */
    record ConsultaSemanaDePersona(UserId actorId, UUID grupoId, UUID personaId, LocalDate semanaHasta) {

        public ConsultaSemanaDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    /** @param semanaHasta igual que en {@link ConsultaSemanaDePersona} */
    record ConsultaSemanaDeUnaPersona(UserId actorId, UUID personaId, LocalDate semanaHasta) {

        public ConsultaSemanaDeUnaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    /**
     * @param cerrada       true solo si la ventana termina en una semana ya cerrada el sábado (su resultado ya no
     *                      cambia); false mientras esa semana siga abierta, como la vigente de un lunes
     * @param primeraSemana el sábado que abre la primera semana de su programa; null si todavía no eligió su Día 1.
     *                      Ninguna ventana que termine antes del viernes de esa semana (su sábado + 6) tiene nada
     *                      que mostrar: es el tope hacia atrás
     * @param grupoId       el grupo con que se la mira; null si la persona no está en ninguno (spec 021)
     * @param mentor        el mentor vigente del grupo; null si no tiene (la app esconde «Avisar a su mentor»)
     * @param color         el del semáforo de esa ventana; {@code NO_SE_MIDE} para quien lleva su propio programa
     * @param porcentaje    1 decimal; null si ningún día se midió
     * @param programacion  spec 018: si programó su día de HOY (hábitos con su hora y acciones agendadas); null si no hay
     *                      nada que programar (programa sin activar, ningún hábito que le toque hoy). No depende de la
     *                      ventana de {@code dias}
     */
    record SemanaDeLaPersona(UUID grupoId, UUID personaId, String nombre, String avatarUrl, UserRole rol,
                             LocalDate desde, LocalDate hasta, String zona, boolean cerrada, LocalDate primeraSemana,
                             MentorDelGrupo mentor, ColorDeLaFila color, BigDecimal porcentaje,
                             ResumenSemana resumen, List<DiaDeLaFicha> dias, ProgramacionDelDia programacion) {

        public SemanaDeLaPersona {
            dias = dias == null ? List.of() : List.copyOf(dias);
        }
    }

    record MentorDelGrupo(UUID id, String nombre) {
    }

    /**
     * @param motivo el nombre de {@code MotivoDelDia} si el día no tiene nada que medir; null si se midió. Nunca es
     *               un incumplimiento. {@code EN_CURSO}: hoy, que solo aparece al pedir el viernes de hoy
     */
    record DiaDeLaFicha(LocalDate fecha, Integer diaDePrograma, String motivo, List<ObligacionDelDia> obligaciones,
                        List<AccionDelDia> acciones) {

        public DiaDeLaFicha {
            obligaciones = obligaciones == null ? List.of() : List.copyOf(obligaciones);
            acciones = acciones == null ? List.of() : List.copyOf(acciones);
        }
    }
}
