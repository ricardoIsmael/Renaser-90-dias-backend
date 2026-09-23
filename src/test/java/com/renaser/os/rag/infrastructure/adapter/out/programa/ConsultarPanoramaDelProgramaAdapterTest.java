package com.renaser.os.rag.infrastructure.adapter.out.programa;

import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.Panorama;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.ProximoEvento;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El panorama se arma con los mismos contratos que {@code GET /home}. Lo que se prueba: que la
 * coherencia se pide con la fecha LOCAL del participante (a las 03:30 UTC, en Lima todavia es el
 * dia anterior), que "sin dato" no se convierte en un numero, y que un evento que no aplica no
 * tumba el resto.
 */
class ConsultarPanoramaDelProgramaAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 2026-09-23 03:30 UTC = 2026-09-22 22:30 en Lima. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-09-23T03:30:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 22);

    /** Coherente: fecha de inicio 2026-09-11 y hoy 2026-09-22 en Lima son 12 dias de programa. */
    private static final ParticipacionPrograma EN_DIA_12 = new ParticipacionPrograma(APRENDIZ, true, 12,
            LocalDate.of(2026, 9, 11), LIMA, FasePrograma.PHASE_2_DEVELOPMENT, null, null, UserRole.TRAINEE,
            false, true);

    private final List<LocalDate> fechasPedidas = new ArrayList<>();

    private PorcentajeRocasFinder coherenciaDe(BigDecimal porcentaje) {
        return (participantes, hasta) -> {
            fechasPedidas.add(hasta);
            return porcentaje == null ? Map.of() : Map.of(APRENDIZ, porcentaje);
        };
    }

    private static ProximoEventoFinder conEvento(String titulo, Instant iniciaEn) {
        return participanteId -> Optional.of(new ProximoEventoFinder.ProximoEvento(UUID.randomUUID(), titulo, iniciaEn));
    }

    @Test
    @DisplayName("la coherencia se pide hasta HOY EN LIMA, no hasta la fecha UTC del servidor")
    void coherenciaConLaFechaLocal() {
        var adaptador = new ConsultarPanoramaDelProgramaAdapter(new FinderFijo(EN_DIA_12),
                coherenciaDe(new BigDecimal("85.5")), participanteId -> Optional.empty());

        Optional<Panorama> panorama = adaptador.de(APRENDIZ, MADRUGADA_UTC);

        assertThat(fechasPedidas).containsExactly(HOY_EN_LIMA);
        assertThat(panorama).hasValueSatisfying(p -> {
            assertThat(p.zona()).isEqualTo(LIMA);
            assertThat(p.coherencia()).contains(new BigDecimal("85.5"));
        });
    }

    @Test
    @DisplayName("sin acciones planificadas no hay coherencia: vacio, ni cero ni cien (D-128)")
    void sinCoherencia() {
        var adaptador = new ConsultarPanoramaDelProgramaAdapter(new FinderFijo(EN_DIA_12), coherenciaDe(null),
                participanteId -> Optional.empty());

        assertThat(adaptador.de(APRENDIZ, MADRUGADA_UTC))
                .hasValueSatisfying(p -> assertThat(p.coherencia()).isEmpty());
    }

    @Test
    @DisplayName("el proximo evento viaja con su titulo y su instante")
    void proximoEvento() {
        Instant inicio = Instant.parse("2026-09-23T14:00:00Z");
        var adaptador = new ConsultarPanoramaDelProgramaAdapter(new FinderFijo(EN_DIA_12), coherenciaDe(null),
                conEvento("Sesion en vivo", inicio));

        assertThat(adaptador.de(APRENDIZ, MADRUGADA_UTC)).hasValueSatisfying(p ->
                assertThat(p.proximoEvento()).contains(new ProximoEvento("Sesion en vivo", inicio)));
    }

    @Test
    @DisplayName("si el evento no aplica para la persona, el panorama sale igual, sin evento")
    void eventoQueNoAplica() {
        ProximoEventoFinder suspendido = participanteId -> {
            throw new NotAuthorizedException("cuenta suspendida");
        };
        ProximoEventoFinder sinProgreso = participanteId -> {
            throw new NoSuchElementException("sin progreso");
        };

        for (ProximoEventoFinder finder : List.of(suspendido, sinProgreso)) {
            var adaptador = new ConsultarPanoramaDelProgramaAdapter(new FinderFijo(EN_DIA_12),
                    coherenciaDe(new BigDecimal("70.0")), finder);

            assertThat(adaptador.de(APRENDIZ, MADRUGADA_UTC)).hasValueSatisfying(p -> {
                assertThat(p.proximoEvento()).isEmpty();
                assertThat(p.coherencia()).contains(new BigDecimal("70.0"));
            });
        }
    }

    @Test
    @DisplayName("una persona que no existe no tiene panorama")
    void sinParticipacion() {
        var adaptador = new ConsultarPanoramaDelProgramaAdapter(new FinderFijo(null), coherenciaDe(null),
                participanteId -> Optional.empty());

        assertThat(adaptador.de(APRENDIZ, MADRUGADA_UTC)).isEmpty();
        assertThat(fechasPedidas).isEmpty();
    }

    /** Doble minimo: solo se le pregunta la participacion de una persona. */
    private record FinderFijo(ParticipacionPrograma participacion) implements ParticipacionProgramaFinder {

        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            return Optional.ofNullable(participacion);
        }

        @Override
        public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> miembrosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return List.of();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    }
}
