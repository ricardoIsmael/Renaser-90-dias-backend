package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.domain.model.evento.AudienciaDelMensaje;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.UserRole;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.OptionalLong;
import java.util.Set;

/**
 * La configuracion de a quien le llega el mensaje de un evento (spec 041, FR-011, FR-015 y FR-025). Son valores, no codigo: cambiarlos no exige
 * desplegar nada nuevo ni migrar, y cada despacho los lee tal como estan en el entorno.
 *
 * <ul>
 *   <li>{@code renaser.eventos.mensajes.audiencia}: {@code VOY} (por defecto) o {@code TODOS}.</li>
 *   <li>{@code renaser.eventos.mensajes.roles}: los roles que reciben, separados por comas. Por defecto solo {@code TRAINEE}; sumar al personal es agregar
 *       su rol. Un nombre que no es un rol se ignora; si no queda ninguno valido, vale el valor por defecto (nunca se abre de mas).</li>
 *   <li>{@code renaser.eventos.whatsapp.tope-mensual}: maximo de mensajes de WhatsApp por mes calendario. Vacio o ausente = sin tope (por defecto).</li>
 * </ul>
 */
@Component
class ReglasDelMensajeDeEvento {

    static final String PROPIEDAD_AUDIENCIA = "renaser.eventos.mensajes.audiencia";
    static final String PROPIEDAD_ROLES = "renaser.eventos.mensajes.roles";
    static final String PROPIEDAD_TOPE = "renaser.eventos.whatsapp.tope-mensual";
    private static final Set<UserRole> ROLES_POR_DEFECTO = EnumSet.of(UserRole.TRAINEE);

    private final Environment entorno;

    ReglasDelMensajeDeEvento(Environment entorno) {
        this.entorno = entorno;
    }

    AudienciaDelMensaje audiencia() {
        return AudienciaDelMensaje.desde(entorno.getProperty(PROPIEDAD_AUDIENCIA));
    }

    Set<UserRole> roles() {
        String valor = entorno.getProperty(PROPIEDAD_ROLES);
        if (valor == null || valor.isBlank()) {
            return ROLES_POR_DEFECTO;
        }
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        Arrays.stream(valor.split(",")).map(String::strip).forEach(nombre -> rol(nombre).ifPresent(roles::add));
        return roles.isEmpty() ? ROLES_POR_DEFECTO : roles;
    }

    /** Sin valor, o con algo que no es un numero positivo, no hay tope. */
    OptionalLong topeMensual() {
        String valor = entorno.getProperty(PROPIEDAD_TOPE);
        if (valor == null || valor.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long tope = Long.parseLong(valor.strip());
            return tope > 0 ? OptionalLong.of(tope) : OptionalLong.empty();
        } catch (NumberFormatException noEsNumero) {
            return OptionalLong.empty();
        }
    }

    /** La formacion del evento; la Escuela si falta (una publicacion vieja del outbox) o no se entiende. */
    static Formacion formacionDe(String codigo) {
        try {
            return Formacion.desde(codigo);
        } catch (IllegalArgumentException sinFormacion) {
            return Formacion.POR_DEFECTO;
        }
    }

    private static java.util.Optional<UserRole> rol(String nombre) {
        try {
            return java.util.Optional.of(UserRole.valueOf(nombre.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException noEsUnRol) {
            return java.util.Optional.empty();
        }
    }
}
