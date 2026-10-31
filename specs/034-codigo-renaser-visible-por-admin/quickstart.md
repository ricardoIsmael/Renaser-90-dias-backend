# Quickstart: comprobar «Código Renaser en las fichas»

Siempre en local, con base clonada y build debug; nunca contra producción.

## Backend

1. Pruebas de los módulos tocados (un worktree propio, `MAVEN_OPTS=-Xmx1500m`; sin `verify` completo en la máquina compartida):
   `./mvnw -q -Dtest='CatalogoDeFuncionalidadesTest,EvaluacionDeFuncionalidadesTest,CodigoRenaserEnFichasTest,VisibilidadDelCodigoRenaserServiceTest,ConsolaDeFuncionalidadesServiceTest,AcompanamientoDeAlumnoServiceTest,RadarDePersonaServiceTest,CodigoRenaserEnFichasControllerTest,ArchitectureTest,EndpointAuthorizationDeclarationTest,FuncionalidadDeclaradaTest,RutasCubiertasPorElFiltroTest,UserRoleTest' test`
2. IT del escenario de punta a punta: `./mvnw -q -Dit.test=CodigoRenaserEnFichasIT verify` (Testcontainers: Postgres y Redis).

## Escenario a mano

1. Entrar como **mentor** de un grupo con un aprendiz y abrir su ficha: sin la sección «Código Renaser» (apagado de fábrica). `GET …/mentor/groups/{g}/learners/{u}/radar` → 404.
2. Entrar como **líder**: lo mismo por el grupo y por «Todos los aprendices».
3. Entrar como **Administración**: Yo → Administración → Más opciones → «Código Renaser en las fichas» → encender → confirmar. «Último cambio: <tu nombre>, <fecha>».
4. Volver al mentor y al líder: la ficha (al releerla) trae la sección con todos los registros, también los hechos mientras estuvo apagado.
5. Apagar de nuevo: desaparece. Administración sigue viendo todo. La propia persona sigue viendo el suyo.
6. Consola de Soporte TI → «Código Renaser en las fichas»: los tres roles reflejan el mismo estado.
