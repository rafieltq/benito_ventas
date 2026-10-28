package com.benitoventas.Cuentas.mongodb.panache.entity;

import java.math.BigDecimal;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import com.benitoventas.Cuentas.mongodb.panache.entity.Transaccion;

@MongoEntity(collection = "Cuentas")
public class Cuenta extends PanacheMongoEntity {
    public ObjectId userId;
    public BigDecimal balance = 0;
    public List<Transaccion> transacciones = List.of();

    public void setUserId(ObjectId userId) {
        this.userId = userId;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public void setBalance(Float balance) {
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
