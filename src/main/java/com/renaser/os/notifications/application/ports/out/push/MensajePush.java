package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;

/**
 * Lo que sale en un push, sin el destino.
 *
 * <p>Existe desde D-188 (E-9 de la retroalimentación del 26/09): el transporte de Expo necesita
 * saber el {@link TipoNotificacion} para elegir el canal de Android, y agregarlo como quinto
 * parámetro suelto a {@link PushPort#enviar} y {@link TransportePush#entregar} dejaba dos firmas
 * con cuatro {@code String}/enum intercambiables en fila.
 *
 * @param tipo    de qué es el aviso. Cada transporte decide qué hace con él (hoy solo Expo lo usa,
 *                para el {@code channelId}); puede ser {@code null} y entonces nadie elige canal.
 * @param rutaApp destino lógico dentro de la app al tocarlo. Puede ser {@code null}.
 */
public record MensajePush(TipoNotificacion tipo, String titulo, String cuerpo, String rutaApp) {
}
