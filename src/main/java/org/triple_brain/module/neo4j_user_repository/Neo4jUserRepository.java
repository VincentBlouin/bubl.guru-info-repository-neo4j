/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import org.neo4j.rest.graphdb.query.QueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;
import org.triple_brain.module.model.User;
import org.triple_brain.module.model.UserUris;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jFriendlyResource;
import org.triple_brain.module.repository.user.ExistingUserException;
import org.triple_brain.module.repository.user.NonExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Date;
import java.util.Map;

import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.map;
import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.wrap;

public class Neo4jUserRepository implements UserRepository {

    public static String neo4jType = "user",
            returnQueryPart =
                    "return user.uri, " +
                            "user.email, " +
                            "user." + props.preferredLocales + "," +
                            "user." + props.salt + "," +
                            "user." + props.passwordHash;

    static enum props {
        username,
        email,
        preferredLocales,
        creationDate,
        updateTime,
        salt,
        passwordHash
    }

    @Inject
    protected QueryEngine queryEngine;

    @Override
    public void createUser(User user) {
        if (emailExists(user.email())) {
            throw new ExistingUserException(
                    user.email()
            );
        }
        if (usernameExists(user.username())) {
            throw new ExistingUserException(
                    user.username()
            );
        }
        queryEngine.query(
                "create (n:" + neo4jType + " {props})",
                wrap(map(
                        Neo4jFriendlyResource.props.uri.name(),
                        user.id(),
                        props.username.name(),
                        user.username(),
                        props.email.name(),
                        user.email(),
                        props.preferredLocales.name(),
                        user.preferredLocales(),
                        props.creationDate.name(),
                        new Date().getTime(),
                        props.updateTime.name(),
                        new Date().getTime(),
                        props.salt.name(),
                        user.salt(),
                        props.passwordHash.name(),
                        user.passwordHash()
                ))
        );
    }

    @Override
    public User findById(String id) throws NonExistingUserException {
        return null;
    }

    @Override
    public User findByUsername(String username) throws NonExistingUserException {
        return null;
    }

    @Override
    public User findByEmail(String email) throws NonExistingUserException {
        String query = "START user=node:node_auto_index('email:" + email + "') " +
                returnQueryPart;
        return userFromResult(
                queryEngine.query(
                        query,
                        wrap(map())
                )
        );
    }

    @Override
    public Boolean usernameExists(String username) {
        URI uri = new UserUris(username).baseUri();
        QueryResult<Map<String, Object>> result = queryEngine.query(
                "START n=node:node_auto_index('uri:" + uri + "') " +
                        "return n." + props.email,
                wrap(map())
        );
        return result.iterator().hasNext();
    }

    @Override
    public Boolean emailExists(String email) {
        QueryResult<Map<String, Object>> result = queryEngine.query(
                "START n=node:node_auto_index('email:" + email + "') " +
                        "return n." + props.email,
                wrap(map())
        );
        return result.iterator().hasNext();
    }

    private User userFromResult(QueryResult<Map<String, Object>> results) {
        Map<String, Object> result = results.iterator().next();
        URI userUri = URI.create(result.get("user.uri").toString());
        User user = User.withUsernameEmailAndLocales(
                UserUris.ownerUserNameFromUri(userUri),
                result.get("user.email").toString(),
                result.get("user.preferredLocales").toString()
        );
        setSalt(
                user,
                result.get("user.salt").toString()
        );
        setPasswordHash(
                user,
                result.get("user.passwordHash").toString()
        );
        return user;
    }

    protected void setSalt(User user, String salt) {
        try {
            Field field = User.class.getDeclaredField("salt");
            field.setAccessible(true);
            field.set(user, salt);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void setPasswordHash(User user, String passwordHash) {
        try {
            Field field = User.class.getDeclaredField("passwordHash");
            field.setAccessible(true);
            field.set(user, passwordHash);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
