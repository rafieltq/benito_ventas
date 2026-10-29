package com.benitoventas.Jobs.mongodb.panache.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;


import com.benitoventas.Jobs.mongodb.panache.entity.TipoJob;
import com.benitoventas.Jobs.mongodb.panache.entity.EstadoJob;

import java.time.Instant;

@MongoEntity(collection = "Jobs")
public class Job extends PanacheMongoEntity {
  TipoJob tipo;
  String contenido;
  EstadoJob estado = EstadoJob.PENDING;
  String error;
  Instant created_at;
  Instant processed_at;

  public TipoJob getTipo() {
    return tipo;
  } 

  public void setTipo(TipoJob tipo) {
    this.tipo = tipo;
  }

  public String getContenido() {
    return contenido;
  }
  
  public void setContenido(String contenido) {
    this.contenido = contenido;
  }

  public EstadoJob getEstado() {
    return estado;
  }

  public void setEstado(EstadoJob estado) {
    this.estado = estado;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public Instant getCreated_at() {
    return created_at;
  }

  public void setCreated_at(Instant created_at) {
    this.created_at = created_at;
  }

  public Instant getProcessed_at() {
    return processed_at;
  }

  public void setProcessed_at(Instant processed_at) {
    this.processed_at = processed_at;
  } 
}
