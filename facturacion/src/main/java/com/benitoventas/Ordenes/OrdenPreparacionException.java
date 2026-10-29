package com.benitoventas.Ordenes;

import jakarta.ws.rs.core.Response.Status;

public class OrdenPreparacionException extends RuntimeException {
    private final Status estadoHttp;

    public OrdenPreparacionException(Status estadoHttp, String mensaje) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
    }

    public OrdenPreparacionException(Status estadoHttp, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.estadoHttp = estadoHttp;
    }

    public Status getEstadoHttp() {
        return estadoHttp;
    }
}
