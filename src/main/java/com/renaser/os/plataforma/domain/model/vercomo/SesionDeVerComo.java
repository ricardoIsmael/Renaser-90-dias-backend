package com.renaser.os.plataforma.domain.model.vercomo;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Una sesión de solo lectura de «ver como» (contrato fase-2.md §5; D-273): la de la cuenta de prueba, marcada con quién
 * la abrió y hasta cuándo vale. Vence a los {@link #DURACION} aunque se siga usando.
 *
 * @param cuenta     la cuenta de prueba que se mira
 * @param abiertaPor la cuenta Soporte TI que la abrió (actor de la auditoría al entrar y al salir)
 */
public record SesionDeVerComo(UserId cuenta, UserId abiertaPor, RolElegido rol, Instant venceEn) {

    public static final Duration DURACION = Duration.ofMinutes(30);

    public SesionDeVerComo {
        Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        Objects.requireNonNull(abiertaPor, "abiertaPor es obligatorio");
        Objects.requireNonNull(rol, "rol es obligatorio");
        Objects.requireNonNull(venceEn, "venceEn es obligatorio");
    }

    public static SesionDeVerComo abrir(UserId cuenta, UserId abiertaPor, RolElegido rol, Instant ahora) {
        return new SesionDeVerComo(cuenta, abiertaPor, rol, ahora.plus(DURACION));
    }
}
