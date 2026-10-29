package com.benitoventas.Ordenes.mongodb.panache.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "Ordenes")
public class Orden extends PanacheMongoEntity {
}
