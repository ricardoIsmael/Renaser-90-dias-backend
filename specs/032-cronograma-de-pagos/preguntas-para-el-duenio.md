# Cronograma de pagos: preguntas para el dueño

> **Respondidas el 2026-11-01** (con la maqueta «Mi ingreso»). Las respuestas están en la sección «Clarifications» de [`spec.md`](./spec.md) y en `decisiones-del-dueno-2026-10-31.md`. Este archivo queda como registro de lo que se preguntó.

**Para mostrarlas junto con tu maqueta.** Son 10, en simple. Cada una trae 2 o 3 opciones y **la recomendada va primero** (con una línea que explica por qué). Si no marcas nada, se toma la A.
Lo que ya dijiste: «El sistema debe de tener cronograma de pagos junto con el diagnóstico como así», «Prioritario: Pagos» y «el de pagos te mandaré imágenes de cómo será».
La spec en borrador está en [`spec.md`](./spec.md); todo lo que no dijiste tú está ahí marcado como **[NEEDS CLARIFICATION]**.

Primero lo más importante (1 a 3), porque cambian el tamaño del trabajo. Las demás (4 a 10) se pueden contestar rápido.

---

## 1. ¿Qué quisiste decir con «junto con el diagnóstico»?

Tu frase se puede leer de tres maneras. Elige la que más se parezca a lo que imaginabas.

- **A (recomendada): al aceptar a la persona, en la misma hoja.** Hoy, al aceptar una solicitud, Administración elige formación, duración, rol y «con o sin diagnóstico». Se suma una decisión más: «con o sin cronograma de pagos», y ahí mismo sale el plan de esa formación para confirmarlo. Es lo que más se parece a lo que ya decidiste para las solicitudes, y le ahorra a Administración armar el cronograma en otro lado.
- **B: la persona lo ve justo después de su diagnóstico, en su primer ingreso.** Entra, completa su ficha o diagnóstico, y a continuación ve su cronograma antes de los Términos. Se puede **sumar a la A** (Administración lo define al aceptar; la persona lo ve al entrar).
- **C: el diagnóstico decide el plan.** Las respuestas de la persona sobre su economía (ingresos, deudas, utilidad del negocio) servirían para proponerle un plan de pagos. **No lo recomiendo**: mezcla datos delicados con cobranza y obliga a inventar reglas («si gana tanto, paga tanto»).

**Y aparte (1b):** ¿el cronograma vale para **cualquier rol** (como el diagnóstico, decisión 025-1 B) o **solo para aprendices**?
- **A (recomendada): cualquier rol, a elección de Administración al aceptar.** Es lo mismo que decidiste para el diagnóstico; por defecto viene «sin cronograma» para el personal.
- **B: solo aprendices.** Más simple, pero si un mentor o guía también paga algo no hay cómo llevarlo.

---

## 2. ¿Se cobra dentro de la app o solo se anota lo que ya se pagó afuera?

- **A (recomendada): solo se anota.** Administración registra cada pago que recibe por transferencia, Yape, efectivo o lo que sea. La app muestra el cronograma y quién está al día, pero **no mueve dinero**. Es lo más rápido de tener y no depende de ningún banco ni pasarela.
- **B: se cobra dentro de la app** (tarjeta, billetera digital). La persona paga con un botón y el pago se registra solo. Hace falta contratar una pasarela de pagos, cuesta comisiones y **las tiendas de aplicaciones tienen reglas propias sobre cobrar dentro de la app que habría que revisar antes**. Sería otra spec, más larga.
- **C: primero A y B más adelante.** Se diseña el cronograma pensando en que algún día pueda cobrar, pero hoy solo se anota.

---

## 3. ¿Quién pone los montos y las cuotas?

- **A (recomendada): un plan por formación y duración, que Administración puede ajustar a una persona.** Por ejemplo «Diplomado · 1 año: S/ X en 12 cuotas». Al aceptar a alguien, el plan sale solo y Administración lo confirma o lo cambia para esa persona (una beca, un descuento, pagar todo junto). Si después cambias el plan, **no se mueve nada a quienes ya tienen su cronograma**.
- **B: persona por persona.** Administración escribe el cronograma de cada una. No hay plantilla. Flexible, pero lento si entran 30 a la vez.
- **C: la persona elige entre varios planes** (por ejemplo, pago único o en cuotas). Más cómodo para ella, pero obliga a definir cuáles planes existen y cómo se elige.

---

## 4. ¿Desde cuándo corren las fechas de las cuotas?

El problema: la persona elige su **Día 1 después** de que la aceptan. La cuota 1 puede ser «al aceptarla» y las demás, según su Día 1.

- **A (recomendada): cada cuota vence tantos días después del Día 1 de la persona** (la primera puede ser «al aceptar»). La app ya funciona así con todo lo demás: el tiempo es de la persona. Mientras no elija su Día 1, el cronograma muestra los montos y dice que las fechas se confirman al elegirlo.
- **B: fechas fijas del calendario** que escribe Administración (por ejemplo, «el 5 de cada mes»). Fácil de entender; no sigue a la persona si empieza tarde.
- **C: desde el día en que se la aceptó.** Simple, pero si tarda en elegir su Día 1 se le puede vencer una cuota antes de empezar.

---

## 5. ¿Cómo se registra un pago?

- **A (recomendada): lo registra Administración.** La persona paga y manda su captura por donde hoy lo hace (WhatsApp o el chat de soporte); Administración marca la cuota «pagada» con fecha, medio y, si quiere, la foto del comprobante. Si se equivoca, **anula con un motivo** (no se borra, queda en el historial) y registra el correcto.
- **B: la persona sube su comprobante desde la app** y la cuota queda «en revisión» hasta que Administración la confirma. Ahorra el WhatsApp, pero es una pantalla más y un estado más.
- **C: las dos.** La persona puede subir su comprobante o Administración lo anota directo. Es lo más completo y lo más largo de construir.

---

## 6. ¿Quién ve el cronograma?

El dinero es lo más delicado que maneja la app.

- **A (recomendada): la persona ve el suyo; Administración y Alquimista ven todos; nadie más.** Mentores, guías, líderes de mentores y Soporte TI **no ven nada de pagos**. Es lo mismo que pasa hoy con la Caja Renaser, que solo opera Administración.
- **B: igual que A, pero el mentor y el líder ven un semáforo sin montos** («al día» / «atrasado») de sus aprendices, para poder acompañarlos. Útil, pero abre la puerta a que el dinero influya en el trato.
- **C: Administración, Alquimista y también el líder de mentores ven los montos.** Si el líder participa en la cobranza.

---

## 7. ¿Cuándo se avisa y qué pasa si alguien no paga a tiempo?

- **A (recomendada): solo avisos, sin bloqueos.** Un aviso 3 días antes, otro el día que vence y otro si se atrasa (por la bandeja, el aviso del teléfono y un mensaje del programa en su chat de soporte, como hace la Caja). Administración ve a los atrasados en un contador. **La persona sigue usando la app con normalidad.** Se puede cambiar después sin tocar nada de lo ya hecho.
- **B: avisos y, además, se limita algo** (por ejemplo, no puede cambiar de formación ni recibir su Caja mientras deba). Hay que decir **qué** se limita y desde cuántos días.
- **C: avisos y, pasados N días, la cuenta se pausa.** Es lo más fuerte: pausar una cuenta afecta su semáforo, su ranking y su acceso. Habría que decir cuántos días y qué se vuelve a activar al pagar.

*(Los números 3 días antes / el día / de gracia son una sugerencia: dime los que prefieres.)*

---

## 8. ¿Qué pasa con la gente que ya está dentro?

Ya hay personas en la Escuela que no tienen ningún cronograma.

- **A (recomendada): quedan sin cronograma** y Administración se lo asigna a quien quiera, una por una. Es lo mismo que decidiste con el diagnóstico («a quienes ya están dentro, Administración les puede asignar»). No hay sorpresas el día que se publique.
- **B: todas reciben uno automáticamente** con el plan de la Escuela. Requiere que el plan de la Escuela ya esté definido y que las fechas tengan sentido para quien lleva semanas dentro.
- **C: solo a las que Administración marque en una lista**, todas a la vez. Parecido a A pero en bloque.

---

## 9. ¿La app emite boletas o facturas?

- **A (recomendada): no.** El comprobante legal se sigue emitiendo fuera de la app, como hoy. La app solo lleva el control de quién pagó.
- **B: la app guarda el número de la boleta o factura** que se emitió afuera y lo muestra junto al pago. Fácil de sumar y útil para ordenar las cuentas.
- **C: la app emite el comprobante electrónico.** Hay que conectarse con un proveedor autorizado por SUNAT; es otra spec, aparte y grande.

---

## 10. ¿Qué pasa con el cronograma si la persona cambia de duración o de formación?

Ya decidiste que se puede **cambiar la duración** de alguien que empezó (con motivo) y que un aprendiz **pasa a otra formación** cuando termina. ¿Y su cronograma?

- **A (recomendada): lo pagado nunca se toca y Administración ajusta lo que falta.** El sistema marca el cronograma «por revisar» y avisa a Administración; ella lo corrige a mano dejando el motivo. Nada se recalcula solo, así no hay cobros inventados.
- **B: se arma uno nuevo automáticamente** con el plan de la formación o duración nueva, y lo ya pagado se descuenta. Más cómodo, pero obliga a definir cómo se descuenta (¿se abona? ¿se pierde?).
- **C: se cancela lo pendiente** y empieza uno nuevo desde cero cuando Administración lo cree.

*(En los tres casos, el cronograma anterior queda en el historial y nunca se mezcla con el nuevo.)*

---

# Datos que necesito que me des (no son preguntas)

Cuando mandes tu maqueta, ayudaría traer también:

1. **Los montos**: el precio total de la Escuela, de la Certificación y del Diplomado (3 meses y 1 año).
2. **Las cuotas**: ¿en cuántas se paga cada una?, ¿cada cuánto?, ¿hay pago único?, ¿hay una cuota de inicio?
3. **La moneda**: ¿solo soles, o también dólares? (el diagnóstico ya habla de «S/» y «US$»).
4. **Los medios de pago** que acepta Renaser y cómo se pagan (Yape, Plin, transferencia, efectivo…): cuenta, número, titular.
5. **Las instrucciones de pago** que verá la persona y **a dónde manda su comprobante**.
6. **Si hay descuentos, becas o promociones**, y cómo se expresan.
7. **Quién opera pagos hoy** (¿solo Administración o también Alquimista?).
8. **Cuánto tiempo hay que conservar los registros de pago** si alguien elimina su cuenta (puede haber una obligación legal; conviene consultarlo).
9. **Los textos**: cómo quieres que hable la app de plata (por ejemplo, cómo se dice «atrasada» sin que suene a reproche).

# Qué hace falta que muestre tu maqueta

1. **Dónde se ve** el cronograma (la persona y Administración).
2. **Cómo es una cuota** (número, monto, fecha, estado, comprobante).
3. **Los estados** (pagada, por vencer, vence hoy, atrasada) y cómo se distinguen.
4. **La parte de Administración**: lista, contadores, filtros, detalle y «Registrar pago».
5. **La hoja de aceptar**: cómo se suma «con o sin cronograma» junto a «con o sin diagnóstico».
6. **Cualquier cosa que quieras ver y que no esté aquí.**
