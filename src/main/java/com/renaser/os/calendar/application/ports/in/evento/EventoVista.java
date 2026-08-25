package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.Evento;

/**
 * Proyeccion de {@link Evento} con la URL de portada YA resuelta (presignada, via
 * {@code AlmacenamientoPort}) — existe para que {@code EventoController} nunca dependa de
 * un puerto {@code out} directamente (ArchitectureTest.controllersDoNotTouchPersistence,
 * CLAUDE.MD §5.4.6): el controller es tonto, solo invoca casos de uso.
 */
public record EventoVista(Evento evento, String coverUrl) {
}
