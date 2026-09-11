package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.AvisoDeAcompanamientoEvent;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El barrido de avisos. Lo que se verifica acá y no en las reglas puras es el cableado: que la
 * consulta sea en lote, que el mentor que además cursa no se autoavise, y que un grupo que falla
 * no frene a los demás.
 */
class AvisosServiceTest {

    /** Jueves 10 de setiembre de 2026, 15:00 UTC = 10:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");

    private static final UUID GRUPO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COHORTE = UUID.randomUUID();
    private static final UserId MENTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

    private BancoDeMentoria banco;
    private final List<AvisoDeAcompanamientoEvent> publicados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        banco = new BancoDeMentoria();
        publicados.clear();
    }

    private AvisosService servicio() {
        ApplicationEventPublisher publisher = evento -> {
            if (evento instanceof AvisoDeAcompanamientoEvent aviso) {
                publicados.add(aviso);
            }
        };
        return new AvisosService(banco.acompanamiento, banco.obligacionesFinder, banco.entregasFinder,
                banco.usuarios, publisher, FixedClock.at(AHORA));
    }

    private void grupoConAna(int umbral) {
        banco.grupo(GRUPO, "Grupo Amanecer", MENTOR, COHORTE, umbral);
        banco.alumno(GRUPO, ANA, "Ana Perez", Instant.parse("2026-08-01T05:00:00Z"), null);
    }

    @Test
    @DisplayName("tres dias sin actividad publican un aviso con el nombre del alumno")
    void avisaPorAusencia() {
        grupoConAna(3);
        banco.obligacion(ANA, LocalDate.of(2026, 9, 7), true, false);

        assertThat(servicio().detectar()).isEqualTo(1);
        assertThat(publicados).hasSize(1);
        assertThat(publicados.getFirst().motivo()).isEqualTo("SIN_ACTIVIDAD");
        assertThat(publicados.getFirst().nombreDelAlumno()).isEqualTo("Ana Perez");
        assertThat(publicados.getFirst().mentorId()).isEqualTo(MENTOR.value());
    }

    @Test
    @DisplayName("la ruta del aviso apunta al detalle del alumno dentro de su grupo")
    void rutaProfunda() {
        grupoConAna(3);
        banco.obligacion(ANA, LocalDate.of(2026, 9, 7), true, false);

        servicio().detectar();

        assertThat(publicados.getFirst().rutaApp())
                .isEqualTo("/mentor/groups/" + GRUPO + "/learners/" + ANA.value());
    }

    @Test
    @DisplayName("repetir el barrido devuelve la MISMA clave: la deduplicacion la resuelve el indice unico")
    void barridoRepetidoMismaClave() {
        grupoConAna(3);
        banco.obligacion(ANA, LocalDate.of(2026, 9, 7), true, false);

        servicio().detectar();
        servicio().detectar();

        assertThat(publicados).hasSize(2);
        assertThat(publicados.get(1).claveDeduplicacion()).isEqualTo(publicados.getFirst().claveDeduplicacion());
    }

    @Test
    @DisplayName("un alumno al dia no genera nada")
    void alumnoAlDiaNoAvisa() {
        grupoConAna(3);
        var hoy = banco.obligacion(ANA, LocalDate.of(2026, 9, 10), true, true);
        banco.entregada(hoy, LocalDate.of(2026, 9, 10));

        assertThat(servicio().detectar()).isZero();
        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin alumnos no consulta nada ni avisa")
    void grupoVacio() {
        banco.grupo(GRUPO, "Grupo Vacio", MENTOR, COHORTE, 3);

        assertThat(servicio().detectar()).isZero();
    }

    @Test
    @DisplayName("el mentor que ademas cursa no se autoavisa")
    void mentorQueCursaNoSeAutoavisa() {
        banco.grupo(GRUPO, "Grupo Amanecer", MENTOR, COHORTE, 3);
        // El propio mentor figura como aprendiz del grupo: no debe generarse aviso para si mismo.
        banco.alumno(GRUPO, MENTOR, "El Mentor", Instant.parse("2026-08-01T05:00:00Z"), null);
        banco.obligacion(MENTOR, LocalDate.of(2026, 9, 1), true, false);

        assertThat(servicio().detectar()).isZero();
    }

    @Test
    @DisplayName("evidencia vencida y ausencia conviven: dos avisos, uno por motivo")
    void dosMotivos() {
        grupoConAna(3);
        banco.obligacion(ANA, LocalDate.of(2026, 9, 5), false, true);

        assertThat(servicio().detectar()).isEqualTo(2);
        assertThat(publicados).extracting(AvisoDeAcompanamientoEvent::motivo)
                .containsExactlyInAnyOrder("SIN_ACTIVIDAD", "EVIDENCIA_VENCIDA");
    }

    @Test
    @DisplayName("sin obligaciones registradas no se avisa: no saber no es incumplir")
    void sinDatosNoAvisa() {
        grupoConAna(3);

        assertThat(servicio().detectar()).isZero();
    }
}
