package com.renaser.os.support.application.services;

import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/** Doble de test para ApplicationEventPublisher: solo registra lo publicado, no hace nada mas. */
class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<Object> eventosPublicados = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        eventosPublicados.add(event);
    }

    List<Object> eventosPublicados() {
        return eventosPublicados;
    }
}
