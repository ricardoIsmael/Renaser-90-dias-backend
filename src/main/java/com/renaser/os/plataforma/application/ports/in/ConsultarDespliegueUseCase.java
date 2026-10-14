package com.renaser.os.plataforma.application.ports.in;

/** El despliegue de una funcionalidad con sus probadores y sus métricas de 24 h (contrato fase 2 §3.4). */
public interface ConsultarDespliegueUseCase {

    DespliegueEnConsola detalle(AccesoDeConsola acceso, String clave);
}
