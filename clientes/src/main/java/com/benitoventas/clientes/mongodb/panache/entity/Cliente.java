package com.benitoventas.clientes.mongodb.panache.entity;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "Clientes")
public class Cliente extends PanacheMongoEntity {
    public ObjectId userId;
    public String name;
    public String email;
    public String phone;
}
