package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El tope de aprendices de cada grupo, para el cupo del mentor (spec 056, FR-003). */
class CupoDeLosGruposServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-18T15:00:00Z");
    private static final CohorteId COHORTE = CohorteId.of(UUID.fromString("00000000-0000-0000-0000-0000000c0001"));
    private static final CohorteId OTRA_COHORTE = CohorteId.of(UUID.fromString("00000000-0000-0000-0000-0000000c0002"));

    private final LoadCelulaPort celulas = mock(LoadCelulaPort.class);
    private final LoadPoliticaMentoriaPort politicas = mock(LoadPoliticaMentoriaPort.class);
    private final CupoDeLosGruposService servicio = new CupoDeLosGruposService(celulas, politicas);

    private static Celula grupo(int sufijo, CohorteId cohorte, TipoCelula tipo, Integer capacidad) {
        return Celula.rehydrate(CelulaId.of(UUID.fromString("00000000-0000-0000-0000-00000000f0" + String.format("%02d", sufijo))),
                "Grupo " + sufijo, null, cohorte, null, null, AHORA, AHORA, tipo, capacidad);
    }

    private static UUID id(Celula celula) {
        return celula.id().value();
    }

    @Test
    @DisplayName("spec 056 FR-003: un grupo sin tope propio usa la capacidad de la política de su cohorte")
    void usaLaPoliticaDeLaCohorte() {
        Celula fenix = grupo(1, COHORTE, TipoCelula.REGULAR, null);
        when(celulas.todas()).thenReturn(List.of(fenix));
        when(politicas.porCohorte(COHORTE)).thenReturn(Optional.of(PoliticaMentoria.rehydrate(COHORTE, 12,
                CadenciaRotacion.MENSUAL, "America/Lima", 8, 3, null, 1)));

        assertThat(servicio.maximosDe(List.of(id(fenix)))).isEqualTo(Map.of(id(fenix), 12));
    }

    @Test
    @DisplayName("spec 056 FR-003: el tope que se le fijó al grupo gana sobre el de la política")
    void ganaElTopeDelGrupo() {
        Celula aurora = grupo(2, COHORTE, TipoCelula.REGULAR, 15);
        when(celulas.todas()).thenReturn(List.of(aurora));
        when(politicas.porCohorte(any())).thenReturn(Optional.empty());

        assertThat(servicio.maximosDe(List.of(id(aurora)))).isEqualTo(Map.of(id(aurora), 15));
    }

    @Test
    @DisplayName("spec 056 FR-003: sin fila de política (una cohorte nueva) vale la de por defecto: 10")
    void sinFilaDePolitica() {
        Celula brisa = grupo(3, OTRA_COHORTE, TipoCelula.REGULAR, null);
        when(celulas.todas()).thenReturn(List.of(brisa));
        when(politicas.porCohorte(OTRA_COHORTE)).thenReturn(Optional.empty());

        assertThat(servicio.maximosDe(List.of(id(brisa)))).isEqualTo(Map.of(id(brisa), PoliticaMentoria.CAPACIDAD_POR_DEFECTO));
    }

    @Test
    @DisplayName("spec 056 FR-003: la recepción no tiene tope y no figura; un id que no existe tampoco")
    void laRecepcionYLosQueNoExistenNoFiguran() {
        Celula recepcion = grupo(4, COHORTE, TipoCelula.RECEPCION, null);
        Celula fenix = grupo(1, COHORTE, TipoCelula.REGULAR, null);
        when(celulas.todas()).thenReturn(List.of(recepcion, fenix));
        when(politicas.porCohorte(any())).thenReturn(Optional.empty());

        assertThat(servicio.maximosDe(List.of(id(recepcion), id(fenix), UUID.randomUUID()))).containsOnlyKeys(id(fenix));
    }

    @Test
    @DisplayName("spec 056 FR-003: solo responde por los grupos pedidos, y lee la política una vez por cohorte")
    void soloLosPedidosYUnaPoliticaPorCohorte() {
        Celula uno = grupo(1, COHORTE, TipoCelula.REGULAR, null);
        Celula dos = grupo(2, COHORTE, TipoCelula.REGULAR, null);
        Celula ajeno = grupo(5, OTRA_COHORTE, TipoCelula.REGULAR, null);
        when(celulas.todas()).thenReturn(List.of(uno, dos, ajeno));
        when(politicas.porCohorte(any())).thenReturn(Optional.empty());

        Map<UUID, Integer> maximos = servicio.maximosDe(List.of(id(uno), id(dos)));

        assertThat(maximos).containsOnlyKeys(id(uno), id(dos));
        verify(politicas, times(1)).porCohorte(COHORTE);
        verify(politicas, never()).porCohorte(OTRA_COHORTE);
    }

    @Test
    @DisplayName("spec 056 FR-003: sin grupos pedidos no lee nada")
    void sinPedidos() {
        assertThat(servicio.maximosDe(List.of())).isEmpty();
        verify(celulas, never()).todas();
    }
}
