package com.benitoventas.Ordenes.dto;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record CrearOrdenRequest(String clienteId, List<Item> items) {
    public record Item(
            String productoId,
            @JsonDeserialize(using = CantidadDeserializer.class) Integer cantidad) {}

    public static class CantidadDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "La cantidad debe ser un número entero.");
            }
            return parser.getIntValue();
        }
    }
}
