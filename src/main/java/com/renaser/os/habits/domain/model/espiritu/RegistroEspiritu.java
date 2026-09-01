package com.renaser.os.habits.domain.model.espiritu;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Entrega del audio de Espiritu del dia (tabla `registros_espiritu`, FK real a
 * `audios_espiritu.dia` — el catalogo de audios vive detras de un puerto a
 * Google Drive, D-34 no aplica aca, decision previa preservada).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RegistroEspiritu {

    private final RegistroEspirituId id;
    private final UserId participanteId;
    private final int dia;
    private final Instant desbloqueadoEn;
    private final Instant fechaLimite;
    private Instant entregadoEn;
    private String resumenTexto;
    private EstadoRegistroEspiritu estado;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code EspirituService}). Asi
     * {@code desbloquear} es referencialmente transparente y un test puede fijar el id que
     * espera, en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static RegistroEspiritu desbloquear(RegistroEspirituId id, UserId participanteId, int dia,
                                                Instant desbloqueadoEn, Instant fechaLimite, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        if (dia < 1 || dia > 90) {
            throw new IllegalArgumentException("dia fuera de rango 1..90: " + dia);
        }
        return new RegistroEspiritu(id, participanteId, dia, desbloqueadoEn, fechaLimite,
                null, null, EstadoRegistroEspiritu.PENDIENTE, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static RegistroEspiritu rehydrate(RegistroEspirituId id, UserId participanteId, int dia,
                                              Instant desbloqueadoEn, Instant fechaLimite, Instant entregadoEn,
                                              String resumenTexto, EstadoRegistroEspiritu estado, Instant creadoEn,
                                              Instant actualizadoEn) {
        return new RegistroEspiritu(id, participanteId, dia, desbloqueadoEn, fechaLimite, entregadoEn, resumenTexto,
                estado, creadoEn, actualizadoEn);
    }

    /**
     * Entrega el resumen del audio del dia. A TIEMPO (antes de {@code fechaLimite}) pasa a
     * ENTREGADO. FUERA DE PLAZO queda PENDIENTE — no lanza excepcion — con el contenido
     * igual guardado (para que un mentor lo pueda leer) y {@code entregadoEn} actualizado;
     * es el propio state machine de {@code EspirituService} (via
     * {@code asegurarAvance}/{@link #marcarPerdido}) el unico lugar que la pasa a PERDIDO,
     * en el proximo chequeo lazy — igual criterio en TODOS los casos de tardanza, sin
     * importar CUANDO se descubre (repo viejo: service.ts, submitSpiritSummary /
     * ensureAdvanced). Devuelve si la entrega fue a tiempo, para que el llamador decida
     * efectos secundarios (ej. reflejar en "Pastilla Renacer" solo si fue a tiempo).
     *
     * <p>Corregido 2026-08-26 (encargo original): antes tiraba {@code IllegalStateException}
     * fuera de plazo, lo que no coincide con el comportamiento real del backend viejo — una
     * entrega tardia es un caso de negocio valido, no un error.
     */
    public boolean entregar(String resumenTexto, Instant ahora) {
        if (estado != EstadoRegistroEspiritu.PENDIENTE) {
            throw new IllegalStateException("Este registro de espiritu ya no esta pendiente: " + estado);
        }
        boolean aTiempo = !ahora.isAfter(fechaLimite);
        this.entregadoEn = ahora;
        this.resumenTexto = resumenTexto;
        this.estado = aTiempo ? EstadoRegistroEspiritu.ENTREGADO : EstadoRegistroEspiritu.PENDIENTE;
        this.actualizadoEn = ahora;
        return aTiempo;
    }

    public void marcarPerdido(Instant ahora) {
        if (estado != EstadoRegistroEspiritu.PENDIENTE) {
            return; // idempotente
        }
        this.estado = EstadoRegistroEspiritu.PERDIDO;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "RegistroEspiritu[" + id + ", dia " + dia + ", " + estado + "]";
    }
}
