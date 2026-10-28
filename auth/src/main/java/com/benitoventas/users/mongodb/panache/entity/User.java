package com.benitoventas.users.mongodb.panache.entity;

import java.util.HashSet;
import java.util.Set;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * User profile and role assignments. Authentication secrets belong to Credential.
 */
@MongoEntity(collection = "Users")
public class User extends PanacheMongoEntity {
    public String name;
    public String email;
    public String phone;
    public Set<ObjectId> roleIds = new HashSet<>();
    public Boolean isActive = false;
}
