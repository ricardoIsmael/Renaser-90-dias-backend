package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Una persona marcada como presente en una ocurrencia (tabla {@code asistencias_evento}).
 *
 * @param marcadoPor quién pasó lista; {@code null} si esa cuenta ya se borró (D-243: la marca sobrevive y
 *                   pierde el autor)
 */
public record MarcaDeAsistencia(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId, EstadoAsistencia estado,
                                UserId marcadoPor, Instant marcadoEn) {

    public MarcaDeAsistencia {
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(inicioOcurrencia, "inicioOcurrencia es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(marcadoEn, "marcadoEn es obligatorio");
    }

    /**
     * La marca que queda después de pedir {@code estado}. Pedir lo mismo que ya está devuelve la MISMA marca
     * (no mueve la hora ni el autor): reintentar un toque que no tuvo respuesta no cambia lo que se ve.
     */
    public MarcaDeAsistencia remarcar(EstadoAsistencia nuevo, UserId actor, Instant ahora) {
        if (nuevo == estado) {
            return this;
        }
        return new MarcaDeAsistencia(eventoId, inicioOcurrencia, usuarioId, nuevo, actor, ahora);
    }
}
