/**
 * Shared kernel (CLAUDE.MD §5): tipos transversales sin logica de negocio propia.
 *
 * Declarado OPEN a proposito: es el unico modulo cuyos paquetes internos pueden
 * importarse desde cualquier otro modulo. Todo lo que se agregue aca debe ser
 * de verdad transversal — si solo lo usa un modulo, no va aca.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel")
package com.renaser.os.shared;
