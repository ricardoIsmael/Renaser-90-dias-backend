package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El barrido horario de D-243 (regla 02): paginado, una cuenta que falla no frena a las demas, y el
 * corte es un instante. El reloj se fija a las 03:30 UTC, que en Lima es todavia el dia anterior
 * (22:30): el corte no puede depender de la fecha de nadie.
 */
@ExtendWith(MockitoExtension.class)
class BarridoDeCuentasCerradasServiceTest {

    /** 2026-10-31 03:30 UTC = 2026-10-30 22:30 en Lima. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-31T03:30:00Z"));
    private static final PlazoDeGracia PLAZO = new PlazoDeGracia(30);

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private BorradoDefinitivoService borrado;

    private BarridoDeCuentasCerradasService service() {
        return new BarridoDeCuentasCerradasService(loadUserPort, borrado, CLOCK, PLAZO);
    }

    @Test
    @DisplayName("el corte es ahora menos 30 x 24 h, en instantes: la cuenta cerrada a las 03:00 UTC del 1/10 ya entra")
    void corteEnInstantes() {
        Instant corteEsperado = Instant.parse("2026-10-01T03:30:00Z");
        when(loadUserPort.cerradasVencidas(eq(corteEsperado), isNull(), eq(BarridoDeCuentasCerradasService.TAMANO_LOTE)))
                .thenReturn(List.of());

        var resultado = service().purgeExpired();

        assertThat(resultado.purgadas()).isZero();
        verify(loadUserPort).cerradasVencidas(corteEsperado, null, BarridoDeCuentasCerradasService.TAMANO_LOTE);
    }

    @Test
    @DisplayName("pagina de a TAMANO_LOTE siguiendo por id, y recorre todas las paginas")
    void paginaPorId() {
        List<UserId> primera = ids(BarridoDeCuentasCerradasService.TAMANO_LOTE);
        List<UserId> segunda = ids(3);
        when(loadUserPort.cerradasVencidas(any(), isNull(), anyInt())).thenReturn(primera);
        when(loadUserPort.cerradasVencidas(any(), eq(primera.getLast()), anyInt())).thenReturn(segunda);
        when(loadUserPort.cerradasVencidas(any(), eq(segunda.getLast()), anyInt())).thenReturn(List.of());

        var resultado = service().purgeExpired();

        assertThat(resultado.purgadas()).isEqualTo(BarridoDeCuentasCerradasService.TAMANO_LOTE + 3);
        assertThat(resultado.fallidas()).isZero();
    }

    @Test
    @DisplayName("una cuenta que falla no frena a las demas y se cuenta como fallida")
    void unaQueFallaNoFrena() {
        List<UserId> lote = ids(3);
        when(loadUserPort.cerradasVencidas(any(), isNull(), anyInt())).thenReturn(lote);
        when(loadUserPort.cerradasVencidas(any(), eq(lote.getLast()), anyInt())).thenReturn(List.of());
        // lenient: con stubs estrictos, llamar a borrar() con OTRA cuenta que la del stub tambien lanza.
        lenient().doThrow(new IllegalStateException("FK inesperada"))
                .when(borrado).borrar(lote.get(1), RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);

        var resultado = service().purgeExpired();

        assertThat(resultado.purgadas()).isEqualTo(2);
        assertThat(resultado.fallidas()).isEqualTo(1);
        verify(borrado).borrar(lote.get(2), RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);
    }

    private static List<UserId> ids(int cuantos) {
        List<UserId> ids = new ArrayList<>();
        IntStream.range(0, cuantos).forEach(i -> ids.add(UserId.of(UUID.randomUUID())));
        return ids;
    }
}
