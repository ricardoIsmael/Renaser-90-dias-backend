package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.application.ports.out.participante.CargarParticipacionesPort;
import com.renaser.os.users.application.ports.out.participante.ListarParticipantesConProgramaActivoPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementa {@link ProgramasActivadosFinder} (D-168) sobre los mismos puertos que ya usa el reloj
 * del programa ({@code RelojProgramaService}): la página de programas activados y la carga por
 * participante. Las fechas las calcula el agregado; acá solo se proyectan.
 */
@Service
public class ProgramasActivadosService implements ProgramasActivadosFinder {

    private final ListarParticipantesConProgramaActivoPort listarParticipantesPort;
    private final LoadParticipacionProgramaPort loadParticipacionPort;
    private final CargarParticipacionesPort cargarParticipacionesPort;

    public ProgramasActivadosService(ListarParticipantesConProgramaActivoPort listarParticipantesPort,
                                     LoadParticipacionProgramaPort loadParticipacionPort,
                                     CargarParticipacionesPort cargarParticipacionesPort) {
        this.listarParticipantesPort = listarParticipantesPort;
        this.loadParticipacionPort = loadParticipacionPort;
        this.cargarParticipacionesPort = cargarParticipacionesPort;
    }

    /**
     * No filtra nada de la página: quien pagina corta cuando una página viene incompleta, y un
     * filtro acá le haría creer que se acabó el padrón. Una fila activada sin fecha de inicio (dato
     * incoherente) viaja con fechas {@code null} y quien consume la saltea.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProgramaActivado> pagina(int offset, int limite) {
        return listarParticipantesPort.pagina(offset, limite).stream()
                .map(ProgramasActivadosService::aProgramaActivado)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProgramaActivado> de(UserId participanteId) {
        return loadParticipacionPort.byParticipanteId(participanteId)
                .filter(ParticipacionPrograma::estaActivado)
                .map(ProgramasActivadosService::aProgramaActivado);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, ProgramaActivado> deVarios(Collection<UserId> participantes) {
        Map<UserId, ProgramaActivado> resultado = new LinkedHashMap<>();
        cargarParticipacionesPort.deVarios(participantes).stream()
                .filter(ParticipacionPrograma::estaActivado)
                .map(ProgramasActivadosService::aProgramaActivado)
                .forEach(programa -> resultado.put(programa.participanteId(), programa));
        return resultado;
    }

    private static ProgramaActivado aProgramaActivado(ParticipacionPrograma participacion) {
        return new ProgramaActivado(participacion.participanteId(), participacion.timezone(),
                participacion.primeraFechaDelPrograma(), participacion.ultimaFechaDelPrograma());
    }
}
