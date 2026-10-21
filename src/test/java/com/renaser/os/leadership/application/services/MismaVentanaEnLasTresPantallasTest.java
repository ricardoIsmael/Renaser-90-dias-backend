package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.ConsultaGrupoDelLider;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.GrupoDelLider;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ConsultaResumenPorGrupos;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.GrupoDelResumen;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ResumenPorGrupos;
import com.renaser.os.mentoring.application.services.BancoDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión del dueño del 2026-10-19: «la ventana del resumen manda en todo». El resumen «Semáforo por grupos», la lista
 * del grupo y la ficha de la persona son tres lecturas distintas de tres módulos, y esta prueba las pone sobre los
 * MISMOS datos del semáforo para comprobar que no pueden volver a decir cosas distintas (E2E de la spec 005: Rocío
 * salía con 50 % en el resumen y 47,6 % en la lista, porque el resumen miraba del lunes 12 al domingo 18 y la lista,
 * del sábado 10 al viernes 16).
 *
 * <p>El resumen y la tabla del grupo son los servicios REALES de {@code mentoring} sobre su banco de dobles; la lista y
 * la ficha son los servicios reales de {@code leadership}, con el semáforo y la tabla de ese mismo banco.
 */
class MismaVentanaEnLasTresPantallasTest {

    private static final UserId LIDER = id("c1");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");

    private final BancoDelSemaforo semaforo = new BancoDelSemaforo();
    private final BancoDeCumplimiento lider = new BancoDeCumplimiento();

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    /** Los mismos grupo, mentora y dos aprendices en los dos bancos, y un solo semaforo que los dos leen. */
    private void armar(Instant ahora) {
        semaforo.relojEn(ahora);
        lider.ahora = ahora;
        semaforo.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        lider.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        semaforo.grupo(FENIX, "Grupo Fénix");
        semaforo.mentor(FENIX, MENTORA);
        semaforo.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        lider.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        for (UserId aprendiz : List.of(ANA, BETO)) {
            String nombre = aprendiz.equals(ANA) ? "Ana Pérez" : "Beto Paz";
            semaforo.aprendiz(FENIX, aprendiz);
            semaforo.persona(aprendiz, nombre);
            lider.aprendiz(FENIX, aprendiz, nombre);
        }
        lider.compartirElSemaforoCon(semaforo.semaforo, semaforo.servicioDeTablas());
    }

    /**
     * Ana y Beto con porcentajes DISTINTOS en la ventana vigente (la que termina ayer) y en la última semana cerrada:
     * si la lista o la ficha leyeran la ventana equivocada, los números no coincidirían con los del resumen.
     */
    private void cargarElSemaforo(LocalDate hoy) {
        LocalDate desdeVigente = hoy.minusDays(7);
        semaforo.vigente(ANA, ventanaPareja(desdeVigente, false, 90));
        semaforo.vigente(BETO, ventanaPareja(desdeVigente, false, 40));
        LocalDate viernes = SemanaDelSemaforo.ultimaCerradaAl(hoy);
        semaforo.semana(viernes, ANA, ventanaPareja(viernes.minusDays(6), true, 70));
        semaforo.semana(viernes, BETO, ventanaPareja(viernes.minusDays(6), true, 55));
    }

    private void lasTresPantallasDicenLoMismo(LocalDate semanaHasta) {
        ResumenPorGrupos resumen = semaforo.servicioPorGrupos().resumenDe(new ConsultaResumenPorGrupos(LIDER, semanaHasta));
        GrupoDelLider lista = lider.grupoService().grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, semanaHasta));
        SemanaDeLaPersona deAna = lider.semanaService()
                .semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, ANA.value(), semanaHasta));
        SemanaDeLaPersona deBeto = lider.semanaService()
                .semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, BETO.value(), semanaHasta));

        // Los mismos siete dias, escritos igual y cerrados o no segun el calendario.
        assertThat(lista.desde()).isEqualTo(resumen.periodo().desde());
        assertThat(lista.hasta()).isEqualTo(resumen.periodo().hasta());
        assertThat(deAna.desde()).isEqualTo(resumen.periodo().desde());
        assertThat(deAna.hasta()).isEqualTo(resumen.periodo().hasta());
        assertThat(deBeto.desde()).isEqualTo(resumen.periodo().desde());
        assertThat(deBeto.hasta()).isEqualTo(resumen.periodo().hasta());
        assertThat(lista.cerrada()).isEqualTo(resumen.periodo().cerrada());
        assertThat(deAna.cerrada()).isEqualTo(resumen.periodo().cerrada());

        // El mismo % y el mismo color para la misma persona en la lista y en su ficha.
        PersonaDeLaLista filaDeAna = fila(lista, ANA);
        PersonaDeLaLista filaDeBeto = fila(lista, BETO);
        assertThat(deAna.porcentaje()).isEqualByComparingTo(filaDeAna.porcentaje());
        assertThat(deAna.color()).isEqualTo(filaDeAna.color());
        assertThat(deBeto.porcentaje()).isEqualByComparingTo(filaDeBeto.porcentaje());
        assertThat(deBeto.color()).isEqualTo(filaDeBeto.color());

        // Y el resumen cuenta a esa misma gente en los mismos colores y con el mismo promedio.
        GrupoDelResumen delResumen = resumen.grupos().getFirst();
        assertThat(delResumen.resumen().conteo()).isEqualTo(new ConteoPorColor(lista.resumen().verde(),
                lista.resumen().amarillo(), lista.resumen().rojo(), lista.resumen().sinDatos()));
        BigDecimal promedioDeLaLista = filaDeAna.porcentaje().add(filaDeBeto.porcentaje())
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        assertThat(delResumen.resumen().promedio()).isEqualByComparingTo(promedioDeLaLista);
    }

    private static PersonaDeLaLista fila(GrupoDelLider lista, UserId persona) {
        return lista.personas().stream().filter(p -> p.personaId().equals(persona.value())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("sin fecha, el resumen, la lista y la ficha leen la misma ventana vigente y dan el mismo % y color")
    void sinFecha() {
        Instant ahora = BancoDeCumplimiento.AHORA;
        armar(ahora);
        cargarElSemaforo(LocalDate.of(2026, 10, 19));

        lasTresPantallasDicenLoMismo(null);

        GrupoDelLider lista = lider.grupoService().grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, null));
        assertThat(lista.desde()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(lista.hasta()).isEqualTo(LocalDate.of(2026, 10, 18));
        // Los numeros son los de la ventana vigente (90 y 40), no los de la semana del 10 al 16 (70 y 55).
        assertThat(fila(lista, ANA).porcentaje()).isEqualByComparingTo("90");
        assertThat(fila(lista, BETO).color()).isEqualTo(ColorDeLaFila.ROJO);
    }

    @Test
    @DisplayName("con el mismo viernes, las tres leen esa semana de sabado a viernes y dan el mismo % y color")
    void conElMismoViernes() {
        armar(BancoDeCumplimiento.AHORA);
        cargarElSemaforo(LocalDate.of(2026, 10, 19));

        lasTresPantallasDicenLoMismo(LocalDate.of(2026, 10, 16));

        GrupoDelLider lista = lider.grupoService()
                .grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, LocalDate.of(2026, 10, 16)));
        assertThat(lista.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(lista.cerrada()).isTrue();
        assertThat(fila(lista, ANA).porcentaje()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("E-629 (e): con una persona sin foto del cierre, las tres rotulan igual la semana que ya pasó: cerrada")
    void conUnaPersonaSinFotoDelCierre() {
        armar(BancoDeCumplimiento.AHORA);
        cargarElSemaforo(LocalDate.of(2026, 10, 19));
        // Beto empezó su programa después de ese viernes: el barrido no le escribió la foto de la semana (cerrada = false).
        LocalDate viernes = LocalDate.of(2026, 10, 16);
        semaforo.semana(viernes, BETO, ventanaPareja(viernes.minusDays(6), false, 55));

        lasTresPantallasDicenLoMismo(viernes);

        ResumenPorGrupos resumen = semaforo.servicioPorGrupos().resumenDe(new ConsultaResumenPorGrupos(LIDER, viernes));
        assertThat(resumen.periodo().cerrada()).isTrue();
    }

    @Test
    @DisplayName("regla 02: con el reloj en la madrugada UTC las tres leen la ventana vigente de Lima, no la de UTC")
    void madrugadaUtc() {
        // 03:00 UTC del lunes 19 = 22:00 del domingo 18 en Lima: para ella la ventana vigente termina el sabado 17.
        Instant ahora = Instant.parse("2026-10-19T03:00:00Z");
        armar(ahora);
        cargarElSemaforo(LocalDate.of(2026, 10, 18));

        lasTresPantallasDicenLoMismo(null);

        GrupoDelLider lista = lider.grupoService().grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, null));
        assertThat(lista.desde()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(lista.hasta()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("un sabado la vigente es la semana que acaba de cerrar: las tres la dan por cerrada")
    void sabado() {
        // Sabado 24, 10:00 en Lima: la ventana vigente es el 17 al viernes 23, que cerro hace unas horas.
        armar(Instant.parse("2026-10-24T15:00:00Z"));
        cargarElSemaforo(LocalDate.of(2026, 10, 24));

        SemanaDeLaPersona deAna = lider.semanaService()
                .semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, ANA.value(), null));
        GrupoDelLider lista = lider.grupoService().grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, null));

        assertThat(deAna.hasta()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(deAna.cerrada()).isTrue();
        assertThat(lista.cerrada()).isTrue();
        assertThat(deAna.porcentaje()).isEqualByComparingTo(fila(lista, ANA).porcentaje());
    }
}
