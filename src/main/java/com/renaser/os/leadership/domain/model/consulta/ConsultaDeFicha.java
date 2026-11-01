package com.renaser.os.leadership.domain.model.consulta;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * El hecho de que alguien del liderazgo abrió la ficha de cumplimiento de una persona (spec 005, FR-016): quién, con
 * qué rol, a quién, de qué grupo, la semana que miraba y cuándo. Alimenta el resumen que recibe el mentor a las 20:00.
 *
 * <p>También deja esta misma constancia la lectura de los <b>huecos de horario</b> de la persona (spec 015, FR-027,
 * decisión del dueño del 2026-10-29), con el lunes de la semana en {@code semanaDesde}. La fila no dice cuál de las dos
 * lecturas fue, y no hace falta: ambas son «miró a esta persona» y el resumen junta a cada una una sola vez.
 *
 * <p><b>Append-only</b>, como {@code ObservacionDeMentor}: no tiene un solo método que la cambie y la base lo hace
 * cumplir con un trigger (V109). No lleva ningún nombre ni texto, solo ids: sobrevive al borrado de las cuentas.
 *
 * <p>Desde la spec 021 el líder también abre a una persona sin pasar por un grupo, así que el grupo puede faltar: una
 * persona que no está en ninguno se registra con {@code grupoId} en null (V124).
 *
 * <p>Desde la spec 026 (decisión del dueño) también deja constancia de quién abrió el <b>diagnóstico</b> de una persona
 * ({@link TipoDeConsulta#DIAGNOSTICO}, V157): el mentor o la guía de su grupo, el líder, Administración y Alquimista. No es
 * de una semana ({@code semanaDesde} en null) y no alimenta el resumen del mentor. La propia persona no deja fila.
 *
 * @param grupoId  el grupo desde el que se la miró; null si la persona no está en ninguno (spec 021)
 * @param mentorId el mentor vigente del grupo al consultar, o null si no tenía: es a quien se le avisa
 * @param tipo     qué se abrió; una consulta de semana lleva su lunes y una de diagnóstico no
 */
public record ConsultaDeFicha(UUID id, UserId consultorId, UserRole rolConsultor, UserId personaId, UUID grupoId,
                              UserId mentorId, LocalDate semanaDesde, Instant consultadaEn, TipoDeConsulta tipo) {

    /** Quiénes pueden abrir la ficha de una semana: el líder de mentores, Administración y Alquimista (FR-001). */
    private static final Set<UserRole> CONSULTORES = Set.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    public ConsultaDeFicha {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(consultadaEn, "consultadaEn es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        if ((tipo == TipoDeConsulta.SEMANA) != (semanaDesde != null)) {
            throw new IllegalArgumentException("Una consulta de semana lleva su lunes y una de diagnóstico no");
        }
    }

    /** Una consulta de la ficha de una semana: la forma de siempre (specs 005, 015 y 017). */
    public ConsultaDeFicha(UUID id, UserId consultorId, UserRole rolConsultor, UserId personaId, UUID grupoId,
                           UserId mentorId, LocalDate semanaDesde, Instant consultadaEn) {
        this(id, consultorId, rolConsultor, personaId, grupoId, mentorId, semanaDesde, consultadaEn, TipoDeConsulta.SEMANA);
    }

    /** El id entra por el puerto {@code IdGenerator} y la hora por {@code Clock}: el dominio no genera ninguna. */
    public static ConsultaDeFicha registrar(UUID id, Contenido contenido, Clock clock) {
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        return new ConsultaDeFicha(id, contenido.consultorId(), contenido.rolConsultor(), contenido.personaId(),
                contenido.grupoId(), contenido.mentorId(), contenido.semanaDesde(), clock.now());
    }

    /**
     * Quién abrió el diagnóstico de una persona. A diferencia de {@link #registrar}, el instante lo trae quien lo lee: la constancia se
     * escribe después de la lectura (en la misma entrega del evento) y tiene que decir cuándo se leyó, no cuándo se anotó.
     */
    public static ConsultaDeFicha registrarDiagnostico(UUID id, ContenidoDelDiagnostico contenido, Instant consultadaEn) {
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        return new ConsultaDeFicha(id, contenido.consultorId(), contenido.rolConsultor(), contenido.personaId(), contenido.grupoId(),
                contenido.mentorId(), null, consultadaEn, TipoDeConsulta.DIAGNOSTICO);
    }

    /** Lo que dice quien consulta; la identidad y la hora las pone {@link #registrar}. */
    public record Contenido(UserId consultorId, UserRole rolConsultor, UserId personaId, UUID grupoId, UserId mentorId,
                            LocalDate semanaDesde) {

        public Contenido {
            Objects.requireNonNull(consultorId, "consultorId es obligatorio");
            Objects.requireNonNull(rolConsultor, "rolConsultor es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(semanaDesde, "semanaDesde es obligatoria");
            if (!CONSULTORES.contains(rolConsultor)) {
                throw new IllegalArgumentException("Solo MENTOR_LEAD, ADMIN o ALCHEMIST abren esta ficha: " + rolConsultor);
            }
        }
    }

    /**
     * Lo que dice quien abre un diagnóstico. Lo abre cualquiera que acompañe o administre, menos la propia persona (que no deja
     * constancia de mirarse a sí misma): ni el consultor puede ser la persona, ni un aprendiz puede abrir el de otro.
     */
    public record ContenidoDelDiagnostico(UserId consultorId, UserRole rolConsultor, UserId personaId, UUID grupoId, UserId mentorId) {

        public ContenidoDelDiagnostico {
            Objects.requireNonNull(consultorId, "consultorId es obligatorio");
            Objects.requireNonNull(rolConsultor, "rolConsultor es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            if (rolConsultor == UserRole.TRAINEE) {
                throw new IllegalArgumentException("Un aprendiz no abre el diagnóstico de otra persona");
            }
            if (consultorId.equals(personaId)) {
                throw new IllegalArgumentException("La persona no deja constancia de leer su propio diagnóstico");
            }
        }
    }
}
