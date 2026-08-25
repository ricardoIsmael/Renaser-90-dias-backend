package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.shared.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo para tests: publica un evento DENTRO de una transaccion propia que hace commit al
 * volver. {@code @ApplicationModuleListener} corre en fase AFTER_COMMIT — si el test llamara
 * a {@code ApplicationEventPublisher.publishEvent(...)} desde un metodo de test marcado
 * {@code @Transactional} (que Spring Test revierte al final), el commit nunca ocurriria y el
 * listener nunca se dispararia. Este componente le da al publish su propio limite de
 * transaccion real.
 */
@Component
class TransactionalEventPublisherTestHelper {

    private final ApplicationEventPublisher publisher;

    TransactionalEventPublisherTestHelper(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional
    void publicarYConfirmar(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
