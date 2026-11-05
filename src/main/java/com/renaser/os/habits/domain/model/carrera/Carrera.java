package com.renaser.os.habits.domain.model.carrera;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Una carrera con GPS ya guardada (spec 042, tabla {@code carreras}, V170): lo que mandó el teléfono, su huella, su día en la
 * zona de la persona, lo que decidió el servidor y lo que hizo con el hábito.
 *
 * <p>Es un hecho registrado: no cambia después de guardarse, salvo dos cosas que el servidor anota y no la persona: el efecto en
 * el hábito, que se escribe en la misma transacción que la guarda ({@link #conEfecto}), y la ruta, que se borra a los 180 días
 * (eso lo hace el barrido directo en la base).
 *
 * @param id     el del servidor (entra por {@code IdGenerator})
 * @param huella SHA-256 del cuerpo canónico ({@link DatosDeCarrera#huella()})
 * @param fecha  el día de la persona en que empezó (FR-021)
 */
public record Carrera(UUID id, UserId participanteId, DatosDeCarrera datos, String huella, LocalDate fecha,
                      EvaluacionDeCarrera evaluacion, EfectoEnElHabito efecto, Instant creadaEn) {

    public Carrera {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(participanteId, "participanteId");
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(huella, "huella");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(evaluacion, "evaluacion");
        Objects.requireNonNull(efecto, "efecto");
        Objects.requireNonNull(creadaEn, "creadaEn");
        if (!evaluacion.cuenta() && efecto.resultado() != ResultadoEnElHabito.NO_CUENTA) {
            throw new IllegalArgumentException("Una carrera que no cuenta no puede entrar al hábito");
        }
    }

    /**
     * Una carrera recién llegada. Si no cuenta, su efecto ya es definitivo ({@link ResultadoEnElHabito#NO_CUENTA}); si cuenta, el
     * efecto es provisional ({@link ResultadoEnElHabito#SIN_CAMBIO}) hasta que se aplique al hábito en la misma transacción.
     */
    public static Carrera nueva(UUID id, UserId participante, DatosDeCarrera datos, ZoneId zona, EvaluacionDeCarrera evaluacion,
                                Instant ahora) {
        EfectoEnElHabito provisional = EfectoEnElHabito.sinRegistro(evaluacion.cuenta() ? ResultadoEnElHabito.SIN_CAMBIO
                : ResultadoEnElHabito.NO_CUENTA);
        return new Carrera(id, participante, datos, datos.huella(), datos.fechaEn(zona), evaluacion, provisional, ahora);
    }

    public Carrera conEfecto(EfectoEnElHabito nuevo) {
        return new Carrera(id, participanteId, datos, huella, fecha, evaluacion, nuevo, creadaEn);
    }

    /** {@code true} si este envío es el mismo que el guardado (misma huella): la respuesta repetida. */
    public boolean esElMismoEnvio(String otraHuella) {
        return huella.equals(otraHuella);
    }
}
