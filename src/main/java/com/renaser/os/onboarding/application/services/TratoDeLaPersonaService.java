package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.TratoDeLaPersonaFinder;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort.ValorDeRespuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Lee la respuesta a {@code sex} de la ficha inicial (E-457). Los valores son los de
 * {@code opciones_pregunta}: "Masculino" y "Femenino". Cualquier otra cosa, o nada, es NEUTRO.
 */
@Service
public class TratoDeLaPersonaService implements TratoDeLaPersonaFinder {

    static final String CLAVE_SEXO = "sex";

    private final LeerRespuestasPorClavePort leerRespuestasPort;

    public TratoDeLaPersonaService(LeerRespuestasPorClavePort leerRespuestasPort) {
        this.leerRespuestasPort = leerRespuestasPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Trato de(UserId usuarioId) {
        ValorDeRespuesta valor = leerRespuestasPort.deUsuario(usuarioId, Set.of(CLAVE_SEXO)).get(CLAVE_SEXO);
        String texto = valor == null || valor.texto() == null ? "" : valor.texto().trim();
        return switch (texto) {
            case "Masculino" -> Trato.MASCULINO;
            case "Femenino" -> Trato.FEMENINO;
            default -> Trato.NEUTRO;
        };
    }
}
