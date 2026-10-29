package com.benitoventas.Cuentas.mongodb.panache.entity;

import com.benitoventas.Cuentas.mongodb.panache.entity.TipoTransaccion;
import java.math.BigDecimal;
import java.time.Instant;
import org.bson.types.ObjectId;


public class Transaccion {
  String id;
  TipoTransaccion tipo;
  BigDecimal monto;
  Instant fecha;
  String descripcion;
  ObjectId referenciaId;


  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TipoTransaccion getTipo() {
    return tipo;
  }

  public void setTipo(TipoTransaccion tipo) {
    this.tipo = tipo;
  }

  public BigDecimal getMonto() {
    return monto;
  }

  public void setMonto(BigDecimal monto) {
    this.monto = monto;
  }

  public Instant getFecha() {
    return fecha;
  }

  public void setFecha(Instant fecha) {
    this.fecha = fecha;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public ObjectId getReferenciaId() {
    return referenciaId;
  }

  public void setReferenciaId(ObjectId referenciaId) {
    this.referenciaId = referenciaId;
  }
}
