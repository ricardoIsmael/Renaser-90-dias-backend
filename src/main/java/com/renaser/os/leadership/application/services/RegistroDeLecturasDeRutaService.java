package com.renaser.os.leadership.application.services;

import com.renaser.os.habits.api.RutaDeCarreraConsultadaEvent;
import com.renaser.os.leadership.application.ports.in.RegistrarLecturaDeRutaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.ContenidoDelDiagnostico;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja constancia de quién abrió la ruta de una carrera de otra persona (spec 042, FR-041): una fila en {@code consultas_de_ficha} de
 * tipo {@code RUTA_DE_CARRERA}, con el rol con el que la abrió (se lee de su cuenta), el grupo y el mentor si pasó por uno, y el
 * instante de la lectura. Mismo criterio que {@link RegistroDeLecturasDelDiagnosticoService}: lo que no encaja (una cuenta que ya no
 * existe, un aprendiz como consultor) se anota en el registro de la aplicación y no se inserta.
 */
@Service
public class RegistroDeLecturasDeRutaService implements RegistrarLecturaDeRutaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroDeLecturasDeRutaService.class);

    private final UserSummaryFinder usuarios;
    private final GuardarConsultaDeFichaPort consultas;

    RegistroDeLecturasDeRutaService(UserSummaryFinder usuarios, GuardarConsultaDeFichaPort consultas) {
        this.usuarios = usuarios;
        this.consultas = consultas;
    }

    @Override
    @Transactional
    public void registrar(RutaDeCarreraConsultadaEvent lectura) {
        UserSummary consultor = usuarios.findById(lectura.consultorId()).orElse(null);
        if (consultor == null) {
            LOG.warn("[leadership] Lectura de una ruta sin constancia: la cuenta {} ya no existe", lectura.consultorId());
            return;
        }
        try {
            consultas.insertar(ConsultaDeFicha.registrarRutaDeCarrera(lectura.consultaId(), new ContenidoDelDiagnostico(
                    lectura.consultorId(), consultor.role(), lectura.personaId(), lectura.grupoId(), lectura.mentorId()),
                    lectura.consultadaEn()));
        } catch (IllegalArgumentException noEsUnaLectura) {
            LOG.warn("[leadership] Lectura de una ruta sin constancia: {}", noEsUnaLectura.getMessage());
        }
    }
}
