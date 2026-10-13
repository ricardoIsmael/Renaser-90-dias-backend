package com.renaser.os.plataforma.application.ports.in;

import java.util.List;

/** El catálogo entero con el estado de cada interruptor, para la consola Soporte TI. */
public interface ConsultarFuncionalidadesUseCase {

    List<FuncionalidadEnConsola> listar(AccesoDeConsola acceso);
}
