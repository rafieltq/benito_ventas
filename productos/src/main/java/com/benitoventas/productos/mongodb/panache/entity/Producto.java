package com.benitoventas.productos.mongodb.panache.entity;

import java.time.LocalDate;
import java.util.List;

import org.bson.codecs.pojo.annotations.BsonProperty;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;

@MongoEntity(collection = "Productos")
public class Producto extends PanacheMongoEntity {
    public String nombre;
    public String descripcion;
    public Float precio;

    // // will be persisted as a 'birth' field in MongoDB
    // @BsonProperty("birth") // whats bson: is it for? 
    // public LocalDate birthDate;
    //
    // public Status status;

    // return name as lowercase in the model
    public String getNombre() {
        return nombre.toLowerCase();
    }

    // store all names in uppercase in the DB
    public void setNombre(String nombre) {
        this.nombre = nombre.toUpperCase();
    }

    // descripcion accessors
    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    // precio accessors
    public Float getPrecio() {
        return precio;
    }

    public void setPrecio(Float precio) {
        this.precio = precio;
    }
}
