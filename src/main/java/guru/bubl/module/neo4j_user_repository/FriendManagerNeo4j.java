/*
 * Copyright Vincent Blouin under the GPL License version 3
 */

package guru.bubl.module.neo4j_user_repository;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import guru.bubl.module.model.User;
import guru.bubl.module.model.UserUris;
import guru.bubl.module.model.friend.FriendManager;
import guru.bubl.module.model.friend.FriendPojo;
import guru.bubl.module.model.friend.FriendStatus;
import org.apache.commons.lang.RandomStringUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.neo4j.driver.Values.parameters;

public class FriendManagerNeo4j implements FriendManager {

    @Inject
    Driver driver;

    private User user;

    @AssistedInject
    protected FriendManagerNeo4j(
            @Assisted User user
    ) {
        this.user = user;
    }


    @Override
    public String add(User newFriend) {
        String confirmToken = RandomStringUtils.randomAlphanumeric(
                30
        );
        FriendStatus friendStatus = this.getStatusWithUser(newFriend);
        if (friendStatus == FriendStatus.waitingForYourAnswer) {
            this.confirm(newFriend);
            return null;
        }
        if (friendStatus != FriendStatus.none) {
            return null;
        }
        try (Session session = driver.session()) {
            session.run(
                    "MATCH(user:Resource{uri:$uri}), (otherUser:Resource{uri:$friendUri}) MERGE (user)-[friendship:friend]->(otherUser) SET friendship.status=$status, friendship.confirmToken=$confirmToken",
                    parameters(
                            "uri", user.id(),
                            "friendUri", newFriend.id(),
                            "status", FriendStatus.waiting.name(),
                            "confirmToken", confirmToken
                    )
            );
            return confirmToken;
        }
    }

    @Override
    public FriendManager confirm(User newFriend) {
        try (Session session = driver.session()) {
            session.run(
                    "MATCH(user:Resource{uri:$uri}) WITH user " +
                            "MATCH(newFriend:Resource{uri:$friendUri}), " +
                            "(user)<-[friendship:friend]-(newFriend) " +
                            "SET friendship.status=$status",
                    parameters(
                            "uri", user.id(),
                            "friendUri", newFriend.id(),
                            "status", FriendStatus.confirmed.name()
                    )
            );
            return this;
        }
    }

    @Override
    public Boolean confirmWithToken(User newFriend, String confirmToken) {
        try (Session session = driver.session()) {
            return session.run(
                    "MATCH(user:Resource{uri:$uri}) WITH user " +
                            "MATCH(newFriend:Resource{uri:$friendUri}), " +
                            "(user)<-[friendship:friend]-(newFriend) " +
                            "WHERE friendship.confirmToken=$confirmToken " +
                            "SET friendship.status=$status " +
                            "RETURN friendship.status ",
                    parameters(
                            "uri", user.id(),
                            "friendUri", newFriend.id(),
                            "confirmToken", confirmToken,
                            "status", FriendStatus.confirmed.name()
                    )
            ).hasNext();
        }
    }

    @Override
    public Map<URI, FriendPojo> list() {
        Map<URI, FriendPojo> friends = new HashMap<>();
        String query = "MATCH(user:Resource{uri:$uri}), " +
                "(user)-[friendship:friend]-(friend) " +
                "RETURN friend.uri as uri, friendship.status as status";
        try (Session session = driver.session()) {
            Result sr = session.run(
                    query,
                    parameters(
                            "uri", user.id()
                    )
            );
            while (sr.hasNext()) {
                Record record = sr.next();
                URI uri = URI.create(record.get("uri").asString());
                friends.put(
                        uri,
                        new FriendPojo(
                                UserUris.ownerUserNameFromUri(
                                        uri
                                ),
                                FriendStatus.valueOf(record.get("status").asString())
                        )
                );
            }
            return friends;
        }
    }

    @Override
    public FriendStatus getStatusWithUser(User otherUser) {
        String query = "MATCH(user:Resource{uri:$uri}) " +
                "WITH user " +
                "MATCH (friend:Resource{uri:$friendUri}) " +
                "OPTIONAL MATCH (user)-[friendRequest:friend]->(friend) " +
                "OPTIONAL MATCH (friend)-[friendInvitation:friend]->(user) " +
                "RETURN friendRequest.status as friendRequestStatus, " +
                "friendInvitation.status as friendInvitationStatus";
        try (Session session = driver.session()) {
            Result sr = session.run(
                    query,
                    parameters(
                            "uri", user.id(),
                            "friendUri", otherUser.id()
                    )
            );
            if (!sr.hasNext()) {
                return FriendStatus.none;
            }
            Record record = sr.single();
            Boolean isRequestUser = record.get("friendRequestStatus").asObject() != null;
            if (!isRequestUser && record.get("friendInvitationStatus").asObject() == null) {
                return FriendStatus.none;
            }
            FriendStatus friendStatus = isRequestUser ?
                    FriendStatus.valueOf(
                            record.get("friendRequestStatus").asString()
                    ) :
                    FriendStatus.valueOf(
                            record.get("friendInvitationStatus").asString()
                    );

            if (friendStatus == FriendStatus.waiting && !isRequestUser) {
                return FriendStatus.waitingForYourAnswer;
            }
            return friendStatus;
        }
    }
}
