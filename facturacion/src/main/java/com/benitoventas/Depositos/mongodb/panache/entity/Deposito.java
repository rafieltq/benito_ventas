package com.benitoventas.Depositos.mongodb.panache.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import com.benitoventas.Depositos.mongodb.panache.entity.EstadoDeposito;

import java.math.BigDecimal;

@MongoEntity(collection = "Depositos")
public class Deposito extends PanacheMongoEntity {
    public String cuentaId;
    public String referencia;
    public BigDecimal monto;
    public String fecha;
    public EstadoDeposito estado = EstadoDeposito.PENDIENTE;


    public String getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(String cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }
}
