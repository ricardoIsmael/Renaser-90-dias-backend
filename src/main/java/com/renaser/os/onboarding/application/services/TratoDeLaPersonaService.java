package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
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
    private final DiagnosticoDeLaPersonaFinder diagnostico;

    public TratoDeLaPersonaService(LeerRespuestasPorClavePort leerRespuestasPort, DiagnosticoDeLaPersonaFinder diagnostico) {
        this.leerRespuestasPort = leerRespuestasPort;
        this.diagnostico = diagnostico;
    }

    /** Primero lo que contestó en el diagnóstico (spec 026, la pregunta del trato); si no tiene, la ficha estándar. */
    @Override
    @Transactional(readOnly = true)
    public Trato de(UserId usuarioId) {
        String delDiagnostico = diagnostico.tratoDe(usuarioId);
        if (delDiagnostico != null) {
            return switch (delDiagnostico) {
                case "MASCULINO" -> Trato.MASCULINO;
                case "FEMENINO" -> Trato.FEMENINO;
                default -> Trato.NEUTRO;
            };
        }
        ValorDeRespuesta valor = leerRespuestasPort.deUsuario(usuarioId, Set.of(CLAVE_SEXO)).get(CLAVE_SEXO);
        String texto = valor == null || valor.texto() == null ? "" : valor.texto().trim();
        return switch (texto) {
            case "Masculino" -> Trato.MASCULINO;
            case "Femenino" -> Trato.FEMENINO;
            default -> Trato.NEUTRO;
        };
    }
}
