package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.Modo;
import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.ResultadoDeImportacion;
import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.VistaPreviaDeImportacion;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.ImportacionConErroresException;
import com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Importar preguntas pegadas como texto (spec 026, US6): todo o nada, añadir o reemplazar, y lo respondido se retira en vez de borrarse. */
class ImportarDiagnosticoServiceTest {

    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final String CABECERA = String.join(",", PlantillaDeImportacion.COLUMNAS.stream().map(c -> c.contains(",") ? "\"" + c + "\"" : c).toList());

    private BorradorEnMemoria borrador;
    private ImportarDiagnosticoService service;

    @BeforeEach
    void preparar() {
        ConsultarActorPort actores = mock(ConsultarActorPort.class);
        when(actores.deActor(ADMIN)).thenReturn(Optional.of(new ActorOnboarding(ADMIN, false, "Admin", true)));
        when(actores.deActor(MENTOR)).thenReturn(Optional.of(new ActorOnboarding(MENTOR, false, "Mentor", false)));
        borrador = new BorradorEnMemoria()
                .con(new SeccionDelDiagnostico(1, SeccionDelDiagnostico.CLAVE_DE_LA_PORTADA, "Diagnóstico", null, 0, List.of()));
        service = new ImportarDiagnosticoService(new GuardiaDelDiagnostico(actores), borrador,
                new QuitarPreguntasDelDiagnostico(borrador, FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"))));
    }

    private static String hoja(String... filas) {
        return CABECERA + "\n" + String.join("\n", filas);
    }

    @Test
    @DisplayName("el formulario del dueño se importa entero: 6 secciones, 27 preguntas, con su condición y sus destinos")
    void elFormularioDelDueno() throws IOException {
        String csv = Files.readString(Path.of("specs/026-ficha-con-diagnostico/diagnostico-inicial.csv"), StandardCharsets.UTF_8);

        ResultadoDeImportacion resultado = service.importar(ADMIN, csv, Modo.ANADIR);

        assertThat(resultado.secciones()).isEqualTo(6);
        assertThat(resultado.preguntas()).isEqualTo(27);
        List<PreguntaDelDiagnostico> preguntas = borrador.cargar().preguntas();
        assertThat(preguntas).hasSize(27);
        PreguntaDelDiagnostico padre = preguntas.stream().filter(p -> p.clave().equals("diag_p13")).findFirst().orElseThrow();
        PreguntaDelDiagnostico hija = preguntas.stream().filter(p -> p.clave().equals("diag_p13a")).findFirst().orElseThrow();
        assertThat(hija.condicion().preguntaId()).isEqualTo(padre.id());
        assertThat(preguntas).filteredOn(p -> p.alimenta() == DestinoDeRespuesta.PRIORIDAD_PRINCIPAL).hasSize(1);
        assertThat(preguntas).filteredOn(p -> p.tipo() == TipoPreguntaOnboarding.CUADRICULA).isNotEmpty();
    }

    @Test
    @DisplayName("todo o nada: un solo error y no se guarda ninguna fila")
    void todoONada() {
        String texto = hoja("Sobre ti,a1,¿Cómo te llamas?,,Texto corto,Sí,,,,,", "Sobre ti,a2,¿Cuántos años tienes?,,Tipo que no existe,Sí,,,,,");

        assertThatThrownBy(() -> service.importar(ADMIN, texto, Modo.ANADIR)).isInstanceOf(ImportacionConErroresException.class)
                .satisfies(e -> assertThat(((ImportacionConErroresException) e).errores()).hasSize(1));
        assertThat(borrador.cargar().preguntas()).isEmpty();
        assertThat(borrador.cargar().secciones()).hasSize(1);
    }

    @Test
    @DisplayName("añadir no deja repetir una clave que ya existe")
    void clavesRepetidas() {
        String texto = hoja("Sobre ti,a1,¿Cómo te llamas?,,Texto corto,Sí,,,,,");
        service.importar(ADMIN, texto, Modo.ANADIR);

        assertThatThrownBy(() -> service.importar(ADMIN, texto, Modo.ANADIR)).isInstanceOf(ImportacionConErroresException.class)
                .satisfies(e -> assertThat(((ImportacionConErroresException) e).errores()).anyMatch(x -> x.mensaje().contains("ya existe")));
        assertThat(borrador.cargar().preguntas()).hasSize(1);
    }

    @Test
    @DisplayName("reemplazar quita lo que había: lo respondido se retira (conserva sus respuestas) y lo demás se borra")
    void reemplazar() {
        service.importar(ADMIN, hoja("Sobre ti,a1,¿Cómo te llamas?,,Texto corto,Sí,,,,,", "Sobre ti,a2,¿De dónde eres?,,Texto corto,No,,,,,"), Modo.ANADIR);
        int respondida = borrador.cargar().preguntas().get(0).id();
        borrador.respuestas.put(respondida, 7);

        VistaPreviaDeImportacion vista = service.revisar(ADMIN, hoja("Tu día,b1,¿A qué hora despiertas?,,Texto corto,Sí,,,,,"), Modo.REEMPLAZAR);
        assertThat(vista.preguntasQueSeRetirarian()).isEqualTo(2);
        assertThat(vista.conRespuestas()).isEqualTo(1);
        assertThat(borrador.cargar().preguntas()).as("revisar no guarda nada").hasSize(2);

        ResultadoDeImportacion resultado = service.importar(ADMIN, hoja("Tu día,b1,¿A qué hora despiertas?,,Texto corto,Sí,,,,,"), Modo.REEMPLAZAR);

        assertThat(resultado.retiradas()).isEqualTo(1);
        assertThat(resultado.eliminadas()).isEqualTo(1);
        assertThat(borrador.cargar().preguntas()).extracting(PreguntaDelDiagnostico::texto).containsExactly("¿A qué hora despiertas?");
        assertThat(borrador.retiradas()).extracting(PreguntaDelDiagnostico::id).containsExactly(respondida);
        assertThat(borrador.clavesLiberadas).containsExactly(respondida);
    }

    @Test
    @DisplayName("solo Administración y Alquimista importan")
    void soloQuienAdministra() {
        String texto = hoja("Sobre ti,a1,¿Cómo te llamas?,,Texto corto,Sí,,,,,");
        assertThatThrownBy(() -> service.importar(MENTOR, texto, Modo.ANADIR)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.revisar(MENTOR, texto, Modo.ANADIR)).isInstanceOf(NotAuthorizedException.class);
        assertThat(borrador.cargar().preguntas()).isEmpty();
    }
}
