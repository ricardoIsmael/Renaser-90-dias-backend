package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.santuario.LoadRachaSinCelularPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadSesionBloqueoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Las lecturas de apoyo de {@link EnfoqueDiarioService}, en un solo colaborador para que la fachada
 * no cargue nueve dependencias. Sin reglas: solo consulta los puertos de salida de {@code habits}.
 * Paquete-privado: no es un caso de uso ni un contrato, es la parte de lectura de esa fachada.
 */
@Component
class EnfoqueDiarioLecturas {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadSesionBloqueoPort loadSesionPort;
    private final LoadRachaSinCelularPort loadRachaPort;

    EnfoqueDiarioLecturas(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                          LoadSesionBloqueoPort loadSesionPort, LoadRachaSinCelularPort loadRachaPort) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadSesionPort = loadSesionPort;
        this.loadRachaPort = loadRachaPort;
    }

    /** Mismo criterio que {@code SantuarioService.requireProgreso}: sin participacion o suspendida, no. */
    ZoneId zonaNoSuspendida(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return ZoneId.of(progreso.timezone());
    }

    Optional<Habito> habitoPorClave(String claveSistema) {
        return loadHabitoPort.porClaveSistema(claveSistema);
    }

    boolean tieneSesion(RegistroHabitoId registroId) {
        return loadSesionPort.porRegistro(registroId).isPresent();
    }

    Optional<RachaSinCelular> rachaActiva(UserId participanteId) {
        return loadRachaPort.activaDe(participanteId);
    }
}
