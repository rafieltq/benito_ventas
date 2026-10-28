package com.benitoventas.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.benitoventas.credentials.mongodb.panache.entity.Credential;
import com.benitoventas.roles.mongodb.panache.entity.Role;
import com.benitoventas.users.mongodb.panache.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;

class AuthEntityTest {
    private static final CodecRegistry CODECS = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void userProfileAndRoleReferencesRoundTripThroughBson() {
        User user = new User();
        user.id = new ObjectId();
        user.name = "María Pérez";
        user.email = "maria@example.test";
        user.phone = "+5215550100100";
        user.roleIds.add(new ObjectId());
        user.roleIds.add(new ObjectId());

        BsonDocument document = encode(user, User.class);
        User restored = decode(document, User.class);

        assertEquals(user.id, restored.id);
        assertEquals(user.name, restored.name);
        assertEquals(user.email, restored.email);
        assertEquals(user.phone, restored.phone);
        assertEquals(user.roleIds, restored.roleIds);
        assertFalse(document.containsKey("password"));
        assertFalse(document.containsKey("passwordHash"));
    }

    @Test
    void roleAssignmentsSupportMultipleRolesWithoutDuplicatesOrSharedState() {
        User first = new User();
        User second = new User();
        ObjectId initialRole = new ObjectId();
        ObjectId additionalRole = new ObjectId();

        first.roleIds.add(initialRole);
        first.roleIds.add(initialRole);
        assertEquals(Set.of(initialRole), first.roleIds);

        first.roleIds.add(additionalRole);
        assertEquals(Set.of(initialRole, additionalRole), first.roleIds);
        assertNotSame(first.roleIds, second.roleIds);
        assertTrue(second.roleIds.isEmpty());
    }

    @Test
    void credentialUsesUserIdAsItsOnlyBsonIdentity() {
        Credential credential = credential();

        BsonDocument document = encode(credential, Credential.class);
        Credential restored = decode(document, Credential.class);

        assertEquals(credential.userId, document.getObjectId("_id").getValue());
        assertFalse(document.containsKey("userId"));
        assertFalse(document.containsKey("id"));
        assertEquals(credential.userId, restored.userId);
        assertEquals(credential.email, restored.email);
        assertEquals(credential.passwordHash, restored.passwordHash);
    }

    @Test
    void passwordHashIsPersistedButNeverSerializedToJson() throws Exception {
        Credential credential = credential();
        ObjectMapper mapper = new ObjectMapper();

        assertEquals(credential.passwordHash,
                encode(credential, Credential.class).getString("passwordHash").getValue());

        String json = mapper.writeValueAsString(credential);
        assertFalse(mapper.readTree(json).has("passwordHash"));
        assertFalse(json.contains(credential.passwordHash));
        assertEquals(credential.email, mapper.readTree(json).get("email").asText());
    }

    @Test
    void roleRoundTripsThroughBson() {
        Role role = new Role();
        role.id = new ObjectId();
        role.name = "User";
        role.description = "Standard application access";

        Role restored = decode(encode(role, Role.class), Role.class);

        assertEquals(role.id, restored.id);
        assertEquals(role.name, restored.name);
        assertEquals(role.description, restored.description);
    }

    private static Credential credential() {
        Credential credential = new Credential();
        credential.userId = new ObjectId();
        credential.email = "maria@example.test";
        credential.passwordHash = "test-only-hash-not-a-real-password";
        return credential;
    }

    private static <T> BsonDocument encode(T value, Class<T> type) {
        BsonDocument document = new BsonDocument();
        CODECS.get(type).encode(new BsonDocumentWriter(document), value,
                EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        return CODECS.get(type).decode(new BsonDocumentReader(document),
                DecoderContext.builder().build());
    }
}
