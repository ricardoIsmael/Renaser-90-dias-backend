package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase.ActivateProgramCommand;
import com.renaser.os.users.application.ports.out.participante.ListarParticipantesConProgramaActivoPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Postgres real (Testcontainers), no mocks — es el ciclo completo que exige el
 * encargo de D-66: "activar -> avanzar -> ver dia y fase correctos". El servicio se
 * construye a mano con {@link FixedClock} (mismo patron que
 * {@code AccountDeletionIntegrationTest}) para poder simular el paso de varios dias sin
 * depender del reloj real.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RelojProgramaIntegrationTest {

    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    @Autowired
    private SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    @Autowired
    private ListarParticipantesConProgramaActivoPort listarParticipantesConProgramaActivoPort;

    private UserId crearTraineeConParticipacionPausada(FixedClock enElAlta) {
        UserId id = UserId.of(UUID.randomUUID());
        User trainee = User.rehydrate(id, new Email(id + "@renaser.com"), UserRole.TRAINEE, UserStatus.ACTIVE,
                "Fixture " + id, null, null, null, null);
        saveUserPort.save(trainee);
        saveParticipacionProgramaPort.save(ParticipacionPrograma.inscribirTraineeAprobado(id, enElAlta));
        return id;
    }

    private RelojProgramaService servicioEn(FixedClock clock) {
        return new RelojProgramaService(new RequireActiveUserGuard(loadUserPort), loadParticipacionProgramaPort,
                saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort, clock);
    }

    /** Corregido tras revision del dueño del proyecto: HOY no es una opcion valida (el
     * reloj avanza a medianoche) — MAÑANA es la primera fecha aceptada. */
    @Test
    void activarConManianaQuedaPausadoHastaQueElCronDetectaQueLlegoElDia() {
        FixedClock hoy = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
        UserId traineeId = crearTraineeConParticipacionPausada(hoy);

        servicioEn(hoy).activarPrograma(new ActivateProgramCommand(traineeId, hoy.today().plusDays(1)));

        ParticipacionPrograma trasActivar = loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow();
        assertThat(trasActivar.diaPrograma()).isZero(); // todavia no es su dia
        assertThat(trasActivar.estaActivado()).isTrue(); // pero ya eligio: no esta PAUSADO
        assertThat(trasActivar.fechaInicio()).isEqualTo(hoy.today().plusDays(1));

        // Reintentar con la MISMA fecha (ej. timeout de red) es un no-op, no un error.
        servicioEn(hoy).activarPrograma(new ActivateProgramCommand(traineeId, hoy.today().plusDays(1)));
        assertThat(loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow().diaPrograma()).isZero();

        // El cron, corrido el dia siguiente (cuando llega la fecha elegida), avanza a dia 1.
        FixedClock maniana = FixedClock.at(hoy.now().plusSeconds(86400));
        var resultado = servicioEn(maniana).avanzarParticipantesActivos();
        assertThat(resultado.avanzados()).isEqualTo(1);
        ParticipacionPrograma enDiaUno = loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow();
        assertThat(enDiaUno.diaPrograma()).isEqualTo(1);
        assertThat(enDiaUno.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);

        // Correr el cron el MISMO dia calendario de nuevo no debe adelantar (idempotencia).
        var resultadoMismoDia = servicioEn(maniana).avanzarParticipantesActivos();
        assertThat(resultadoMismoDia.avanzados()).isZero();
        assertThat(loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow().diaPrograma())
                .isEqualTo(1);
    }

    @Test
    void activarConFechaFuturaNoAvanzaHastaQueLlegaYLuegoElCronLaAvanzaDiaADia() {
        FixedClock enElAlta = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
        UserId traineeId = crearTraineeConParticipacionPausada(enElAlta);
        FixedClock fechaElegida = FixedClock.at(enElAlta.now().plusSeconds(2 * 86400)); // hoy + 2

        servicioEn(enElAlta).activarPrograma(new ActivateProgramCommand(traineeId, fechaElegida.today()));

        ParticipacionPrograma pausadaTodavia = loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow();
        assertThat(pausadaTodavia.diaPrograma()).isZero(); // todavia no llego la fecha
        assertThat(pausadaTodavia.estaActivado()).isTrue(); // pero ya eligio: no esta PAUSADO

        // Un dia antes de que llegue: el cron no debe hacer nada.
        FixedClock unDiaAntes = FixedClock.at(fechaElegida.now().minusSeconds(86400));
        servicioEn(unDiaAntes).avanzarParticipantesActivos();
        assertThat(loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow().diaPrograma()).isZero();

        // Llega el dia elegido: el cron avanza a dia 1.
        servicioEn(fechaElegida).avanzarParticipantesActivos();
        assertThat(loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow().diaPrograma())
                .isEqualTo(1);

        // Simula los 7 dias siguientes: en el dia 8 la fase debe pasar a FASE II.
        FixedClock cursor = fechaElegida;
        for (int i = 0; i < 7; i++) {
            cursor = FixedClock.at(cursor.now().plusSeconds(86400));
            servicioEn(cursor).avanzarParticipantesActivos();
        }
        ParticipacionPrograma enDiaOcho = loadParticipacionProgramaPort.byParticipanteId(traineeId).orElseThrow();
        assertThat(enDiaOcho.diaPrograma()).isEqualTo(8);
        assertThat(enDiaOcho.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
    }

    @Test
    void avanzarParticipantesActivosIgnoraUnParticipantePausadoQueNuncaEligio() {
        FixedClock hoy = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
        UserId pausadoId = crearTraineeConParticipacionPausada(hoy);

        var resultado = servicioEn(hoy).avanzarParticipantesActivos();

        assertThat(resultado.avanzados()).isZero();
        assertThat(loadParticipacionProgramaPort.byParticipanteId(pausadoId).orElseThrow().diaPrograma()).isZero();
    }
}
