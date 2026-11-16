package com.renaser.os.community.application.ports.in.sobrenombre;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Spec 046 (D-331): Administración y Alquimista ven, cambian y quitan sobrenombres, y editan las palabras prohibidas. Solo ellos: cada
 * operación responde 403 a quien no modera el Muro (ADMIN o ALCHEMIST activos).
 */
public interface AdministrarSobrenombresUseCase {

    /** El sobrenombre de una persona de su alcance, o {@code null} si no tiene. */
    String de(UserId actor, UserId persona);

    /** Lo cambia ({@code nuevo}) o lo quita ({@code null}), con motivo; deja constancia y le avisa a la persona sin el motivo (D7). */
    String cambiar(UserId actor, UserId persona, String nuevo, String motivo);

    List<PersonaConSobrenombre> listar(UserId actor, String buscar);

    PalabrasProhibidas palabras(UserId actor);

    PalabrasProhibidas agregarPalabra(UserId actor, String palabra);

    PalabrasProhibidas quitarPalabra(UserId actor, String palabra);

    record PersonaConSobrenombre(UserId persona, String sobrenombre, String nombreReal, Instant desde) {
    }

    record PalabrasProhibidas(int deLaListaBase, List<String> deAdministracion) {
    }
}
