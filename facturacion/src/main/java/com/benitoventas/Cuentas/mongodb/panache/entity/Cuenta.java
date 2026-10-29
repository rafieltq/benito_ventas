package com.benitoventas.Cuentas.mongodb.panache.entity;

import java.math.BigDecimal;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;

import org.bson.types.ObjectId;

@MongoEntity(collection = "Cuentas")
public class Cuenta extends PanacheMongoEntity {
    ObjectId clienteId;
    BigDecimal balance = BigDecimal.ZERO;
    List<Transaccion> transacciones = List.of();

    public void setClienteId(ObjectId clienteId) {
        this.clienteId = clienteId;
    }

    public ObjectId getClienteId() {
        return clienteId;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setTransacciones(List<Transaccion> transacciones) {
        this.transacciones = transacciones;
    }

    public List<Transaccion> getTransacciones() {
        return transacciones;
    }
}
