package com.benitoventas.Depositos.mongodb.panache.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "Depositos")
public class Deposito extends PanacheMongoEntity {
}
