package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.RegistrarLecturaDelDiagnosticoUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.ContenidoDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja constancia de quién abrió el diagnóstico de una persona (spec 026): una fila en {@code consultas_de_ficha} de tipo {@code DIAGNOSTICO}, con el rol
 * con el que lo abrió (se lee de su cuenta; el evento solo trae ids), el grupo y el mentor si pasó por uno, y el instante de la lectura.
 *
 * <p>Lo que no encaja no tumba nada: una cuenta que ya no existe o un aprendiz como consultor se anotan en el registro de la aplicación y no se insertan (la
 * entrega no se reintenta eternamente por un dato que no va a cambiar). La propia persona nunca llega acá: onboarding no publica su lectura.
 */
@Service
public class RegistroDeLecturasDelDiagnosticoService implements RegistrarLecturaDelDiagnosticoUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroDeLecturasDelDiagnosticoService.class);

    private final UserSummaryFinder usuarios;
    private final GuardarConsultaDeFichaPort consultas;

    RegistroDeLecturasDelDiagnosticoService(UserSummaryFinder usuarios, GuardarConsultaDeFichaPort consultas) {
        this.usuarios = usuarios;
        this.consultas = consultas;
    }

    @Override
    @Transactional
    public void registrar(DiagnosticoConsultadoEvent lectura) {
        UserSummary consultor = usuarios.findById(lectura.consultorId()).orElse(null);
        if (consultor == null) {
            LOG.warn("[leadership] Lectura del diagnostico sin constancia: la cuenta {} ya no existe", lectura.consultorId());
            return;
        }
        try {
            consultas.insertar(ConsultaDeFicha.registrarDiagnostico(lectura.consultaId(), new ContenidoDelDiagnostico(lectura.consultorId(), consultor.role(),
                    lectura.personaId(), lectura.grupoId(), lectura.mentorId()), lectura.consultadaEn()));
        } catch (IllegalArgumentException noEsUnaLectura) {
            LOG.warn("[leadership] Lectura del diagnostico sin constancia: {}", noEsUnaLectura.getMessage());
        }
    }
}
