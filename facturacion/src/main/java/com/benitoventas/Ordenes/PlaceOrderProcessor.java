package com.benitoventas.Ordenes;

import jakarta.enterprise.context.ApplicationScoped;

import com.benitoventas.Ordenes.mongodb.panache.entity.Orden;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.smallrye.reactive.messaging.annotations.Blocking;

import org.bson.types.ObjectId;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlaceOrderProcessor {

    @Inject
    OrdenesService ordenesService;

    @Incoming("place-order-in") 
    @Blocking             
    public void process(String ordenId) throws InterruptedException {
        // Simular procesamiento de la orden
        // Thread.sleep(1000); // Simula un retraso en el procesamiento
        try {
            ordenesService.placeOrder(new ObjectId(ordenId));
        } catch (Exception e) {
            // Manejar la excepción según sea necesario
            throw new RuntimeException("Error al procesar la orden: " + ordenId, e);
        }

        // 1. asignar orden al procesador para prevenir cobros dobles
        // 2. buscar cuenta por sub (userId)
        // 2.1 validar si el saldo es mayor al total de la orden
        // 3. si es mayor, descontar el total de la orden del saldo de la cuenta
        // 4. si es menor, marcar la orden como PAGO_PENDIENTE
    }
}
