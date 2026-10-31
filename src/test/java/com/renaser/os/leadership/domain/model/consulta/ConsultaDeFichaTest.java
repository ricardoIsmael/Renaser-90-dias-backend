package com.renaser.os.leadership.domain.model.consulta;

import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaDeFichaTest {

    /** 03:00 UTC del lunes 19: en Lima todavía es el domingo 18 (regla 02). */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-19T03:00:00Z"));
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UserId LIDER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId LUISA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID GRUPO = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final LocalDate SEMANA = LocalDate.of(2026, 10, 10);

    private static Contenido contenido(UserRole rol, UserId mentor) {
        return new Contenido(LIDER, rol, ANA, GRUPO, mentor, SEMANA);
    }

    @Test
    @DisplayName("registra quien, con que rol, a quien, de que grupo, la semana y el instante exacto del reloj")
    void registra() {
        ConsultaDeFicha consulta = ConsultaDeFicha.registrar(ID, contenido(UserRole.MENTOR_LEAD, LUISA), CLOCK);

        assertThat(consulta.id()).isEqualTo(ID);
        assertThat(consulta.consultorId()).isEqualTo(LIDER);
        assertThat(consulta.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
        assertThat(consulta.personaId()).isEqualTo(ANA);
        assertThat(consulta.grupoId()).isEqualTo(GRUPO);
        assertThat(consulta.mentorId()).isEqualTo(LUISA);
        assertThat(consulta.semanaDesde()).isEqualTo(SEMANA);
        assertThat(consulta.consultadaEn()).isEqualTo(Instant.parse("2026-10-19T03:00:00Z"));
    }

    @Test
    @DisplayName("un grupo sin mentor vigente se registra igual, con el mentor en null: a nadie hay que avisarle")
    void sinMentor() {
        assertThat(ConsultaDeFicha.registrar(ID, contenido(UserRole.ADMIN, null), CLOCK).mentorId()).isNull();
    }

    @Test
    @DisplayName("solo el liderazgo consulta: un aprendiz o un mentor como consultor se rechaza")
    void soloElLiderazgo() {
        assertThatThrownBy(() -> ConsultaDeFicha.registrar(ID, contenido(UserRole.TRAINEE, LUISA), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsultaDeFicha.registrar(ID, contenido(UserRole.MENTOR, LUISA), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        for (UserRole rol : new UserRole[]{UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST}) {
            assertThat(ConsultaDeFicha.registrar(ID, contenido(rol, LUISA), CLOCK).rolConsultor()).isEqualTo(rol);
        }
    }

    @Test
    @DisplayName("sin consultor, persona o semana no hay consulta")
    void obligatorios() {
        assertThatThrownBy(() -> new Contenido(null, UserRole.ADMIN, ANA, GRUPO, LUISA, SEMANA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Contenido(LIDER, UserRole.ADMIN, null, GRUPO, LUISA, SEMANA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Contenido(LIDER, UserRole.ADMIN, ANA, GRUPO, LUISA, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("spec 021: una persona que no esta en ningun grupo se registra con el grupo y el mentor en null")
    void sinGrupo() {
        ConsultaDeFicha consulta = ConsultaDeFicha.registrar(ID,
                new Contenido(LIDER, UserRole.MENTOR_LEAD, ANA, null, null, SEMANA), CLOCK);

        assertThat(consulta.grupoId()).isNull();
        assertThat(consulta.mentorId()).isNull();
        assertThat(consulta.personaId()).isEqualTo(ANA);
    }

    @Test
    @DisplayName("append-only: la clase no tiene un solo metodo que la cambie")
    void sinMutadores() {
        assertThat(Arrays.stream(ConsultaDeFicha.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set") || nombre.startsWith("cambiar")
                        || nombre.startsWith("editar") || nombre.startsWith("borrar"));
    }
}
