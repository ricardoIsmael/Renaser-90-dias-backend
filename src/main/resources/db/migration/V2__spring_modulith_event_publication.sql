-- Tabla requerida por spring-modulith-events-jpa (outbox de eventos de dominio, CLAUDE.MD §4.4).
-- El starter no trae un script propio (verificado: el jar no contiene ningun .sql) -- la
-- documentacion de Spring Modulith espera que el proyecto la cree via su propia migracion.
-- Esquema derivado exactamente del mapeo JPA real de `JpaEventPublication` (Modulith 2.1.0),
-- generado con Hibernate ddl-auto=update contra Postgres y volcado desde information_schema,
-- no inventado a mano.
CREATE TABLE event_publication (
    id UUID NOT NULL,
    completion_attempts INTEGER NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    event_type VARCHAR(255) NOT NULL,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    listener_id VARCHAR(255) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    PRIMARY KEY (id)
);
