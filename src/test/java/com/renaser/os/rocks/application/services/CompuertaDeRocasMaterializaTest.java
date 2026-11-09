package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #60: a quien tiene sus tres objetivos en el diagnostico pero todavia no abrio Plan, la compuerta le decia «cerrada» porque leia las
 * Rocas Maestras sin crearlas, y SER no le proponia planes. Ahora las lee como Plan ({@code misRocasMaestras}), que crea las del diagnostico.
 */
class CompuertaDeRocasMaterializaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-11-09T15:00:00Z");

    private final LoadRocaMaestraPort guardadas = mock(LoadRocaMaestraPort.class);
    private final ConsultarRocasMaestrasUseCase comoPlan = mock(ConsultarRocasMaestrasUseCase.class);
    private final CompuertaDeRocasService compuerta = new CompuertaDeRocasService(guardadas, mock(LoadRocaSemanalPort.class),
            mock(ConsultarProgresoParticipanteRocksPort.class), FixedClock.at(AHORA), comoPlan);

    private static List<RocaMaestra> lasTresDelDiagnostico() {
        return Arrays.stream(EjeObjetivo.values())
                .map(eje -> RocaMaestra.delDiagnostico(RocaMaestraId.of(UUID.randomUUID()), APRENDIZ, eje, "Objetivo", AHORA))
                .toList();
    }

    @Test
    @DisplayName("Objetivos del diagnostico sin abrir Plan: la compuerta los crea al leerlos y queda abierta")
    void abreConLosDelDiagnostico() { // spec 038 FR-014
        when(guardadas.deParticipante(APRENDIZ)).thenReturn(List.of());
        when(comoPlan.misRocasMaestras(APRENDIZ)).thenReturn(lasTresDelDiagnostico());

        assertThat(compuerta.rocasMaestrasCompletas(APRENDIZ)).isTrue();
    }

    @Test
    @DisplayName("Sin programa andando no se crea nada: se lee lo que haya, como antes")
    void sinProgramaLeeLoGuardado() {
        when(comoPlan.misRocasMaestras(APRENDIZ)).thenThrow(new NoSuchElementException("Participante no encontrado"));
        when(guardadas.deParticipante(APRENDIZ)).thenReturn(List.of());
        assertThat(compuerta.rocasMaestrasCompletas(APRENDIZ)).isFalse();
    }

    @Test
    @DisplayName("Cuenta suspendida: no se crea nada y se lee lo que haya")
    void suspendidaLeeLoGuardado() {
        when(comoPlan.misRocasMaestras(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        when(guardadas.deParticipante(APRENDIZ)).thenReturn(lasTresDelDiagnostico());
        assertThat(compuerta.rocasMaestrasCompletas(APRENDIZ)).isTrue();
    }
}
