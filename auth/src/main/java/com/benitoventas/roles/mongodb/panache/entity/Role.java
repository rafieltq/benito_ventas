package com.benitoventas.roles.mongodb.panache.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * A named authorization role that may be assigned to multiple users.
 */
@MongoEntity(collection = "Roles")
public class Role extends PanacheMongoEntity {
    public String name;
    public String description;
}
