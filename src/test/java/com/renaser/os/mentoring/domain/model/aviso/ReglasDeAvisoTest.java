package com.renaser.os.mentoring.domain.model.aviso;

import com.renaser.os.mentoring.domain.model.aviso.ReglasDeAviso.ObligacionEvaluada;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuándo avisar y —sobre todo— cuándo no. Un aviso de más le hace perseguir a alguien que está
 * a tiempo; uno de menos lo deja sin enterarse. Las dos cosas cuestan.
 */
class ReglasDeAvisoTest {

    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UUID GRUPO = UUID.randomUUID();
    private static final LocalDate HOY = LocalDate.of(2026, 9, 10);
    private static final int UMBRAL = 3;

    private final List<ObligacionEvaluada> obligaciones = new ArrayList<>();

    private void obligacion(LocalDate fecha, boolean cumplida, boolean requiereEvidencia, boolean entregada) {
        obligaciones.add(new ObligacionEvaluada(fecha, requiereEvidencia, requiereEvidencia, cumplida, entregada));
    }

    private List<AvisoDeAcompanamiento> evaluar() {
        return ReglasDeAviso.evaluar(MENTOR, ANA, GRUPO, obligaciones, HOY, UMBRAL);
    }

    private static boolean tiene(List<AvisoDeAcompanamiento> avisos, MotivoAviso motivo) {
        return avisos.stream().anyMatch(a -> a.motivo() == motivo);
    }

    @Test
    @DisplayName("sin ninguna obligacion registrada no se avisa: no saber no es incumplir")
    void sinDatosNoAvisa() {
        assertThat(evaluar()).isEmpty();
    }

    @Test
    @DisplayName("tres dias completos sin actividad disparan el aviso")
    void tresDiasSinActividad() {
        obligacion(LocalDate.of(2026, 9, 7), true, false, false);

        List<AvisoDeAcompanamiento> avisos = evaluar();

        assertThat(tiene(avisos, MotivoAviso.SIN_ACTIVIDAD)).isTrue();
        assertThat(avisos.stream().filter(a -> a.motivo() == MotivoAviso.SIN_ACTIVIDAD).findFirst()
                .orElseThrow().magnitud()).isEqualTo(3);
    }

    @Test
    @DisplayName("dos dias todavia no: el umbral es de dias COMPLETOS")
    void dosDiasNoAlcanzan() {
        obligacion(LocalDate.of(2026, 9, 8), true, false, false);

        assertThat(tiene(evaluar(), MotivoAviso.SIN_ACTIVIDAD)).isFalse();
    }

    @Test
    @DisplayName("el umbral es configurable: con 5, tres dias no disparan")
    void umbralConfigurable() {
        obligacion(LocalDate.of(2026, 9, 7), true, false, false);

        List<AvisoDeAcompanamiento> conCinco =
                ReglasDeAviso.evaluar(MENTOR, ANA, GRUPO, obligaciones, HOY, 5);

        assertThat(tiene(conCinco, MotivoAviso.SIN_ACTIVIDAD)).isFalse();
    }

    @Test
    @DisplayName("mientras siga ausente el aviso es el MISMO: no se repite dia tras dia")
    void mismoEpisodioMismaClave() {
        obligacion(LocalDate.of(2026, 9, 7), true, false, false);

        AvisoDeAcompanamiento hoy = ReglasDeAviso.evaluar(MENTOR, ANA, GRUPO, obligaciones, HOY, UMBRAL)
                .stream().filter(a -> a.motivo() == MotivoAviso.SIN_ACTIVIDAD).findFirst().orElseThrow();
        AvisoDeAcompanamiento manana = ReglasDeAviso.evaluar(MENTOR, ANA, GRUPO, obligaciones,
                HOY.plusDays(1), UMBRAL).stream()
                .filter(a -> a.motivo() == MotivoAviso.SIN_ACTIVIDAD).findFirst().orElseThrow();

        assertThat(manana.claveDeDeduplicacion()).isEqualTo(hoy.claveDeDeduplicacion());
        // La magnitud si cambia: son cuatro dias, no tres. Cambia el texto, no el aviso.
        assertThat(manana.magnitud()).isEqualTo(4);
    }

    @Test
    @DisplayName("si vuelve y se ausenta otra vez, es otro episodio y si avisa")
    void episodioNuevoClaveNueva() {
        obligacion(LocalDate.of(2026, 9, 1), true, false, false);
        AvisoDeAcompanamiento primero = evaluar().stream()
                .filter(a -> a.motivo() == MotivoAviso.SIN_ACTIVIDAD).findFirst().orElseThrow();

        obligacion(LocalDate.of(2026, 9, 7), true, false, false);
        AvisoDeAcompanamiento segundo = evaluar().stream()
                .filter(a -> a.motivo() == MotivoAviso.SIN_ACTIVIDAD).findFirst().orElseThrow();

        assertThat(segundo.claveDeDeduplicacion()).isNotEqualTo(primero.claveDeDeduplicacion());
    }

    @Test
    @DisplayName("al rotar, el mentor entrante recibe su propio aviso")
    void avisoPorMentor() {
        obligacion(LocalDate.of(2026, 9, 7), true, false, false);
        UserId otroMentor = UserId.of(UUID.randomUUID());

        AvisoDeAcompanamiento delPrimero = evaluar().getFirst();
        AvisoDeAcompanamiento delSegundo =
                ReglasDeAviso.evaluar(otroMentor, ANA, GRUPO, obligaciones, HOY, UMBRAL).getFirst();

        assertThat(delSegundo.claveDeDeduplicacion()).isNotEqualTo(delPrimero.claveDeDeduplicacion());
    }

    @Test
    @DisplayName("una evidencia vencida sin entregar avisa")
    void evidenciaVencida() {
        obligacion(LocalDate.of(2026, 9, 9), true, true, false);

        assertThat(tiene(evaluar(), MotivoAviso.EVIDENCIA_VENCIDA)).isTrue();
    }

    @Test
    @DisplayName("la de HOY no avisa: todavia le queda el dia")
    void laDeHoyNoVence() {
        obligacion(HOY, false, true, false);

        assertThat(tiene(evaluar(), MotivoAviso.EVIDENCIA_VENCIDA)).isFalse();
    }

    @Test
    @DisplayName("una obligacion futura tampoco")
    void futuraNoAvisa() {
        obligacion(HOY.plusDays(2), false, true, false);

        assertThat(evaluar()).isEmpty();
    }

    @Test
    @DisplayName("entregada no avisa, aunque la revision este pendiente")
    void entregadaNoAvisa() {
        obligacion(LocalDate.of(2026, 9, 9), true, true, true);

        assertThat(tiene(evaluar(), MotivoAviso.EVIDENCIA_VENCIDA)).isFalse();
    }

    @Test
    @DisplayName("un habito que no pide evidencia nunca genera aviso de evidencia")
    void sinExigenciaNoAvisa() {
        obligacion(LocalDate.of(2026, 9, 5), false, false, false);

        assertThat(tiene(evaluar(), MotivoAviso.EVIDENCIA_VENCIDA)).isFalse();
    }

    @Test
    @DisplayName("varias vencidas son UN aviso anclado en la mas antigua, con el conteo")
    void variasVencidasUnSoloAviso() {
        obligacion(LocalDate.of(2026, 9, 5), false, true, false);
        obligacion(LocalDate.of(2026, 9, 7), false, true, false);
        obligacion(LocalDate.of(2026, 9, 8), false, true, false);

        List<AvisoDeAcompanamiento> avisos = evaluar().stream()
                .filter(a -> a.motivo() == MotivoAviso.EVIDENCIA_VENCIDA).toList();

        assertThat(avisos).hasSize(1);
        assertThat(avisos.getFirst().magnitud()).isEqualTo(3);
        assertThat(avisos.getFirst().ancla()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("al resolverse la mas antigua, la siguiente genera un aviso distinto")
    void resolverLaViejaDestrabaLaSiguiente() {
        obligacion(LocalDate.of(2026, 9, 5), false, true, false);
        obligacion(LocalDate.of(2026, 9, 7), false, true, false);
        UUID antes = evaluar().stream().filter(a -> a.motivo() == MotivoAviso.EVIDENCIA_VENCIDA)
                .findFirst().orElseThrow().claveDeDeduplicacion();

        obligaciones.set(0, new ObligacionEvaluada(LocalDate.of(2026, 9, 5), true, true, true, true));
        UUID despues = evaluar().stream().filter(a -> a.motivo() == MotivoAviso.EVIDENCIA_VENCIDA)
                .findFirst().orElseThrow().claveDeDeduplicacion();

        assertThat(despues).isNotEqualTo(antes);
    }

    @Test
    @DisplayName("los dos motivos pueden convivir en la misma persona")
    void dosMotivosALaVez() {
        obligacion(LocalDate.of(2026, 9, 5), false, true, false);

        List<AvisoDeAcompanamiento> avisos = evaluar();

        assertThat(tiene(avisos, MotivoAviso.SIN_ACTIVIDAD)).isTrue();
        assertThat(tiene(avisos, MotivoAviso.EVIDENCIA_VENCIDA)).isTrue();
        assertThat(avisos).hasSize(2);
    }
}
