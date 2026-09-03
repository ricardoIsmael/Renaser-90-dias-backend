package com.renaser.os.community.domain.model.celula;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CelulaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final CelulaId ID = CelulaId.of(UUID.randomUUID());

    private static Celula nueva() {
        return Celula.crear(ID, "Celula 1", CohorteId.of(UUID.randomUUID()), null, CLOCK.now());
    }

    @Test
    void crearNaceSinMentor() {
        Celula c = nueva();
        assertThat(c.id()).isEqualTo(ID);
        assertThat(c.mentorId()).isNull();
    }

    @Test
    void nombreVacioEsInvalido() {
        assertThatThrownBy(() -> Celula.crear(ID, " ", CohorteId.of(UUID.randomUUID()), null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarMentorLoDejaListo() {
        Celula c = nueva();
        UserId mentor = UserId.of(UUID.randomUUID());
        c.asignarMentor(mentor, CLOCK.now());
        assertThat(c.mentorId()).isEqualTo(mentor);
    }

    @Test
    void quitarMentorLoDejaEnNull() {
        Celula c = nueva();
        c.asignarMentor(UserId.of(UUID.randomUUID()), CLOCK.now());
        c.quitarMentor(CLOCK.now());
        assertThat(c.mentorId()).isNull();
    }

    @Test
    void programarSesionRequiereFechaNoNula() {
        Celula c = nueva();
        assertThatThrownBy(() -> c.programarSesion(null, CLOCK.now())).isInstanceOf(NullPointerException.class);
    }
}
