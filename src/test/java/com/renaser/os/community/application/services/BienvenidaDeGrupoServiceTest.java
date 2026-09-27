package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.BienvenidaDeGrupo.Pendiente;
import com.renaser.os.community.api.BienvenidaDeGrupo.Pendientes;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.MarcaDeBienvenidaEnGrupoPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quién espera la bienvenida de grupo (D-191): solo grupo estable, con mentor, pertenencias vigentes sin marca. */
@ExtendWith(MockitoExtension.class)
class BienvenidaDeGrupoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T03:00:00Z");
    private static final CelulaId GRUPO = CelulaId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private LoadAsignacionesPort loadAsignacionesPort;
    @Mock
    private MarcaDeBienvenidaEnGrupoPort marcaPort;
    @Mock
    private AcompanamientoFinder acompanamientoFinder;
    @InjectMocks
    private BienvenidaDeGrupoService servicio;

    private final List<AsignacionCelula> asignaciones = new ArrayList<>();

    @Test
    @DisplayName("grupo estable con mentor: devuelve al mentor y solo las pertenencias vigentes SIN marca")
    void devuelveLasPendientes() {
        grupo(TipoCelula.REGULAR);
        asignar(MENTOR, FuncionAcompanamiento.MENTOR);
        AsignacionCelula ana = asignar(ANA, FuncionAcompanamiento.APRENDIZ);
        AsignacionCelula luis = asignar(LUIS, FuncionAcompanamiento.APRENDIZ);
        when(acompanamientoFinder.acompanaVigente(MENTOR, GRUPO.value(), AHORA)).thenReturn(true);
        when(marcaPort.sinBienvenida(any())).thenReturn(Set.of(ana.id().value()));

        Optional<Pendientes> pendientes = servicio.pendientes(GRUPO.value(), AHORA);

        assertThat(pendientes).hasValueSatisfying(p -> {
            assertThat(p.mentorId()).isEqualTo(MENTOR);
            assertThat(p.aprendices()).containsExactly(new Pendiente(ana.id().value(), ANA));
        });
        assertThat(luis.id()).isNotNull();
    }

    @Test
    @DisplayName("la recepción no recibe bienvenida de grupo")
    void recepcionNo() {
        grupo(TipoCelula.RECEPCION);

        assertThat(servicio.pendientes(GRUPO.value(), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("sin mentor vigente no hay quién la firme: vacío, y las pertenencias siguen pendientes")
    void sinMentorNo() {
        grupo(TipoCelula.REGULAR);
        asignar(ANA, FuncionAcompanamiento.APRENDIZ);

        assertThat(servicio.pendientes(GRUPO.value(), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("un grupo fuera de su periodo no manda bienvenidas aunque tenga mentor")
    void grupoNoOperativoNo() {
        grupo(TipoCelula.REGULAR);
        asignar(MENTOR, FuncionAcompanamiento.MENTOR);
        asignar(ANA, FuncionAcompanamiento.APRENDIZ);
        when(acompanamientoFinder.acompanaVigente(MENTOR, GRUPO.value(), AHORA)).thenReturn(false);

        assertThat(servicio.pendientes(GRUPO.value(), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("marcar delega en el UPDATE condicional y devuelve si esta llamada la dejó")
    void marcar() {
        UUID asignacion = UUID.randomUUID();
        when(marcaPort.marcar(asignacion, AHORA)).thenReturn(false);

        assertThat(servicio.marcarDada(asignacion, AHORA)).isFalse();
        verify(marcaPort).marcar(asignacion, AHORA);
    }

    private void grupo(TipoCelula tipo) {
        Celula celula = Celula.rehydrate(GRUPO, "Fenix", null, CohorteId.of(UUID.randomUUID()), null, null,
                AHORA, AHORA, tipo, null);
        when(loadCelulaPort.porId(GRUPO)).thenReturn(Optional.of(celula));
        lenient().when(loadAsignacionesPort.porCelula(GRUPO)).thenReturn(asignaciones);
    }

    private AsignacionCelula asignar(UserId usuario, FuncionAcompanamiento funcion) {
        AsignacionCelula a = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), GRUPO, usuario, funcion,
                AHORA.minusSeconds(3600), MotivoAsignacion.ADMINISTRATIVO, null, UUID.randomUUID().toString());
        asignaciones.add(a);
        return a;
    }
}
