package com.benitoventas.Ordenes.mongodb.panache.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;

@MongoEntity(collection = "Ordenes")
public class Orden extends PanacheMongoEntity {
  String clienteId;
  Instant fecha;
  List<OrdenItem> items;
  BigDecimal total = new BigDecimal("0.00");
  EstadoOrden estado = EstadoOrden.PENDIENTE; 


  public String getClienteId() {
    return clienteId;
  }

  public void setClienteId(String clienteId) {
    this.clienteId = clienteId;
  }

  public Instant getFecha() {
    return fecha;
  }

  public void setFecha(Instant fecha) {
    this.fecha = fecha;
  }

  public List<OrdenItem> getItems() {
    return items;
  }

  public void setItems(List<OrdenItem> items) {
    this.items = items;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal total) {
    this.total = total;
  }

  public EstadoOrden getEstado() {
    return estado;
  }

  public void setEstado(EstadoOrden estado) {
    this.estado = estado;
  }
}
