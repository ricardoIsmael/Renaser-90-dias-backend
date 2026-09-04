package com.renaser.os.users.infrastructure.adapter.out.persistence.ajustediaprograma;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.ajustediaprograma.LoadUltimoAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
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

/** Bitacora de ajustes del dia (V21, D-82) contra Postgres real. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AjusteDiaProgramaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-03T15:00:00Z"));

    @Autowired
    private SaveUserPort userAdapter;
    @Autowired
    private SaveParticipacionProgramaPort saveParticipacionPort;
    @Autowired
    private SaveAjusteDiaProgramaPort savePort;
    @Autowired
    private LoadUltimoAjusteDiaProgramaPort loadPort;

    private UserId crearUsuario(UserRole role) {
        UserId id = UserId.of(UUID.randomUUID());
        userAdapter.save(User.rehydrate(id, new Email(id + "@renaser.com"), role, UserStatus.ACTIVE,
                "Fixture " + id, null, null, null, null));
        return id;
    }

    private UserId crearParticipante() {
        UserId id = crearUsuario(UserRole.TRAINEE);
        saveParticipacionPort.save(ParticipacionPrograma.activarSeguimientoPersonal(id, CLOCK));
        return id;
    }

    @Test
    void guardaYRecuperaUnAjusteConSusCuatroContadores() {
        UserId participante = crearParticipante();
        UserId admin = crearUsuario(UserRole.ADMIN);

        savePort.save(AjusteDiaPrograma.registrar(UUID.randomUUID(), participante, 40, 34, 0, 6, "Viaje", admin, CLOCK));

        AjusteDiaPrograma leido = loadPort.ultimoDe(participante).orElseThrow();
        assertThat(leido.participanteId()).isEqualTo(participante);
        assertThat(leido.ajustadoPor()).isEqualTo(admin);
        assertThat(leido.diaAnterior()).isEqualTo(40);
        assertThat(leido.diaNuevo()).isEqualTo(34);
        assertThat(leido.diasAjusteAnterior()).isZero();
        assertThat(leido.diasAjusteNuevo()).isEqualTo(6);
        assertThat(leido.motivo()).isEqualTo("Viaje");
    }

    @Test
    void unParticipanteSinAjustesDevuelveVacio() {
        assertThat(loadPort.ultimoDe(crearParticipante())).isEmpty();
    }

    /**
     * Append-only (V21): un segundo ajuste NO pisa al primero, se apila. La lectura del
     * panel devuelve el mas reciente, y el anterior sigue existiendo para auditar.
     */
    @Test
    void unSegundoAjusteNoPisaAlPrimeroYSeDevuelveElMasReciente() {
        UserId participante = crearParticipante();
        UserId admin = crearUsuario(UserRole.ADMIN);
        savePort.save(AjusteDiaPrograma.registrar(UUID.randomUUID(), participante, 40, 34, 0, 6, "Primer viaje", admin, CLOCK));

        FixedClock despues = FixedClock.at(CLOCK.now().plusSeconds(86_400));
        savePort.save(AjusteDiaPrograma.registrar(UUID.randomUUID(), participante, 35, 30, 6, 11, "Segundo viaje", admin, despues));

        AjusteDiaPrograma ultimo = loadPort.ultimoDe(participante).orElseThrow();
        assertThat(ultimo.motivo()).isEqualTo("Segundo viaje");
        assertThat(ultimo.diasAjusteNuevo()).isEqualTo(11);
    }

    /** El ajuste de un participante no se filtra al detalle de otro. */
    @Test
    void elAjusteDeUnParticipanteNoApareceEnOtro() {
        UserId ajustado = crearParticipante();
        UserId intacto = crearParticipante();
        UserId admin = crearUsuario(UserRole.ADMIN);
        savePort.save(AjusteDiaPrograma.registrar(UUID.randomUUID(), ajustado, 40, 34, 0, 6, "Viaje", admin, CLOCK));

        assertThat(loadPort.ultimoDe(intacto)).isEmpty();
    }
}
