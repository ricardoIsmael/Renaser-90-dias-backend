package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.Bloqueo;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursoAccesible;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursoBloqueado;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursosDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@code consultar_mis_cursos} y {@code consultar_por_que_esta_bloqueado} (2026-09-23). */
class CursosHerramientasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final ConsultarCursosPort puerto = mock(ConsultarCursosPort.class);
    private final ConsultarMisCursosHerramienta misCursos = new ConsultarMisCursosHerramienta(puerto);
    private final ConsultarPorQueEstaBloqueadoHerramienta porQue = new ConsultarPorQueEstaBloqueadoHerramienta(puerto);

    private static InvocacionHerramienta bloqueo(Map<String, String> argumentos) {
        return new InvocacionHerramienta(ConsultarPorQueEstaBloqueadoHerramienta.NOMBRE, argumentos);
    }

    @Test
    @DisplayName("mis cursos: accesibles con avance y bloqueados con su dia, cada uno con curso_id")
    void listaLosCursos() {
        when(puerto.cursosDe(APRENDIZ)).thenReturn(new CursosDelAprendiz(
                List.of(new CursoAccesible("c-1", "Fundamentos", 20, 7)),
                List.of(new CursoBloqueado("c-2", "Liderazgo", 30, 12))));

        String texto = ((ResultadoHerramienta.Exito) misCursos.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarMisCursosHerramienta.NOMBRE))).contenido();

        assertThat(texto).contains("curso_id=c-1 | Fundamentos | 7 de 20 lecciones completadas")
                .contains("curso_id=c-2 | Liderazgo | se desbloquea en su dia 30 del programa (hoy va en el dia 12)");
    }

    @Test
    @DisplayName("mis cursos: sin programa vuelve Fallo legible")
    void sinPrograma() {
        when(puerto.cursosDe(APRENDIZ)).thenThrow(new NoSuchElementException("Usuario no encontrado"));

        assertThat(((ResultadoHerramienta.Fallo) misCursos.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarMisCursosHerramienta.NOMBRE))).motivo())
                .doesNotContain("Usuario no encontrado");
    }

    @Test
    @DisplayName("por que: curso bloqueado por dia dice el dia de desbloqueo y el actual")
    void cursoBloqueado() {
        when(puerto.bloqueoDeCurso(APRENDIZ, "c-2")).thenReturn(new Bloqueo(true, "Liderazgo", 30, 12));

        String texto = ((ResultadoHerramienta.Exito) porQue.ejecutar(APRENDIZ,
                bloqueo(Map.of(ConsultarPorQueEstaBloqueadoHerramienta.ARGUMENTO_CURSO_ID, " c-2 ")))).contenido();

        assertThat(texto).contains("'Liderazgo'").contains("dia 30").contains("dia 12");
    }

    @Test
    @DisplayName("por que: leccion no bloqueada por dia no inventa otro motivo")
    void leccionNoBloqueada() {
        when(puerto.bloqueoDeLeccion(APRENDIZ, "l-9")).thenReturn(new Bloqueo(false, null, null, null));

        String texto = ((ResultadoHerramienta.Exito) porQue.ejecutar(APRENDIZ,
                bloqueo(Map.of(ConsultarPorQueEstaBloqueadoHerramienta.ARGUMENTO_LECCION_ID, "l-9")))).contenido();

        assertThat(texto).contains("No esta bloqueado por dia").contains("no inventes otro motivo");
    }

    @Test
    @DisplayName("por que: exige exactamente uno de curso_id o leccion_id")
    void exigeUnSoloId() {
        assertThat(porQue.ejecutar(APRENDIZ, bloqueo(Map.of()))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(porQue.ejecutar(APRENDIZ, bloqueo(Map.of(
                ConsultarPorQueEstaBloqueadoHerramienta.ARGUMENTO_CURSO_ID, "c-1",
                ConsultarPorQueEstaBloqueadoHerramienta.ARGUMENTO_LECCION_ID, "l-1"))))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(puerto);
    }
}
