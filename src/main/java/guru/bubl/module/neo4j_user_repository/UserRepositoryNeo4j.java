/*
 * Copyright Vincent Blouin under the GPL License version 3
 */

package guru.bubl.module.neo4j_user_repository;

import guru.bubl.module.model.User;
import guru.bubl.module.model.UserUris;
import guru.bubl.module.model.forgot_password.UserForgotPasswordToken;
import guru.bubl.module.neo4j_graph_manipulator.graph.FriendlyResourceNeo4j;
import guru.bubl.module.repository.user.ExistingUserException;
import guru.bubl.module.repository.user.NonExistingUserException;
import guru.bubl.module.repository.user.UserRepository;
import org.apache.commons.lang.StringUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static guru.bubl.module.neo4j_graph_manipulator.graph.RestApiUtilsNeo4j.map;
import static org.neo4j.driver.Values.parameters;

public class UserRepositoryNeo4j implements UserRepository {

    public static String neo4jType = "user",
            returnQueryPart =
                    "return user.uri, " +
                            "user.email, " +
                            "user.consultNotificationDate," +
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
    protected Driver driver;

    @Override
    public User createUser(User user) {
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
        try (Session session = driver.session()) {
            session.run(
                    "CREATE(:Resource:User $user)",
                    parameters(
                            "user",
                            parameters(
                                    FriendlyResourceNeo4j.props.uri.name(),
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
                            )
                    )
            );
            return user;
        }
    }

    @Override
    public User findByUsername(String username) throws NonExistingUserException {
        URI uri = new UserUris(username).baseUri();
        try (Session session = driver.session()) {
            Result sr = session.run(
                    String.format(
                            "MATCH(user:Resource{uri:$uri}) %s",
                            returnQueryPart
                    ),
                    parameters(
                            "uri", uri.toString()
                    )
            );
            return userFromResult(
                    sr,
                    username
            );
        }
    }

    @Override
    public User findByEmail(String email) throws NonExistingUserException {
        if (email.trim().equals("")) {
            throw new NonExistingUserException("");
        }
        try (Session session = driver.session()) {
            Result sr = session.run(
                    String.format(
                            "MATCH(user:User{email:$email}) %s",
                            returnQueryPart
                    ),
                    parameters(
                            "email", email
                    )
            );
            return userFromResult(
                    sr,
                    email
            );
        }
    }

    @Override
    public Boolean usernameExists(String username) {
        if (username.trim().equals("")) {
            return false;
        }
        URI uri = new UserUris(username).baseUri();
        try (Session session = driver.session()) {
            return session.run(
                    "MATCH(n:Resource{uri:$uri}) RETURN n.email",
                    parameters(
                            "uri", uri.toString()
                    )
            ).hasNext();
        }
    }

    @Override
    public Boolean emailExists(String email) {
        if (email.trim().equals("")) {
            return false;
        }
        try (Session session = driver.session()) {
            Record record = session.run(
                    "MATCH(n:User{email:$email}) RETURN count(n) as number",
                    parameters(
                            "email", email
                    )
            ).single();
            Integer numberOf = record.get("number").asInt();
            return numberOf != 0;
        }
    }

    @Override
    public void generateForgetPasswordToken(User user, UserForgotPasswordToken userForgotPasswordToken) {
        URI uri = new UserUris(user.username()).baseUri();
        String query = String.format(
                "MATCH(user:Resource{uri:$uri}) SET user.%s=$token SET user.%s=$expirationDate",
                props.forgetPasswordToken,
                props.changePasswordExpirationDate
        );
        try (Session session = driver.session()) {
            session.run(
                    query,
                    parameters(
                            "uri", uri.toString(),
                            "token", userForgotPasswordToken.getToken(),
                            "expirationDate", userForgotPasswordToken.getResetPasswordExpirationDate().getTime()
                    )
            );
        }
    }

    @Override
    public UserForgotPasswordToken getUserForgetPasswordToken(User user) {
        URI uri = new UserUris(user.username()).baseUri();
        String query = String.format(
                "MATCH (user:Resource{uri:$uri}) RETURN user.%s, user.%s",
                props.forgetPasswordToken,
                props.changePasswordExpirationDate
        );
        try (Session session = driver.session()) {
            Record record = session.run(
                    query,
                    parameters(
                            "uri", uri.toString()
                    )
            ).single();
            String forgetPasswordToken = record.get(
                    "user." + props.forgetPasswordToken
            ).asString();
            if (forgetPasswordToken.equals("null")) {
                return UserForgotPasswordToken.empty();
            }

            Date changePasswordExpirationDate = record.get(
                    "user." + props.changePasswordExpirationDate
            ).asObject() == null ? null : new Date(
                    record.get(
                            "user." + props.changePasswordExpirationDate
                    ).asLong()
            );
            return UserForgotPasswordToken.withTokenAndExpirationDate(
                    forgetPasswordToken,
                    changePasswordExpirationDate
            );
        }
    }

    @Override
    public void changePassword(User user) {
        URI uri = new UserUris(user.username()).baseUri();
        try (Session session = driver.session()) {
            session.run(
                    "MATCH (user:Resource{uri:$uri}) SET user.salt=$salt, user.passwordHash=$passwordHash, user.forgetPasswordToken=$token, user.changePasswordExpirationDate=$expirationDate",
                    parameters(
                            "uri", uri.toString(),
                            "salt", user.salt(),
                            "passwordHash", user.passwordHash(),
                            "token", null,
                            "expirationDate", null
                    )
            );
        }
    }

    @Override
    public void updatePreferredLocales(User user) {
        try (Session session = driver.session()) {
            session.run(
                    "MATCH(user:Resource{uri:$uri}) SET user.preferredLocales=$locale",
                    parameters(
                            "uri", user.id(),
                            "locale", user.getPreferredLocalesAsString()
                    )
            );
        }
    }

    @Override
    public Date updateConsultNotificationDate(User user) {
        try (Session session = driver.session()) {
            Record record = session.run(
                    "MATCH(user:Resource{uri:$uri}) SET user.consultNotificationDate=timestamp() RETURN user.consultNotificationDate",
                    parameters(
                            "uri", user.id()
                    )
            ).single();
            return new Date(record.get("user.consultNotificationDate").asLong());
        }
    }

    public List<User> searchUsers(String searchTerm, User user) {
        List<User> users = new ArrayList<>();
        try (Session session = driver.session()) {
            Result sr = session.run(
                    "CALL db.index.fulltext.queryNodes('username', $username) YIELD node RETURN node.uri as uri",
                    parameters(
                            "username",
                            searchTerm + "*"
                    )
            );
            while (sr.hasNext()) {
                Record record = sr.next();
                users.add(
                        User.withUsername(
                                UserUris.ownerUserNameFromUri(
                                        URI.create(record.get("uri").asString())
                                )
                        )
                );
            }
            return users;
        }
    }

    private User userFromResult(Result rs, String identifier) {
        if (!rs.hasNext()) {
            throw new NonExistingUserException(identifier);
        }
        Record record = rs.single();
        URI userUri = URI.create(
                record.get("user.uri").asString()
        );
        User user = User.withEmailAndUsername(
                record.get(
                        "user." + props.email
                ).asString(),
                UserUris.ownerUserNameFromUri(userUri)
        );
        user.setPreferredLocales(
                record.get(
                        "user." + props.preferredLocales
                ).asString()
        );
        Object date = record.get("user.consultNotificationDate").asObject();
        if (date != null) {
            user.setConsultNotificationDate(
                    new Date(
                            (Long) date
                    )
            );
        }
        setSalt(
                user,
                record.get(
                        "user." + props.salt
                ).asString()
        );
        setPasswordHash(
                user,
                record.get(
                        "user." + props.passwordHash
                ).asString()
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
