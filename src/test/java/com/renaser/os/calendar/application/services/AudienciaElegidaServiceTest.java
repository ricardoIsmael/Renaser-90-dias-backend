package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.ResumenDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.OrigenDelInvitado;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 044: armar la audiencia elegida al guardar, quién está dentro y el resumen para quien la creó. */
class AudienciaElegidaServiceTest {

    private final MundoDeAudiencia m = new MundoDeAudiencia();
    private static final Formacion ESCUELA = Formacion.ESCUELA;

    private AudienciaElegida armarComoLider(List<ParteDeAudiencia> partes) {
        return m.servicio.armar(m.lider, RolUsuario.MENTOR_LEAD, ESCUELA, partes, null, AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA);
    }

    private Evento evento(UserId creador) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Sesión", null, MundoDeAudiencia.AHORA.plusSeconds(86_400), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.SELECCION, null, null, null,
                TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), creador,
                FixedClock.at(MundoDeAudiencia.AHORA), ESCUELA, null);
    }

    // spec 044 FR-009, FR-010, SC-001
    @Test
    @DisplayName("FR-010: el color es una foto al guardar: entran los rojos de hoy y nadie más")
    void elColorEsUnaFotoAlGuardar() {
        AudienciaElegida a = armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));

        assertThat(a.personasFijas()).containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.deOtroGrupo);
        assertThat(a.fotoDe(ColorDeAudiencia.ROJO)).get().extracting(r -> r.resueltoEn()).isEqualTo(MundoDeAudiencia.AHORA);
        // Quien después pasa a rojo no entra solo: la lista no cambia.
        m.semaforo.put(m.verde, ColorDeAudiencia.ROJO);
        assertThat(m.servicio.miembros(evento(m.lider), a)).doesNotContain(m.verde);
    }

    // spec 044 FR-009
    @Test
    @DisplayName("FR-009: sin datos o sin semáforo no entra por ningún color")
    void sinDatosNoEntra() {
        AudienciaElegida a = armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO),
                ParteDeAudiencia.color(ColorDeAudiencia.AMARILLO), ParteDeAudiencia.color(ColorDeAudiencia.VERDE)));

        assertThat(a.personasFijas()).doesNotContain(m.sinGrupo); // no tiene semáforo
    }

    // spec 044 FR-011
    @Test
    @DisplayName("FR-011: al editar, la foto guardada se conserva; con «Volver a calcular» se toma otra")
    void volverACalcular() {
        AudienciaElegida previa = armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        m.semaforo.put(m.rojo1, ColorDeAudiencia.VERDE);
        m.semaforo.put(m.amarillo, ColorDeAudiencia.ROJO);
        Instant despues = MundoDeAudiencia.AHORA.plusSeconds(3600);

        AudienciaElegida conservada = m.servicio.armar(m.lider, RolUsuario.MENTOR_LEAD, ESCUELA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)),
                null, previa, false, despues);
        AudienciaElegida nueva = m.servicio.armar(m.lider, RolUsuario.MENTOR_LEAD, ESCUELA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)),
                null, previa, true, despues);

        assertThat(conservada.personasFijas()).containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.deOtroGrupo);
        assertThat(conservada.fotoDe(ColorDeAudiencia.ROJO).orElseThrow().resueltoEn()).isEqualTo(MundoDeAudiencia.AHORA);
        assertThat(nueva.personasFijas()).containsExactlyInAnyOrder(m.amarillo, m.rojo2, m.deOtroGrupo);
        assertThat(nueva.fotoDe(ColorDeAudiencia.ROJO).orElseThrow().resueltoEn()).isEqualTo(despues);
    }

    // spec 044 FR-012, SC-007
    @Test
    @DisplayName("FR-012: elegir tres colores lee el semáforo una sola vez, no una por aprendiz")
    void unaLecturaDelSemaforo() {
        armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO), ParteDeAudiencia.color(ColorDeAudiencia.AMARILLO),
                ParteDeAudiencia.color(ColorDeAudiencia.VERDE)));

        assertThat(m.lecturasDelSemaforo).isEqualTo(1);
    }

    // spec 044 FR-003
    @Test
    @DisplayName("FR-003: las partes se suman y cada persona cuenta una vez")
    void sumanYUnaVez() {
        AudienciaElegida a = armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO),
                ParteDeAudiencia.persona(m.rojo1.value()), ParteDeAudiencia.persona(m.otroMentor.value())));

        assertThat(m.servicio.miembros(evento(m.lider), a)).containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.deOtroGrupo, m.otroMentor);
        assertThat(m.servicio.resumen(evento(m.lider), a).total()).isEqualTo(4);
    }

    // spec 044 FR-004, SC-003
    @Test
    @DisplayName("FR-004: un mentor que invita a quien no es su alumno recibe 403 con ese id")
    void mentorFueraDeSuAlcance() {
        assertThatThrownBy(() -> m.servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA,
                List.of(ParteDeAudiencia.persona(m.rojo1.value()), ParteDeAudiencia.persona(m.deOtroGrupo.value())), m.mentor,
                AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA))
                .isInstanceOfSatisfying(AudienciaRechazadaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(AudienciaRechazadaException.FUERA_DE_TU_ALCANCE);
                    assertThat(e.ids()).containsExactly(m.deOtroGrupo.value());
                });
    }

    // spec 044 FR-002
    @Test
    @DisplayName("FR-002: un mentor no elige «Personal» ni «Mentores»; un líder no elige «Mis alumnos»")
    void partesQueNoLeTocan() {
        assertThatThrownBy(() -> m.servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.sola(TipoDeParte.MENTORES)),
                m.mentor, AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA))
                .isInstanceOf(AudienciaRechazadaException.class);
        assertThatThrownBy(() -> m.servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA,
                List.of(new ParteDeAudiencia(TipoDeParte.PERSONAL, m.alquimista.value(), null)), m.mentor, AudienciaElegida.vacia(), true,
                MundoDeAudiencia.AHORA))
                .isInstanceOf(AudienciaRechazadaException.class);
        assertThatThrownBy(() -> armarComoLider(List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS))))
                .isInstanceOf(AudienciaRechazadaException.class);
    }

    // spec 044 FR-002
    @Test
    @DisplayName("FR-002: el color del mentor es el de sus alumnos, no el de toda la formación")
    void elColorDelMentorEsDeSusAlumnos() {
        AudienciaElegida a = m.servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)),
                m.mentor, AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA);

        assertThat(a.personasFijas()).containsExactlyInAnyOrder(m.rojo1, m.rojo2);
    }

    // spec 044 FR-005
    @Test
    @DisplayName("FR-005: sin partes es AUDIENCIA_INVALIDA; sin nadie hoy es AUDIENCIA_VACIA")
    void vaciaEInvalida() {
        assertThatThrownBy(() -> armarComoLider(List.of()))
                .isInstanceOfSatisfying(AudienciaRechazadaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(AudienciaRechazadaException.AUDIENCIA_INVALIDA));
        m.semaforo.clear();
        assertThatThrownBy(() -> armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO))))
                .isInstanceOfSatisfying(AudienciaRechazadaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(AudienciaRechazadaException.AUDIENCIA_VACIA));
    }

    // spec 044 FR-014
    @Test
    @DisplayName("FR-014: «Mis alumnos» y el grupo se leen en cada momento; quien entra al grupo está, quien sale ya no")
    void lasDinamicasSeLeenEnCadaMomento() {
        AudienciaElegida a = m.servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS)),
                m.mentor, AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA);
        Evento evento = evento(m.mentor);

        assertThat(m.servicio.incluye(evento, a, m.verde, RolUsuario.TRAINEE)).isTrue();
        m.integrantes.get(m.grupo).remove(m.verde);
        m.integrantes.get(m.grupo).add(m.sinGrupo);
        assertThat(m.servicio.incluye(evento, a, m.verde, RolUsuario.TRAINEE)).isFalse();
        assertThat(m.servicio.incluye(evento, a, m.sinGrupo, RolUsuario.TRAINEE)).isTrue();
        assertThat(m.servicio.miembros(evento, a)).contains(m.sinGrupo).doesNotContain(m.verde);
    }

    // spec 044 FR-020, S5
    @Test
    @DisplayName("FR-020: quien creó el evento está dentro aunque no se haya elegido; el resto de la formación no")
    void elCreadorEstaDentro() {
        AudienciaElegida a = armarComoLider(List.of(ParteDeAudiencia.persona(m.rojo1.value())));
        Evento evento = evento(m.lider);

        assertThat(m.servicio.incluye(evento, a, m.lider, RolUsuario.MENTOR_LEAD)).isTrue();
        assertThat(m.servicio.incluye(evento, a, m.rojo1, RolUsuario.TRAINEE)).isTrue();
        assertThat(m.servicio.incluye(evento, a, m.verde, RolUsuario.TRAINEE)).isFalse();
    }

    // spec 044 FR-002 (Personal)
    @Test
    @DisplayName("Historia 3: el líder elige al Alquimista por su nombre: solo ella está, no Administración")
    void elLiderConElAlquimista() {
        AudienciaElegida a = armarComoLider(List.of(new ParteDeAudiencia(TipoDeParte.PERSONAL, m.alquimista.value(), null)));

        assertThat(m.servicio.miembros(evento(m.lider), a)).containsExactly(m.alquimista);
        assertThat(a.invitadosDe(OrigenDelInvitado.PERSONAL)).hasSize(1);
    }

    // spec 044 FR-018
    @Test
    @DisplayName("FR-018: el resumen dice cada parte con su cifra y las personas por nombre")
    void resumenConCifras() {
        AudienciaElegida a = armarComoLider(List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO), ParteDeAudiencia.persona(m.verde.value())));

        ResumenDeAudiencia r = m.servicio.resumen(evento(m.lider), a);

        assertThat(r.total()).isEqualTo(4);
        assertThat(r.partes()).anySatisfy(p -> {
            assertThat(p.tipo()).isEqualTo(TipoDeParte.COLOR);
            assertThat(p.color()).isEqualTo(ColorDeAudiencia.ROJO);
            assertThat(p.cuantos()).isEqualTo(3);
            assertThat(p.resueltoEn()).isEqualTo(MundoDeAudiencia.AHORA);
        });
        assertThat(r.partes()).anySatisfy(p -> {
            assertThat(p.tipo()).isEqualTo(TipoDeParte.PERSONA);
            assertThat(p.personas()).extracting(x -> x.id()).containsExactly(m.verde.value());
        });
    }

    // spec 044 FR-033
    @Test
    @DisplayName("FR-033: la vista previa cuenta sin guardar nada y sin contar a quien la pide")
    void vistaPrevia() {
        ResumenDeAudiencia r = m.servicio.vistaPrevia(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS)));

        assertThat(r.total()).isEqualTo(4);
    }

    // spec 044 FR-033
    @Test
    @DisplayName("FR-033: el mentor que acompaña grupos de dos formaciones ve «Mis alumnos» y «Llega a» con los de la formación del evento")
    void misAlumnosDeLaFormacionDelEvento() {
        // El amarillo es de otra formación (el mentor acompaña también un grupo del Diplomado).
        AudienciaElegidaService servicio = new AudienciaElegidaService(m.padron, m.colores, m.celulas, m.pertenencia,
                new AlcanceDeFormacionPermisivo() {
                    @Override
                    public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
                        Set<UserId> r = new HashSet<>(personas);
                        r.remove(m.amarillo);
                        return r;
                    }
                }, FixedClock.at(MundoDeAudiencia.AHORA));

        assertThat(servicio.alumnosEnLaFormacion(m.mentor, ESCUELA)).containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.verde);
        assertThat(servicio.vistaPrevia(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.sola(TipoDeParte.MIS_ALUMNOS))).total())
                .isEqualTo(3);
        assertThatThrownBy(() -> servicio.armar(m.mentor, RolUsuario.MENTOR, ESCUELA, List.of(ParteDeAudiencia.persona(m.amarillo.value())), m.mentor,
                AudienciaElegida.vacia(), true, MundoDeAudiencia.AHORA))
                .isInstanceOfSatisfying(AudienciaRechazadaException.class,
                        e -> assertThat(e.codigo()).isEqualTo(AudienciaRechazadaException.FUERA_DE_TU_ALCANCE));
    }
}
