package com.renaser.os.users.domain.model.user;

import com.renaser.os.users.api.UserRole;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * El correo y el nombre de cada cuenta de prueba de «ver como rol» (spec 002, fase 2, US5; contrato fase-2.md §5;
 * D-273): {@code ver-como+<rol>@renaser.invalid} para un rol base ({@code <rol>} es el de la API en minúsculas y con
 * guiones: {@code mentor}, {@code mentor-lead}, {@code trainee-guide}…) y {@code ver-como+rp-<id>@renaser.invalid} para
 * un rol personalizado. El correo es la identidad estable de la cuenta: con él se la vuelve a encontrar la segunda vez.
 *
 * <p>{@code .invalid} es un dominio reservado (RFC 2606): no tiene buzón, nadie recibe un código de recuperación ni un
 * aviso, y no choca con el correo de ninguna persona real. La base exige el mismo patrón (V107).
 */
public final class CorreoDeCuentaDePrueba {

    static final String PREFIJO = "ver-como+";
    static final String DOMINIO = "@renaser.invalid";

    private CorreoDeCuentaDePrueba() {
    }

    public static Email deRol(UserRole rol) {
        Objects.requireNonNull(rol, "rol es obligatorio");
        return new Email(PREFIJO + rol.name().toLowerCase(Locale.ROOT).replace('_', '-') + DOMINIO);
    }

    public static Email deRolPersonalizado(UUID rolPersonalizadoId) {
        Objects.requireNonNull(rolPersonalizadoId, "rolPersonalizadoId es obligatorio");
        return new Email(PREFIJO + "rp-" + rolPersonalizadoId + DOMINIO);
    }

    /** «Prueba · Mentor», «Prueba · Mentor junior». */
    public static String nombre(String nombreDelRol) {
        return "Prueba · " + Objects.requireNonNull(nombreDelRol, "nombreDelRol es obligatorio");
    }

    public static boolean loEs(Email email) {
        return email != null && email.value().startsWith(PREFIJO) && email.value().endsWith(DOMINIO);
    }
}
