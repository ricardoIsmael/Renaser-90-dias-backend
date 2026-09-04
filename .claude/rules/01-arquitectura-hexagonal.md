# Arquitectura: hexagonal por módulo, Spring Modulith entre módulos

La regla de oro, de la que salen todas las demás: **las dependencias apuntan hacia adentro, hacia el
dominio.**

## Qué puede importar cada capa

| Carpeta | ¿Spring / JPA / Jackson? | Qué vive ahí |
|---|---|---|
| `domain/` | **NO. Nunca.** | Reglas de negocio puras. Datos **y** comportamiento |
| `application/` | Mínimo (`@Transactional`) | Casos de uso: una clase por operación del sistema |
| `port/in/`, `port/out/` | No | Interfaces. El puerto dice **qué necesito**, no **cómo se consigue** |
| `adapter/in/`, `adapter/out/` | Sí | HTTP, scheduler, JPA, S3, SMTP |
| `api/` | Sí | Lo único que otros módulos pueden importar |

Si una clase de dominio "necesita" `@Entity`, esa anotación va en un `*JpaEntity` separado de
`adapter/out/persistence/`, con un mapper a mano entre las dos.

## Reglas duras (se verifican en review, y `ArchitectureTest` rompe el build)

- **Un módulo solo importa `<otro>/api/`.** Nunca `otro.domain.*`, nunca `otro.application.*`.
- **Ningún módulo manda SQL nativo contra una tabla ajena.** Para `participantes_programa` la vía es
  `users.api.ParticipacionProgramaFinder` (D-41). Si falta un método, se agrega al puerto.
- **El controller es tonto.** Solo puede: deserializar, `@Valid`, invocar `AccessGuard`, invocar
  **un** caso de uso, mapear la salida. Prohibido: inyectar repositorios o puertos `out`,
  `@Transactional`, `if` de negocio, orquestar dos casos de uso.
- **El caso de uso no conoce HTTP.** Nada de `HttpServletRequest`, `ResponseEntity` ni códigos de
  estado en `application/`.
- **Ninguna clase con anotaciones de dos mundos** (`@Entity` + `@JsonProperty` juntas).
- **Ninguna llamada a un puerto de IA dentro de `@Transactional`** (C-1). Una IA real puede tardar
  45 s reteniendo una conexión de Hikari, y agota el pool para toda la API.
- **Nombres de puerto por intención de negocio**, no por tecnología: `LoadUserPort`, no
  `JpaUserFinder`. Los adaptadores sí nombran la tecnología.
- Prohibidos: `Util`, `Helper`, `Manager`, `Processor`, `Data`, `Info` sueltos.

## Lombok en `domain/`

Permitido: `@Getter`, `@AllArgsConstructor(access = PRIVATE)`, `@EqualsAndHashCode(of = "id")`,
`@Accessors(fluent = true)`. La validación de invariantes va en los factory methods estáticos.

Prohibido sin excepción: `@Data`, `@Setter`, `@NoArgsConstructor` público, y cualquier
`public void setX(...)`. Las raíces de agregado **sí mutan**, pero solo por métodos con nombre de
intención (`suspend()`, `asignarMentor()`, `fijarDia()`), nunca por setters.

## Tamaño

Método ≤ 20 líneas (techo 40). Clase ≤ 150 (techo 300). Parámetros ≤ 3. Anidamiento ≤ 2.
Métodos públicos por clase ≤ 7. Se rompe extrayendo **métodos privados con nombre de intención** —
el método privado existe para ponerle nombre a una idea, no para acortar líneas.
