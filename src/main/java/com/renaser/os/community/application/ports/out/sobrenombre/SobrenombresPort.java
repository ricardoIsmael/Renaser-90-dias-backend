package com.renaser.os.community.application.ports.out.sobrenombre;

import com.renaser.os.community.domain.model.sobrenombre.Sobrenombre;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Spec 046: las tablas `sobrenombres`, `sobrenombres_cambios` y `palabras_prohibidas_sobrenombre` (V181). */
public interface SobrenombresPort {

    Optional<FilaDeSobrenombre> de(UserId persona);

    /** Solo las personas que tienen sobrenombre. Una consulta para todas (el Muro las pide por página). */
    Map<UserId, String> sobrenombresDe(Collection<UserId> personas);

    /** Quiénes (además de {@code excepto}) tienen ese mismo sobrenombre normalizado. */
    List<UserId> conElMismo(String normalizado, UserId excepto);

    /** Si es el nombre real (normalizado) de otra persona. */
    boolean esNombreRealDeOtra(String normalizado, UserId excepto);

    /** Candado por sobrenombre hasta el fin de la transacción: dos personas no se quedan con el mismo a la vez. */
    void bloquear(String normalizado);

    /** Guarda o quita ({@code null}) el sobrenombre. {@code porLaPersona} cuenta los 30 días entre cambios (D5). */
    void guardar(UserId persona, Sobrenombre sobrenombre, Instant ahora, boolean porLaPersona);

    void descartarInvitacion(UserId persona, Instant ahora);

    void anotarCambio(UserId persona, String antes, String despues, UserId por, String motivo, Instant en);

    /** Las palabras que agregó Administración. */
    List<String> palabrasDeAdministracion();

    void agregarPalabra(String palabraNormalizada, UserId por, Instant en);

    void quitarPalabra(String palabraNormalizada);

    /** Las personas con sobrenombre, con su nombre real, que contengan {@code buscar} en cualquiera de los dos (sin tildes). */
    List<FilaDeLaLista> listar(String buscar, int limite);

    record FilaDeSobrenombre(String sobrenombre, Instant elegidoEn, Instant cambiadoPorLaPersonaEn, Instant invitacionDescartadaEn) {
    }

    record FilaDeLaLista(UserId persona, String sobrenombre, String nombreReal, Instant desde) {
    }
}
