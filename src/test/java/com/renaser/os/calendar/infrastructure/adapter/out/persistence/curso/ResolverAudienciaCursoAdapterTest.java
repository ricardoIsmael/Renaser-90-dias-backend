package com.renaser.os.calendar.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.api.AccesoCursoFinder;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Puente hacia {@code academy.api} — no toca la base, asi que se prueba con un doble del
 * contrato publico de `academy`: lo unico propio del adaptador es el filtrado en memoria
 * de {@code filtrarConAcceso}, que es donde puede colarse un error.
 */
@ExtendWith(MockitoExtension.class)
class ResolverAudienciaCursoAdapterTest {

    private static final String CURSO_ID = "curso-1";

    @Mock
    private AccesoCursoFinder accesoCursoFinder;

    @InjectMocks
    private ResolverAudienciaCursoAdapter adapter;

    @Test
    void tieneAccesoDelegaEnElContratoPublicoDeAcademy() {
        UserId usuario = UserId.of(UUID.randomUUID());
        when(accesoCursoFinder.tieneAcceso(usuario, CURSO_ID)).thenReturn(true);

        assertThat(adapter.tieneAcceso(usuario, CURSO_ID)).isTrue();
    }

    @Test
    void filtrarConAccesoSeQuedaSoloConLosCandidatosQueDeVerdadTienenAcceso() {
        UserId conAcceso = UserId.of(UUID.randomUUID());
        UserId sinAcceso = UserId.of(UUID.randomUUID());
        UserId ajeno = UserId.of(UUID.randomUUID());
        when(accesoCursoFinder.usuariosConAcceso(CURSO_ID)).thenReturn(Set.of(conAcceso, ajeno));

        var filtrados = adapter.filtrarConAcceso(CURSO_ID, Set.of(conAcceso, sinAcceso));

        assertThat(filtrados).containsExactly(conAcceso);
    }

    @Test
    void filtrarConAccesoDevuelveVacioCuandoNadieTieneAcceso() {
        when(accesoCursoFinder.usuariosConAcceso(CURSO_ID)).thenReturn(Set.of());

        assertThat(adapter.filtrarConAcceso(CURSO_ID, Set.of(UserId.of(UUID.randomUUID())))).isEmpty();
    }
}
