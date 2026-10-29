package com.benitoventas.Jobs.mongodb.panache.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;


import com.benitoventas.Jobs.mongodb.panache.entity.TipoJob;
import com.benitoventas.Jobs.mongodb.panache.entity.EstadoJob;

@MongoEntity(collection = "Jobs")
public class Job extends PanacheMongoEntity {
  TipoJob tipo;
  String contenido;
  EstadoJob estado = EstadoJob.PENDING;

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
}
