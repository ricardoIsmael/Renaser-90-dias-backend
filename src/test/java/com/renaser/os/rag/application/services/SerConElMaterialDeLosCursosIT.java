package com.renaser.os.rag.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase.PreguntarRenasiaCommand;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.application.services.herramientas.BuscarEnLosCursosHerramienta;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-255 contra Postgres real con pgvector: SER usa el conocimiento de los cursos que tenia Sparkie —
 * la misma tabla {@code base_conocimiento}, el mismo gate de lecciones visibles de {@code academy} — y
 * el chat del curso del APK viejo (que todavia pide {@code COURSE_TUTOR}) lo responde SER.
 *
 * <p>Lo unico doblado es lo que sale a la IA: los embeddings ({@link EmbeddingPort}, por palabras en
 * comun, asi el orden por distancia coseno es predecible) y el modelo ({@link ChatIAPort}, que guarda
 * la consulta y contesta "ok"). El resto es el camino real: el caso de uso, el gate de
 * {@code academy}, la consulta nativa de pgvector, la herramienta registrada en el servicio de
 * herramientas y la persistencia de los mensajes.
 *
 * <p>Fixture: un aprendiz en el dia 5, el curso A (abierto) con la leccion del ritual, el curso C
 * (abierto) con la del jugo verde, y el curso B que se desbloquea el dia 60, con una leccion que
 * tambien habla del ritual — nunca se le puede citar.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SerConElMaterialDeLosCursosIT.IaDeMentira.class})
class SerConElMaterialDeLosCursosIT {

    private static final String RITUAL = "El ritual tierra agua fuego de la manana se hace descalzo, respirando "
            + "profundo frente al agua.";
    private static final String JUGO = "El jugo verde se prepara con papaya, apio y pepino, en ayunas.";
    private static final String BLOQUEADO = "Ritual de la manana avanzado del dia sesenta: no se cita antes.";

    @Autowired
    private PreguntarRenasiaUseCase preguntar;
    @Autowired
    private ObtenerHistorialUseCase historial;
    @Autowired
    private EjecutarHerramientaAgenteUseCase herramientas;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IaDeMentira.ConsultasAlModelo consultasAlModelo;

    private final String marca = UUID.randomUUID().toString().substring(0, 8);
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> cursos = new ArrayList<>();
    private String cursoA;
    private UserId aprendiz;

    @BeforeEach
    void sembrar() {
        consultasAlModelo.lista.clear();
        aprendiz = aprendizEnElDia(5);
        cursoA = curso("a", null);
        leccionConMaterial(cursoA, RITUAL);
        leccionConMaterial(curso("c", null), JUGO);
        leccionConMaterial(curso("b", (short) 60), BLOQUEADO);
    }

    @AfterEach
    void limpiar() {
        // Sin esto, los chunks quedan con leccion_id NULL (ON DELETE SET NULL) y pasarian el filtro de
        // cualquier otra prueba: material sin leccion es visible para todos.
        jdbcTemplate.update("DELETE FROM renaser.base_conocimiento WHERE documento_id = ?", marca);
        cursos.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.cursos WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("SER pregunta como se hace el ritual y recibe la leccion del curso, nunca la bloqueada")
    void serRecibeElMaterialDeLosCursos() {
        preguntarYEsperar(AgenteConversacional.COMPANION, "como se hace el ritual de la manana?", null, null);

        Consulta consulta = unicaConsulta();
        assertThat(consulta.agente()).isEqualTo(AgenteConversacional.COMPANION);
        assertThat(consulta.contexto()).contains(RITUAL).doesNotContain(BLOQUEADO);
        assertThat(consulta.herramientas()).extracting(DefinicionHerramienta::nombre)
                .contains(BuscarEnLosCursosHerramienta.NOMBRE);
    }

    @Test
    @DisplayName("buscar_en_los_cursos devuelve el material visible del tema que pide SER, sin el bloqueado")
    void laHerramientaBuscaEnLosCursos() {
        ResultadoHerramienta resultado = herramientas.ejecutar(aprendiz, new InvocacionHerramienta(
                BuscarEnLosCursosHerramienta.NOMBRE,
                Map.of(BuscarEnLosCursosHerramienta.ARGUMENTO_CONSULTA, "jugo verde papaya")));

        String texto = ((ResultadoHerramienta.Exito) resultado).contenido();
        assertThat(texto.indexOf(JUGO)).as("el mas parecido va primero").isGreaterThan(0)
                .isLessThan(texto.indexOf(RITUAL));
        assertThat(texto).doesNotContain(BLOQUEADO);
    }

    @Test
    @DisplayName("el chat del curso del APK viejo (COURSE_TUTOR) lo responde SER, acotado al curso, y queda en su historial")
    void elChatDelCursoDelApkViejoLoRespondeSer() {
        preguntarYEsperar(AgenteConversacional.COURSE_TUTOR, "que dice del jugo verde y del ritual?", cursoA,
                "el curso \"A\", la leccion \"Ritual\"");

        Consulta consulta = unicaConsulta();
        assertThat(consulta.agente()).isEqualTo(AgenteConversacional.COMPANION);
        assertThat(consulta.ambito()).isEqualTo("el curso \"A\", la leccion \"Ritual\"");
        assertThat(consulta.contexto()).as("solo el curso desde el que pregunta").contains(RITUAL)
                .doesNotContain(JUGO).doesNotContain(BLOQUEADO);
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT agente FROM renaser.mensajes_renasia "
                + "WHERE usuario_id = ?", String.class, aprendiz.value())).containsExactly("COMPANION");
        assertThat(historial.obtenerHistorial(aprendiz, AgenteConversacional.COURSE_TUTOR, null, 10).mensajes())
                .extracting(MensajeRenasia::contenido)
                .containsExactly("ok", "que dice del jugo verde y del ritual?");
    }

    private void preguntarYEsperar(AgenteConversacional agente, String pregunta, String cursoId, String ambito) {
        preguntar.preguntar(new PreguntarRenasiaCommand(aprendiz, agente, pregunta, ambito, cursoId, null))
                .collectList().block();
    }

    private Consulta unicaConsulta() {
        assertThat(consultasAlModelo.lista).hasSize(1);
        return consultasAlModelo.lista.get(0);
    }

    private UserId aprendizEnElDia(int dia) {
        UUID id = UUID.randomUUID();
        usuarios.add(id);
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture SER cursos', 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, ?)",
                id, dia);
        return UserId.of(id);
    }

    private String curso(String sufijo, Short diaDesbloqueo) {
        String id = "curso-" + sufijo + "-" + marca;
        cursos.add(id);
        jdbcTemplate.update("""
                INSERT INTO renaser.cursos (id, slug, titulo, publicado, acceso, dia_desbloqueo)
                VALUES (?, ?, ?, true, CAST('ABIERTO' AS renaser.acceso_curso), CAST(? AS smallint))
                """, id, id, "Curso " + sufijo, diaDesbloqueo);
        return id;
    }

    private void leccionConMaterial(String cursoId, String contenido) {
        String seccion = "seccion-" + cursoId;
        String leccion = "leccion-" + cursoId;
        jdbcTemplate.update("INSERT INTO renaser.secciones_curso (id, curso_id, titulo) VALUES (?, ?, 'Seccion')",
                seccion, cursoId);
        jdbcTemplate.update("INSERT INTO renaser.lecciones (id, curso_id, seccion_id, titulo) VALUES (?, ?, ?, ?)",
                leccion, cursoId, seccion, "Leccion de " + cursoId);
        jdbcTemplate.update("""
                INSERT INTO renaser.base_conocimiento (tipo_fuente, clase, documento_id, leccion_id, contenido, embedding)
                VALUES ('TRANSCRIPCION_CLASE', ?, ?, ?, ?, CAST(? AS vector))
                """, cursoId, marca, leccion, contenido, IaDeMentira.literal(IaDeMentira.vectorDe(contenido)));
    }

    /** La IA doblada: embeddings por palabras en comun y un modelo que solo guarda lo que recibio. */
    @TestConfiguration(proxyBeanMethods = false)
    static class IaDeMentira {

        /** Lo que le llego al modelo, en orden. */
        static final class ConsultasAlModelo {
            final List<Consulta> lista = new CopyOnWriteArrayList<>();
        }

        @Bean
        ConsultasAlModelo consultasAlModelo() {
            return new ConsultasAlModelo();
        }

        @Bean
        @Primary
        ChatIAPort modeloQueGuarda(ConsultasAlModelo consultasAlModelo) {
            return consulta -> {
                consultasAlModelo.lista.add(consulta);
                return Flux.just(new EventoRenasia.Texto("ok"), new EventoRenasia.Fin());
            };
        }

        @Bean
        @Primary
        EmbeddingPort embeddingPorPalabras() {
            return IaDeMentira::vectorDe;
        }

        /** Una dimension por palabra (de 4 letras o mas) y una fija chica, para no tener vectores nulos. */
        static List<Float> vectorDe(String texto) {
            float[] vector = new float[ChunkConocimiento.DIMENSION_EMBEDDING];
            vector[vector.length - 1] = 0.01f;
            String plano = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            Arrays.stream(plano.split("[^a-z]+")).filter(palabra -> palabra.length() >= 4)
                    .forEach(palabra -> vector[Math.floorMod(palabra.hashCode(), vector.length - 1)] += 1f);
            List<Float> resultado = new ArrayList<>(vector.length);
            for (float valor : vector) {
                resultado.add(valor);
            }
            return resultado;
        }

        static String literal(List<Float> vector) {
            return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        }
    }
}
