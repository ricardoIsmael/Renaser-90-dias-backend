---
paths:
  - "specs/**"
  - "src/test/**"
  - "scripts/trazabilidad-specs*"
---

# Trazabilidad de specs

Cada requisito (`FR-NNN`) y cada criterio de éxito (`SC-NNN`) de una spec que se exige tiene que
tener, por lo menos, una prueba que lo cubra. Lo comprueba `scripts/trazabilidad-specs.mjs`
(sin dependencias, solo Node 18 o más; sus propias pruebas: `node --test scripts/trazabilidad-specs.test.mjs`).

## La marca

La prueba que cubre un requisito lleva, en un comentario o en su nombre, la marca `spec NNN FR-NNN`:

```java
// spec 037 FR-005
@Test void unaFuenteSinDobleConteo() { … }
```

```ts
// spec 037 FR-006, SC-002
it('km = distancia agregada; sin ella, pasos × 0,75 m', () => { … });
```

```yaml
# spec 037 FR-007
- tapOn: "Registrar"
```

- Varios requisitos en una marca: `// spec 037 FR-005, FR-006 SC-002` (separados por coma, punto y
  coma, `/`, `+`, `y` o `e`). Mayúscula o minúscula en `spec` da igual.
- Una marca solo cuenta en una **prueba**: en el backend, todo texto bajo `src/test/`; en la app,
  los Jest (`__tests__` o `*.test.*` / `*.spec.*` bajo `src/`) y los flujos de Maestro (`.maestro/**.yaml`).
  Una marca en código de producción o en un `.md` no cubre nada.
- El número de la marca es el de la spec (`037`), no el de la tarea ni el de la issue. Una marca de
  la 036 no cubre un requisito de la 037.

## Qué cuenta como requisito

Solo los que la spec **define** en su `spec.md`: una viñeta `- **FR-001**: …`, un encabezado
`### FR-001 …` o la primera celda de una tabla (`| FR-001 | … |`). Que un párrafo mencione `FR-001`
no lo define.

Si un criterio no se puede probar con código (por ejemplo «el dueño aprueba el texto»), la spec lo
dice en su misma viñeta con `[sin prueba automática: el motivo]`. Queda exento, con su motivo a la
vista en la tabla. Con el corchete vacío también queda exento, pero la tabla muestra «(sin motivo)»:
el revisor lo ve y lo pide.

## Qué specs se exigen

`specs/trazabilidad.json`: un arreglo con el número de cada spec exigida (`["035","036","037"]`;
si dos specs comparten número, el nombre completo de la carpeta). **Una spec entra a la lista en el
mismo cambio que le pone sus marcas**, no antes: una spec en la lista sin su carpeta, o sin ningún
`FR-`/`SC-` definido, es un error de configuración.

## Cómo se corre

```bash
node scripts/trazabilidad-specs.mjs ../Renaser-90-dias-frontend- --detalle
node scripts/trazabilidad-specs.mjs --spec 037 --backend .claude/worktrees/spec-037 ../Renaser-90-dias-frontend-/.claude/worktrees/impl-037
```

- Sin la ruta de la app solo se buscan marcas en el backend (y lo avisa).
- `--spec 037` revisa solo esa spec y no lee la lista: sirve para probar una spec contra su rama.
- `--md archivo` agrega las tablas en Markdown (el CI lo apunta a `$GITHUB_STEP_SUMMARY`).

Salida: `0` todo cubierto; `1` hay un requisito exigido sin prueba o una marca que apunta a un
requisito que la spec no define (casi siempre un error de tipeo); `2` configuración rota (lista
ilegible, spec sin carpeta, ruta inexistente).

## Lo que esta regla NO garantiza

La marca prueba que alguien declaró que esa prueba cubre el requisito, no que lo cubra bien. Una
marca puesta en una prueba que no verifica el requisito es peor que la falta, porque tapa el hueco.
Por eso:

- **No se marca por marcar.** Si la prueba no falla cuando el requisito se rompe, no lleva la marca.
- Al cerrar una spec se revisa la tabla a mano, requisito por requisito, y se anota en su
  `validation.md` lo que quedó sin prueba y por qué.
