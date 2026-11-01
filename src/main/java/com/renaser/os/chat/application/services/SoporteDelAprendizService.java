package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.SoporteDelAprendizFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Busca el soporte por su clave de negocio ({@code soporte:<aprendiz>}, V53). Solo lee. */
@Service
public class SoporteDelAprendizService implements SoporteDelAprendizFinder {

    private final SoporteVigenteDelAprendiz soportes;

    SoporteDelAprendizService(SoporteVigenteDelAprendiz soportes) {
        this.soportes = soportes;
    }

    @Override
    public Optional<UUID> conversacionDeSoporteDe(UserId aprendizId) {
        return soportes.de(aprendizId)
                .map(conversacion -> conversacion.id().value());
    }
}
