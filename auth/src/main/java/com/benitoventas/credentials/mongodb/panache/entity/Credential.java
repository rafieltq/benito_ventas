package com.benitoventas.credentials.mongodb.panache.entity;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Password-based login for a user. The user's ID is also this document's ID,
 * so MongoDB cannot store multiple credentials for the same user.
 */
@MongoEntity(collection = "Credentials")
public class Credential extends PanacheMongoEntityBase {
    @BsonId
    public ObjectId userId;

    public String email;

    @JsonIgnore
    public String passwordHash;
}
