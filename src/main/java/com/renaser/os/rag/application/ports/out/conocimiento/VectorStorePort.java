package com.renaser.os.rag.application.ports.out.conocimiento;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Puerto propio de búsqueda por similitud — NO la interfaz {@code VectorStore} de
 * Spring AI (docs/MODULO_RAG.md D-45, verificado contra el bytecode de
 * {@code spring-ai-pgvector-store:2.0.0}: su SQL tiene las columnas
 * {@code id/content/metadata/embedding} hardcodeadas, incompatibles con nuestro
 * esquema en español y con {@code tipo_fuente NOT NULL}). La única implementación es
 * {@code PgVectorNativoAdapter}, con SQL nativo contra {@code base_conocimiento} usando
 * el operador {@code <=>} (distancia coseno, coherente con el índice HNSW
 * {@code vector_cosine_ops} ya existente).
 *
 * <p><b>Por qué {@link FiltroLecciones} es parámetro de ESTE puerto, y no algo que el
 * servicio aplica después sobre el resultado.</b> Filtrar en memoria después de traer
 * {@code topK} filas rompe dos cosas a la vez: (1) si de las {@code topK} filas más
 * parecidas la mitad pertenece a lecciones bloqueadas, el aprendiz se queda con menos
 * contexto del que pidió, sin ningún candidato de reemplazo — el WHERE tiene que aplicarse
 * ANTES del {@code ORDER BY ... LIMIT}, no después; y (2) hoy el índice HNSW ya hace todo el
 * trabajo pesado del ranking, así que agregar la condición al mismo WHERE es prácticamente
 * gratis, mientras que traer de más "por las dudas" y descartar en Java no lo es. Por eso el
 * puerto recibe el filtro ya resuelto, no un {@code UserId} que tendría que resolverlo él
 * mismo: {@code PgVectorNativoAdapter} no sabe nada de "quién puede ver qué lección" (esa es
 * una regla de {@code academy}, prohibida de importar acá por Modulith) — solo sabe aplicar
 * un WHERE sobre {@code leccion_id}. QUIÉN puede ver qué lo resuelve
 * {@code ConversacionRenasiaService} ANTES de llamar a este puerto, vía
 * {@code ConsultarLeccionesVisiblesPort} (que a su vez delega en
 * {@code academy.api.LeccionesVisiblesFinder}) — el mismo reparto de responsabilidades que ya
 * usa este módulo para leer el diario en {@code espejosombra} (puerto propio + adaptador que
 * delega en el {@code api} del módulo dueño de la regla).
 *
 * <p><b>CONTRATO COMPARTIDO — firma congelada.</b> {@code Conversacion}/{@code EspejoSombra}
 * (otros agentes de este mismo módulo) programan contra esta interfaz tal cual está. Esta
 * firma cambió una vez (se agregó {@link FiltroLecciones}) para cerrar un bug real: la
 * búsqueda vectorial podía citarle a un aprendiz contenido de una lección que su propio gate
 * de programa de {@code academy} todavía tenía bloqueada.
 */
public interface VectorStorePort {

    /**
     * Busca los {@code topK} fragmentos más parecidos a {@code consulta} (texto libre,
     * no un vector — el adaptador genera el embedding de la consulta internamente vía
     * {@link com.renaser.os.rag.application.ports.out.ia.EmbeddingPort}).
     *
     * @param filtro qué fragmentos son candidatos citables. Los chunks con {@code leccionId}
     *               {@code null} (material general, no ligado a una lección puntual) NUNCA se
     *               filtran, sea cual sea el filtro — ver {@link FiltroLecciones}.
     */
    List<FragmentoRelevante> buscarSimilares(String consulta, int topK, FiltroLecciones filtro);

    record FragmentoRelevante(String contenido, String leccionId, double distancia) {
    }

    /**
     * Qué fragmentos son candidatos citables, por la lección de la que salieron.
     *
     * <p>{@link SinFiltro}: no descarta nada. Es el único caso de uso legítimo hoy de
     * {@code ConocimientoAdminController}/{@code ConocimientoService} si algún día necesitan
     * buscar (hoy no buscan, solo indexan) — un administrador que gestiona la base de
     * conocimiento tiene que poder ver TODO el corpus, no el catálogo recortado de un rol
     * particular.
     *
     * <p>{@link SoloVisibles}: descarta los chunks cuyo {@code leccionId} no está en
     * {@code leccionIds}. Es el caso de {@code ConversacionRenasiaService} (Renasia
     * respondiéndole a un aprendiz): {@code leccionIds} es el resultado de
     * {@code ConsultarLeccionesVisiblesPort.visiblesParaActor}, ya resuelto para ESE actor.
     * Un conjunto vacío es válido y NO es un error: significa "hoy no tiene ningún curso
     * accesible" — el filtro igual deja pasar el material general
     * ({@code leccionId == null}), solo bloquea el contenido ligado a una lección.
     */
    sealed interface FiltroLecciones {

        record SinFiltro() implements FiltroLecciones {
        }

        record SoloVisibles(Set<String> leccionIds) implements FiltroLecciones {

            public SoloVisibles {
                Objects.requireNonNull(leccionIds, "leccionIds no puede ser null");
                leccionIds = Set.copyOf(leccionIds);
            }
        }

        static FiltroLecciones sinFiltro() {
            return new SinFiltro();
        }

        static FiltroLecciones soloVisibles(Set<String> leccionIds) {
            return new SoloVisibles(leccionIds);
        }
    }
}
