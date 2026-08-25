package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia.EventoAudiencia;
import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia.VisorContexto;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Puerto directo de canViewEvent() (audience.ts, repo viejo). */
class ResolverAudienciaTest {

    @Test
    void adminSiempreVeSinImportarLaAudiencia() {
        VisorContexto admin = new VisorContexto(RolUsuario.ADMIN, 0, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.NIVEL_MINIMO, 99, null, Set.of(), null);
        assertThat(ResolverAudiencia.puedeVer(admin, audiencia, false)).isTrue();
    }

    @Test
    void alchemistSiempreVe() {
        VisorContexto alchemist = new VisorContexto(RolUsuario.ALCHEMIST, 0, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.ROLES, null, null, Set.of(RolUsuario.MENTOR), null);
        assertThat(ResolverAudiencia.puedeVer(alchemist, audiencia, false)).isTrue();
    }

    @Test
    void todosSiempreVisible() {
        VisorContexto trainee = new VisorContexto(RolUsuario.TRAINEE, 0, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.TODOS, null, null, Set.of(), null);
        assertThat(ResolverAudiencia.puedeVer(trainee, audiencia, false)).isTrue();
    }

    @Test
    void nivelMinimoRequiereRangoSuficiente() {
        VisorContexto rangoBajo = new VisorContexto(RolUsuario.TRAINEE, 1, null);
        VisorContexto rangoAlto = new VisorContexto(RolUsuario.TRAINEE, 3, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.NIVEL_MINIMO, 2, null, Set.of(), null);

        assertThat(ResolverAudiencia.puedeVer(rangoBajo, audiencia, false)).isFalse();
        assertThat(ResolverAudiencia.puedeVer(rangoAlto, audiencia, false)).isTrue();
    }

    @Test
    void cursoDependeDelAccesoResuelto() {
        VisorContexto trainee = new VisorContexto(RolUsuario.TRAINEE, 0, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.CURSO, null, "curso-1", Set.of(), null);

        assertThat(ResolverAudiencia.puedeVer(trainee, audiencia, false)).isFalse();
        assertThat(ResolverAudiencia.puedeVer(trainee, audiencia, true)).isTrue();
    }

    @Test
    void rolesRequiereQueElRolDelVisorEsteEnLaLista() {
        VisorContexto mentor = new VisorContexto(RolUsuario.MENTOR, 0, null);
        VisorContexto trainee = new VisorContexto(RolUsuario.TRAINEE, 0, null);
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.ROLES, null, null, Set.of(RolUsuario.MENTOR), null);

        assertThat(ResolverAudiencia.puedeVer(mentor, audiencia, false)).isTrue();
        assertThat(ResolverAudiencia.puedeVer(trainee, audiencia, false)).isFalse();
    }

    @Test
    void celulaRequiereMismaCelulaQueElVisor() {
        UUID celula = UUID.randomUUID();
        VisorContexto miembro = new VisorContexto(RolUsuario.TRAINEE, 0, celula);
        VisorContexto otro = new VisorContexto(RolUsuario.TRAINEE, 0, UUID.randomUUID());
        EventoAudiencia audiencia = new EventoAudiencia(TipoAudiencia.CELULA, null, null, Set.of(), celula);

        assertThat(ResolverAudiencia.puedeVer(miembro, audiencia, false)).isTrue();
        assertThat(ResolverAudiencia.puedeVer(otro, audiencia, false)).isFalse();
    }
}
