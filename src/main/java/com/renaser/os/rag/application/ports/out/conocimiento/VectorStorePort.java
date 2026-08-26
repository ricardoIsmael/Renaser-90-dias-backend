package com.renaser.os.rag.application.ports.out.conocimiento;

import java.util.List;

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
 * <p><b>CONTRATO COMPARTIDO — firma congelada.</b> {@code Conversacion}/{@code EspejoSombra}
 * (otros agentes de este mismo módulo) programan contra esta interfaz tal cual está.
 */
public interface VectorStorePort {

    /**
     * Busca los {@code topK} fragmentos más parecidos a {@code consulta} (texto libre,
     * no un vector — el adaptador genera el embedding de la consulta internamente vía
     * {@link com.renaser.os.rag.application.ports.out.ia.EmbeddingPort}).
     */
    List<FragmentoRelevante> buscarSimilares(String consulta, int topK);

    record FragmentoRelevante(String contenido, String leccionId, double distancia) {
    }
}
