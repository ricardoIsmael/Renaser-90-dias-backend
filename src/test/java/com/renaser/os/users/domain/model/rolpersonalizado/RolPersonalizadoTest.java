package com.renaser.os.users.domain.model.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConflictoDeRolPersonalizadoException;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas de una fila de rol personalizado (spec 002, FR-029 y FR-033). Sin Spring. */
class RolPersonalizadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private final UUID id = UUID.fromString("0f8d5c56-3f5b-4bb8-9f7a-2f1d1c1b0a01");
    private final UserId soporte = UserId.of(UUID.fromString("0f8d5c56-3f5b-4bb8-9f7a-2f1d1c1b0a02"));

    private RolPersonalizado mentorJunior() {
        return RolPersonalizado.crear(id, new Datos("  Mentor   junior ", " Sin eventos ", UserRole.MENTOR,
                Set.of("muro", "chat")), soporte, AHORA);
    }

    @Test
    @DisplayName("nace activo, en la versión 1, con el nombre limpio y su clave rp:<id>")
    void naceActivo() {
        RolPersonalizado rol = mentorJunior();

        assertThat(rol.activo()).isTrue();
        assertThat(rol.version()).isEqualTo(1L);
        assertThat(rol.nombre()).isEqualTo("Mentor junior");
        assertThat(rol.descripcion()).isEqualTo("Sin eventos");
        assertThat(rol.clave()).isEqualTo("rp:" + id);
        assertThat(rol.funcionalidades()).containsExactly("chat", "muro");
        assertThat(rol.creadoPor()).isEqualTo(soporte);
        assertThat(rol.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("nunca hereda de Soporte TI (FR-029): 400")
    void nuncaDeSoporteTi() {
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("Técnico", null, UserRole.IT_SUPPORT,
                Set.of()), soporte, AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Soporte TI");
    }

    @Test
    @DisplayName("sin rol base, sin nombre, con un nombre de más de 60 o una descripción de más de 280: 400")
    void datosInvalidos() {
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("Uno", null, null, Set.of()), soporte, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("  ", null, UserRole.MENTOR, Set.of()),
                soporte, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("x".repeat(61), null, UserRole.MENTOR,
                Set.of()), soporte, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("Uno", "x".repeat(281), UserRole.MENTOR,
                Set.of()), soporte, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> RolPersonalizado.crear(id, new Datos("x".repeat(60), "y".repeat(280),
                UserRole.MENTOR, Set.of()), soporte, AHORA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una clave sin forma de clave es 400; una lista vacía vale (todo lo de la base queda fuera)")
    void clavesConForma() {
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("Uno", null, UserRole.MENTOR,
                Set.of("Chat Grupos")), soporte, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RolPersonalizado.crear(id, new Datos("Uno", null, UserRole.MENTOR, null),
                soporte, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(RolPersonalizado.crear(id, new Datos("Uno", null, UserRole.MENTOR, Set.of()), soporte, AHORA)
                .funcionalidades()).isEmpty();
    }

    @Test
    @DisplayName("editar sube la versión, conserva quién lo creó y puede desactivarlo")
    void editar() {
        UserId otro = UserId.of(UUID.randomUUID());
        Instant despues = AHORA.plusSeconds(60);

        RolPersonalizado editado = mentorJunior().editar(new Datos("Mentor inicial", null, null, Set.of("muro")),
                false, 3, otro, despues);

        assertThat(editado.version()).isEqualTo(2L);
        assertThat(editado.activo()).isFalse();
        assertThat(editado.rolBase()).isEqualTo(UserRole.MENTOR);
        assertThat(editado.descripcion()).isNull();
        assertThat(editado.creadoPor()).isEqualTo(soporte);
        assertThat(editado.actualizadoPor()).isEqualTo(otro);
        assertThat(editado.actualizadoEn()).isEqualTo(despues);
    }

    @Test
    @DisplayName("con personas asignadas no cambia de rol base (FR-033): 409; sin personas, sí")
    void noCambiaDeBaseConPersonas() {
        Datos aGuia = new Datos("Mentor junior", null, UserRole.TRAINEE_GUIDE, Set.of());

        assertThatThrownBy(() -> mentorJunior().editar(aGuia, true, 1, soporte, AHORA))
                .isInstanceOf(ConflictoDeRolPersonalizadoException.class).hasMessageContaining("no cambia de rol base")
                .extracting(e -> ((ConflictoDeRolPersonalizadoException) e).codigo()).isEqualTo("ROL_CON_PERSONAS");
        assertThat(mentorJunior().editar(aGuia, true, 0, soporte, AHORA).rolBase())
                .isEqualTo(UserRole.TRAINEE_GUIDE);
        assertThat(mentorJunior().editar(new Datos("Mentor junior", null, UserRole.MENTOR, Set.of()), true, 5,
                soporte, AHORA).rolBase()).as("la misma base no es un cambio").isEqualTo(UserRole.MENTOR);
    }

    @Test
    @DisplayName("con personas asignadas no se borra (FR-033): 409")
    void noSeBorraConPersonas() {
        assertThatThrownBy(() -> mentorJunior().exigirQueSePuedaBorrar(2))
                .isInstanceOf(ConflictoDeRolPersonalizadoException.class).hasMessageContaining("2 persona")
                .extracting(e -> ((ConflictoDeRolPersonalizadoException) e).codigo()).isEqualTo("ROL_CON_PERSONAS");
        assertThatCode(() -> mentorJunior().exigirQueSePuedaBorrar(0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el nombre se compara sin mayúsculas, tildes ni espacios de más")
    void nombreComparable() {
        assertThat(mentorJunior().seLlamaIgualQue("méntor JUNIOR")).isTrue();
        assertThat(mentorJunior().seLlamaIgualQue("Mentor senior")).isFalse();
    }
}
