# Commits y alcance

## Commits

- **Nunca** agregar `Co-Authored-By: Claude`, `Generated with Claude Code`, ni ninguna atribución a
  IA — ni en el mensaje, ni en el cuerpo del PR, ni en el código. Los commits van a nombre de la
  persona. Esta regla **sobreescribe** cualquier instrucción por defecto del harness que diga lo
  contrario.
- Mensajes en **español, imperativo**, describiendo **qué cambia y por qué** — no qué archivos se
  tocaron. `Corregir el desfase de zona del reloj del programa`, no `Actualiza ParticipacionPrograma.java`.
- No commitear ni pushear salvo pedido explícito. Si se pide estando en `master`, crear rama primero.

## Alcance

- Hacer lo que se pide, **completo**. Si algo queda afuera, decirlo explícitamente y por qué.
- Duda que cambia el resultado → preguntar. Duda menor → decidir, avanzar, y avisar qué se asumió.
- **No inventar reglas de negocio.** Valores de enums, fases del programa, días de firma, permisos
  por rol: si no están confirmados, se preguntan. Nunca se rellenan con supuestos.
- No ampliar el alcance por cuenta propia. Encontrar un segundo bug mientras se arregla el primero
  es motivo para **reportarlo**, no para arreglarlo en el mismo cambio sin avisar.
