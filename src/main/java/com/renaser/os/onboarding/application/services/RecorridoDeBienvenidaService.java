package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase;
import com.renaser.os.onboarding.application.ports.out.recorrido.RecorridoDeBienvenidaPort;
import com.renaser.os.onboarding.application.ports.out.recorrido.RecorridoDeBienvenidaPort.FilaDelRecorrido;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renaser.os.shared.domain.Clock;

/**
 * Spec 045 (D-330). Cuándo sale el recorrido de bienvenida y en qué paso va.
 *
 * <p>Sale solo si: (1) no terminó —las cuentas anteriores quedaron terminadas por la migración V180 (D2: solo cuentas nuevas)—, y
 * (2) la persona tiene su programa propio activado y no está suspendida. Así el aprendiz lo ve al terminar su ingreso (el programa se
 * activa al firmar los términos, spec 036) y el personal solo si hace su programa propio (D4).
 */
@Service
public class RecorridoDeBienvenidaService implements RecorridoDeBienvenidaUseCase {

    private final RecorridoDeBienvenidaPort recorridos;
    private final ParticipacionProgramaFinder participaciones;
    private final Clock clock;

    public RecorridoDeBienvenidaService(RecorridoDeBienvenidaPort recorridos, ParticipacionProgramaFinder participaciones,
                                        Clock clock) {
        this.recorridos = recorridos;
        this.participaciones = participaciones;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public VistaDelRecorrido de(UserId persona) {
        FilaDelRecorrido fila = recorridos.de(persona).orElse(null);
        boolean terminado = fila != null && fila.terminadoEn() != null;
        boolean conPrograma = participaciones.deParticipante(persona)
                .map(p -> p.activado() && !p.suspendido())
                .orElse(false);
        return new VistaDelRecorrido(!terminado && conPrograma, fila == null ? 1 : fila.paso(),
                fila == null ? null : fila.terminadoEn(), fila == null ? null : fila.como());
    }

    @Override
    @Transactional
    public VistaDelRecorrido avanzar(UserId persona, int paso) {
        if (paso < 1 || paso > ULTIMO_PASO) {
            throw new IllegalArgumentException("El paso del recorrido va de 1 a " + ULTIMO_PASO + ".");
        }
        recorridos.guardarPaso(persona, paso, clock.now());
        return de(persona);
    }

    @Override
    @Transactional
    public VistaDelRecorrido terminar(UserId persona, ComoTermino como) {
        if (como == null || como == ComoTermino.CUENTA_ANTERIOR) {
            throw new IllegalArgumentException("Termina con PUBLICO, LISTO o SALTO.");
        }
        recorridos.terminar(persona, como, clock.now());
        return de(persona);
    }
}
