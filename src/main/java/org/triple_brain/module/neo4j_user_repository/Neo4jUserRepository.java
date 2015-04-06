/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import org.neo4j.rest.graphdb.query.QueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;
import org.triple_brain.module.model.User;
import org.triple_brain.module.model.UserNameGenerator;
import org.triple_brain.module.model.UserUris;
import org.triple_brain.module.model.forget_password.ForgetPasswordTokenGenerator;
import org.triple_brain.module.model.forget_password.UserForgetPasswordToken;
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

    enum props {
        username,
        email,
        preferredLocales,
        creationDate,
        updateTime,
        salt,
        passwordHash,
        forgetPasswordToken,
        changePasswordExpirationDate
    }

    @Inject
    protected QueryEngine queryEngine;

    @Inject
    UserNameGenerator userNameGenerator;

    @Override
    public User createUser(User user) {
        if (emailExists(user.email())) {
            throw new ExistingUserException(
                    user.email()
            );
        }
        user.setUsername(userNameGenerator.generate());
        if (usernameExists(user.username())) {
            throw new ExistingUserException(
                    user.username()
            );
        }
        queryEngine.query(
                "create (user:resource {props})",
                wrap(map(
                        Neo4jFriendlyResource.props.type.name(),
                        neo4jType,
                        Neo4jFriendlyResource.props.uri.name(),
                        user.id(),
                        props.username.name(),
                        user.username(),
                        props.email.name(),
                        user.email(),
                        props.preferredLocales.name(),
                        user.getPreferredLocalesAsString(),
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
        return user;
    }

    @Override
    public User findByUsername(String username) throws NonExistingUserException {
        URI uri = new UserUris(username).baseUri();
        return userFromResult(
                queryEngine.query(
                        "START user=node:node_auto_index('uri:" + uri + "') " +
                                returnQueryPart,
                        wrap(map())
                ),
                username
        );
    }

    @Override
    public User findByEmail(String email) throws NonExistingUserException {
        if (email.trim().equals("")) {
            throw new NonExistingUserException("");
        }
        String query = "START user=node:node_auto_index('email:" + email + "') " +
                returnQueryPart;
        return userFromResult(
                queryEngine.query(
                        query,
                        wrap(map())
                ),
                email
        );
    }

    @Override
    public Boolean usernameExists(String username) {
        if (username.trim().equals("")) {
            return false;
        }
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
        if (email.trim().equals("")) {
            return false;
        }
        String query = "START n=node:node_auto_index('email:" + email + "') " +
                "RETURN count(n) as number";
        QueryResult<Map<String, Object>> result = queryEngine.query(
                query,
                map()
        );
        Integer numberOf = new Integer(
                result.iterator().next().get("number").toString()
        );
        return numberOf != 0;
    }

    @Override
    public void generateForgetPasswordToken(User user) {
        URI uri = new UserUris(user.username()).baseUri();
        queryEngine.query(
                "START user=node:node_auto_index('uri:" + uri + "') " +
                        "SET user." + props.forgetPasswordToken + "={" + props.forgetPasswordToken + "} " +
                        "SET user." + props.changePasswordExpirationDate + "={" + props.changePasswordExpirationDate + "}",
                map(
                        props.forgetPasswordToken.name(),
                        ForgetPasswordTokenGenerator.generateToken(),
                        props.changePasswordExpirationDate.name(),
                        ForgetPasswordTokenGenerator.generateTokenExpirationDate().getTime()
                )
        );
    }

    @Override
    public UserForgetPasswordToken getUserForgetPasswordToken(User user) {
        URI uri = new UserUris(user.username()).baseUri();
        QueryResult<Map<String, Object>> results = queryEngine.query(
                "START user=node:node_auto_index('uri:" + uri + "') " +
                        "RETURN user." + props.forgetPasswordToken + ", " +
                        "user." + props.changePasswordExpirationDate,
                map()
        );
        Map<String, Object> result = results.iterator().next();
        if(result.get("user." + props.forgetPasswordToken) == null){
            return UserForgetPasswordToken.empty();
        }
        String token = result.get("user." + props.forgetPasswordToken).toString();
        Date changePasswordExpirationDate = new Date(
                Long.valueOf(
                        result.get("user." + props.changePasswordExpirationDate).toString()
                )
        );
        return UserForgetPasswordToken.withTokenAndExpirationDate(
                token,
                changePasswordExpirationDate
        );
    }

    @Override
    public void changePassword(User user) {
        URI uri = new UserUris(user.username()).baseUri();
        queryEngine.query(
                "START user=node:node_auto_index('uri:" + uri + "') " +
                        "SET user." + props.salt + "={" + props.salt + "} " +
                        "SET user." + props.passwordHash + "={" + props.passwordHash + "}",
                map(
                        props.salt.name(),
                        user.salt(),
                        props.passwordHash.name(),
                        user.passwordHash()
                )
        );
    }

    private User userFromResult(QueryResult<Map<String, Object>> results, String identifier) {
        if (!results.iterator().hasNext()) {
            throw new NonExistingUserException(identifier);
        }
        Map<String, Object> result = results.iterator().next();
        URI userUri = URI.create(result.get("user.uri").toString());
        User user = User.withEmailAndUsername(
                result.get("user." + props.email).toString(),
                UserUris.ownerUserNameFromUri(userUri)
        );
        user.setPreferredLocales(
                result.get("user." + props.preferredLocales).toString()
        );
        setSalt(
                user,
                result.get("user." + props.salt).toString()
        );
        setPasswordHash(
                user,
                result.get("user." + props.passwordHash).toString()
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
