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

    public static RegistroEspiritu desbloquear(UserId participanteId, int dia, Instant desbloqueadoEn,
                                                Instant fechaLimite, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        if (dia < 1 || dia > 90) {
            throw new IllegalArgumentException("dia fuera de rango 1..90: " + dia);
        }
        return new RegistroEspiritu(RegistroEspirituId.newId(), participanteId, dia, desbloqueadoEn, fechaLimite,
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

    public void entregar(String resumenTexto, Instant ahora) {
        if (estado != EstadoRegistroEspiritu.PENDIENTE) {
            throw new IllegalStateException("Este registro de espiritu ya no esta pendiente: " + estado);
        }
        if (ahora.isAfter(fechaLimite)) {
            throw new IllegalStateException("El plazo para entregar este audio ya vencio");
        }
        this.estado = EstadoRegistroEspiritu.ENTREGADO;
        this.entregadoEn = ahora;
        this.resumenTexto = resumenTexto;
        this.actualizadoEn = ahora;
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
