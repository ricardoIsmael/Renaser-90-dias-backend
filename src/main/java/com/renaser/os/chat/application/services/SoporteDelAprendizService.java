package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.SoporteDelAprendizFinder;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Busca el soporte por su clave de negocio ({@code soporte:<aprendiz>}, V53). Solo lee. */
@Service
public class SoporteDelAprendizService implements SoporteDelAprendizFinder {

    private final LoadConversacionPort conversaciones;

    SoporteDelAprendizService(LoadConversacionPort conversaciones) {
        this.conversaciones = conversaciones;
    }

    @Override
    public Optional<UUID> conversacionDeSoporteDe(UserId aprendizId) {
        return conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(aprendizId))
                .map(conversacion -> conversacion.id().value());
    }
}
