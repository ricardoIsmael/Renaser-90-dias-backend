package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.plataforma.domain.model.estado.CambioDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El «un solo interruptor» sobre tres roles (spec 034, D-299): cómo se lee y cómo se cambia. Sin Spring. */
class CodigoRenaserEnFichasTest {

    private static final Set<UserRole> LOS_TRES = Set.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD);

    @Test
    @DisplayName("sin fila (de fábrica) está oculto para los tres y no es mixto")
    void sinFilaEstaOculto() {
        var estado = CodigoRenaserEnFichas.estadoDe(Optional.empty());

        assertThat(estado.visible()).isFalse();
        assertThat(estado.mixto()).isFalse();
        assertThat(estado.porRol()).containsOnlyKeys(LOS_TRES).containsValues(false, false, false);
    }

    @Test
    @DisplayName("visible = prendida y ninguno de los tres apagado; mixto = algunos sí y otros no (visible es falso)")
    void visibleOculteOMixto() {
        assertThat(CodigoRenaserEnFichas.estadoDe(Optional.of(fila(true, Set.of()))).visible()).isTrue();

        var mixto = CodigoRenaserEnFichas.estadoDe(Optional.of(fila(true, Set.of(UserRole.MENTOR))));
        assertThat(mixto.visible()).isFalse();
        assertThat(mixto.mixto()).isTrue();
        assertThat(mixto.porRol()).containsEntry(UserRole.MENTOR, false).containsEntry(UserRole.MENTOR_LEAD, true);

        var oculto = CodigoRenaserEnFichas.estadoDe(Optional.of(fila(true, LOS_TRES)));
        assertThat(oculto.visible()).isFalse();
        assertThat(oculto.mixto()).isFalse();
    }

    @Test
    @DisplayName("apagada para todos por Soporte TI se lee oculta, no mixta")
    void apagadaParaTodos() {
        var estado = CodigoRenaserEnFichas.estadoDe(Optional.of(fila(false, Set.of())));

        assertThat(estado.visible()).isFalse();
        assertThat(estado.mixto()).isFalse();
    }

    @Test
    @DisplayName("pedir lo que ya rige no escribe: sin fila + ocultar y fila visible + mostrar ya rigen")
    void yaRige() {
        assertThat(CodigoRenaserEnFichas.yaRige(false, Optional.empty())).isTrue();
        assertThat(CodigoRenaserEnFichas.yaRige(true, Optional.empty())).isFalse();
        assertThat(CodigoRenaserEnFichas.yaRige(true, Optional.of(fila(true, Set.of())))).isTrue();
        assertThat(CodigoRenaserEnFichas.yaRige(false, Optional.of(fila(true, Set.of())))).isFalse();
        assertThat(CodigoRenaserEnFichas.yaRige(false, Optional.of(fila(true, LOS_TRES)))).isTrue();
    }

    @Test
    @DisplayName("un estado mixto nunca «ya rige»: cualquier cambio de Administración deja los tres iguales")
    void elMixtoSeIguala() {
        Optional<EstadoDeFuncionalidad> mixto = Optional.of(fila(true, Set.of(UserRole.MENTOR)));

        assertThat(CodigoRenaserEnFichas.yaRige(true, mixto)).isFalse();
        assertThat(CodigoRenaserEnFichas.yaRige(false, mixto)).isFalse();
    }

    @Test
    @DisplayName("el cambio hacia visible prende la funcionalidad, quita a los tres y conserva lo demás y la versión que leyó")
    void cambioHaciaVisible() {
        Optional<EstadoDeFuncionalidad> anterior = Optional.of(new EstadoDeFuncionalidad("radar.fichas", true,
                Set.of(UserRole.MENTOR, UserRole.TRAINEE), "aviso", 9, UserId.of(UUID.randomUUID()),
                Instant.parse("2026-10-31T10:00:00Z")));

        CambioDeFuncionalidad cambio = CodigoRenaserEnFichas.cambioHacia(true, anterior);

        assertThat(cambio.clave()).isEqualTo("radar.fichas");
        assertThat(cambio.prendidaGlobal()).isTrue();
        assertThat(cambio.apagadaPara()).containsExactly(UserRole.TRAINEE);
        assertThat(cambio.aviso()).isEqualTo("aviso");
        assertThat(cambio.versionEsperada()).isEqualTo(9);
        assertThat(cambio.motivo()).isEqualTo(CodigoRenaserEnFichas.MOTIVO_DEL_CAMBIO);
    }

    @Test
    @DisplayName("el cambio hacia oculto, sin fila, apaga a los tres con la funcionalidad prendida (Administración sigue viéndola) y versión 0")
    void cambioHaciaOcultoSinFila() {
        CambioDeFuncionalidad cambio = CodigoRenaserEnFichas.cambioHacia(false, Optional.empty());

        assertThat(cambio.prendidaGlobal()).isTrue();
        assertThat(cambio.apagadaPara()).containsExactlyInAnyOrderElementsOf(LOS_TRES);
        assertThat(cambio.versionEsperada()).isZero();
        assertThat(cambio.aviso()).isNull();
    }

    @Test
    @DisplayName("el interruptor gobierna a los tres roles de quien acompaña y a nadie de Administración")
    void losRoles() {
        assertThat(CodigoRenaserEnFichas.ROLES).containsExactlyInAnyOrderElementsOf(LOS_TRES);
        assertThat(CodigoRenaserEnFichas.ROLES).doesNotContain(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.IT_SUPPORT,
                UserRole.TRAINEE);
    }

    private static EstadoDeFuncionalidad fila(boolean prendida, Set<UserRole> apagadaPara) {
        return new EstadoDeFuncionalidad("radar.fichas", prendida, apagadaPara, null, 3, UserId.of(UUID.randomUUID()),
                Instant.parse("2026-10-31T10:00:00Z"));
    }
}
