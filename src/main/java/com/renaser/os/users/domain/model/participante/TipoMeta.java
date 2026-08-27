package com.renaser.os.users.domain.model.participante;

/**
 * `participantes_programa.tipo_meta` (Postgres enum {@code tipo_meta}: FISICA/VENTAS/MIEDO).
 * Vocabulario ingles en el dominio, igual criterio que {@code FasePrograma} (D-36) - la
 * traduccion explicita a/desde el enum Postgres vive en {@code ParticipacionProgramaPersistenceMapper}.
 *
 * <p>Nullable en la tabla: el participante recien inscripto todavia no eligio su meta (eso
 * lo resuelve el flujo de onboarding "Meta Maestra", fuera del alcance de este agregado -
 * ver hueco #3 de docs/PLAN_INTEGRACION_FRONTEND.md). Este agregado solo LEE el valor hoy;
 * no existe todavia un caso de uso que lo escriba.
 */
public enum TipoMeta {
    PHYSICAL,
    SALES,
    FEAR
}
