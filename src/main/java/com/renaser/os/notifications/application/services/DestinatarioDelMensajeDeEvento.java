package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Si el mensaje de un evento le corresponde a esta persona (spec 041, FR-015): una cuenta activa y que no sea de prueba, de un rol que recibe
 * ({@code renaser.eventos.mensajes.roles}) y dentro de la audiencia configurada ({@code VOY} o {@code TODOS}). Lo comparten el chat y el WhatsApp, para que
 * los dos le lleguen a la misma gente. La audiencia del evento en si (todos, nivel, curso, roles o grupo, dentro de su formacion) ya la resolvio
 * {@code calendar} al generar el aviso.
 *
 * <p>No decide las preferencias: cada canal mira la suya ({@code RECORDATORIO_EVENTO} para el chat, {@code RECORDATORIO_EVENTO_WHATSAPP} para WhatsApp).
 */
@Component
class DestinatarioDelMensajeDeEvento {

    private final UserSummaryFinder usuarios;
    private final ReglasDelMensajeDeEvento reglas;

    DestinatarioDelMensajeDeEvento(UserSummaryFinder usuarios, ReglasDelMensajeDeEvento reglas) {
        this.usuarios = usuarios;
        this.reglas = reglas;
    }

    /** La persona, si le corresponde el mensaje; vacio si no existe, esta suspendida, es de prueba, no tiene un rol que reciba o no esta en la audiencia. */
    Optional<UserSummary> siLeCorresponde(AvisoDeMensajeDelEvento aviso) {
        return usuarios.findById(aviso.destinatarioId())
                .filter(persona -> persona.status().allowsAccess())
                .filter(persona -> !persona.esCuentaDePrueba())
                .filter(persona -> reglas.roles().contains(persona.role()))
                .filter(persona -> reglas.audiencia().admite(aviso.dijoVoy()));
    }
}
