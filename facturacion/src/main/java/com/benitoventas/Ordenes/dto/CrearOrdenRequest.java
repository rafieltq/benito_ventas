package com.benitoventas.Ordenes.dto;

import java.util.List;

public record CrearOrdenRequest(String clienteId, List<Item> items) {
    public record Item(String productoId, Integer cantidad) {}
}
