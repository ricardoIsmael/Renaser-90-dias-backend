package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionDiariaUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * D-226 — {@link RegistrarMedicionDiariaUseCase}: ubica el track del día por (participante, clave,
 * fecha) y lo completa con el número por {@link CompletarRegistroUseCase}. No reimplementa nada: la
 * política del hábito valida el número, y puntos, ventana y evento salen de {@code RegistroService}.
 *
 * <p>El track se busca CON cerrojo y en la misma transacción que lo completa (mismo motivo que
 * {@code PastillaRenacerHabitoService}: Hibernate no rehidrata una entidad ya leída sin cerrojo).
 * La pertenencia se mira acá también, antes de leer nada: si no, un actor ajeno sabría por el 404 si
 * otra persona tiene track ese día.
 */
@Service
public class MedicionDiariaService implements RegistrarMedicionDiariaUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;

    public MedicionDiariaService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                 CompletarRegistroUseCase completarRegistroUseCase) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
    }

    @Override
    @Transactional
    public RegistroHabito registrar(RegistrarMedicionCommand command) {
        if (!command.actorId().equals(command.participanteId())) {
            throw new NotAuthorizedException("Solo el propio participante registra su medición");
        }
        Habito habito = loadHabitoPort.porClaveSistema(command.claveSistema())
                .orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + command.claveSistema()));
        RegistroHabito registro = loadRegistroPort.porParticipanteHabitoYFechaParaEscritura(command.participanteId(),
                        habito.id(), command.fecha())
                .orElseThrow(() -> new NoSuchElementException("No hay registro de ese hábito ese día"));
        return completarRegistroUseCase.completar(new CompletarRegistroCommand(command.actorId(), registro.id(),
                null, null, GestoCompletar.GENERICO, command.medicion()));
    }
}
