package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Las publicaciones del Muro hechas sin red (spec 048, fase 2, V189): qué envío ya llegó (un reintento no la duplica) y la
 * hora real en que se escribió (el «Post diario» cuenta ese día, D17).
 */
public interface PublicacionesSinRedPort {

    /** La publicación que dejó ese envío de ese autor, si ya llegó. */
    Optional<UUID> publicacionDe(UserId autorId, UUID idCliente);

    void registrar(UserId autorId, UUID idCliente, UUID publicacionId, Instant hechoEn, String origenDeLaHora);
}
