/*
 * Copyright Vincent Blouin under the GPL License version 3
 */

package guru.bubl.module.neo4j_user_repository;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import guru.bubl.module.common_utils.NoEx;
import guru.bubl.module.model.User;
import guru.bubl.module.model.UserUris;
import guru.bubl.module.model.friend.FriendManager;
import guru.bubl.module.model.friend.FriendPojo;
import guru.bubl.module.model.friend.FriendStatus;
import guru.bubl.module.repository.user.UserRepository;
import org.apache.commons.lang.RandomStringUtils;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class FriendManagerNeo4j implements FriendManager {

    @Inject
    Connection connection;

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
        if(friendStatus == FriendStatus.waitingForYourAnswer){
            this.confirm(newFriend);
            return null;
        }
        if(friendStatus != FriendStatus.none){
            return null;
        }
        return NoEx.wrap(() -> {
            String query = String.format("START user=node:node_auto_index('uri:%s') " +
                            "with user " +
                            "START otherUser=node:node_auto_index('uri:%s') " +
                            "CREATE UNIQUE (user)-[friendship:friend]->(otherUser) " +
                            "SET friendship.status='%s', " +
                            "friendship.confirmToken='%s'",
                    user.id(),
                    newFriend.id(),
                    FriendStatus.waiting,
                    confirmToken
            );
            connection.createStatement().execute(query);
            return confirmToken;
        }).get();
    }

    @Override
    public FriendManager confirm(User newFriend) {
        return NoEx.wrap(() -> {
            String query = String.format("START user=node:node_auto_index('uri:%s') " +
                            "with user " +
                            "START newFriend=node:node_auto_index('uri:%s') " +
                            "MATCH user<-[friendship:friend]-newFriend " +
                            "SET friendship.status='%s' ",
                    user.id(),
                    newFriend.id(),
                    FriendStatus.confirmed
            );
            connection.createStatement().executeQuery(query);
            return this;
        }).get();
    }

    @Override
    public Boolean confirmWithToken(User newFriend, String confirmToken) {
        return NoEx.wrap(() -> {
            String query = String.format("START user=node:node_auto_index('uri:%s') " +
                            "with user " +
                            "START newFriend=node:node_auto_index('uri:%s') " +
                            "MATCH user<-[friendship:friend]-newFriend " +
                            "WHERE friendship.confirmToken='%s' " +
                            "SET friendship.status='%s' " +
                            "RETURN friendship.status ",
                    user.id(),
                    newFriend.id(),
                    confirmToken,
                    FriendStatus.confirmed
            );
            ResultSet rs = connection.createStatement().executeQuery(query);
            return rs.next();
        }).get();
    }

    @Override
    public Map<URI, FriendPojo> list() {
        Map<URI, FriendPojo> friends = new HashMap<>();
        return NoEx.wrap(() -> {
            String query = String.format("START user=node:node_auto_index('uri:%s') " +
                            "MATCH user-[friendship:friend]-friend " +
                            "RETURN friend.uri as uri, " +
                            "friendship.status as status",
                    user.id()
            );
            ResultSet rs = connection.createStatement().executeQuery(query);
            while (rs.next()) {
                URI uri = URI.create(rs.getString("uri"));
                friends.put(
                        uri,
                        new FriendPojo(
                                UserUris.ownerUserNameFromUri(
                                        uri
                                ),
                                FriendStatus.valueOf(rs.getString("status"))
                        )
                );
            }
            return friends;
        }).get();
    }

    @Override
    public FriendStatus getStatusWithUser(User otherUser) {
        return NoEx.wrap(() -> {
            String query = String.format("START user=node:node_auto_index('uri:%s') " +
                            "with user " +
                            "START friend=node:node_auto_index('uri:%s') " +
                            "OPTIONAL MATCH (user)-[friendRequest:friend]->(friend) " +
                            "OPTIONAL MATCH (friend)-[friendInvitation:friend]->(user) " +
                            "RETURN friendRequest.status as friendRequestStatus, " +
                            "friendInvitation.status as friendInvitationStatus",
                    user.id(),
                    otherUser.id()
            );
            ResultSet rs = connection.createStatement().executeQuery(query);
            if (!rs.next()) {
                return FriendStatus.none;
            }
            Boolean isRequestUser = rs.getObject("friendRequestStatus") != null;

            if (!isRequestUser && rs.getObject("friendInvitationStatus") == null) {
                return FriendStatus.none;
            }

            FriendStatus friendStatus = isRequestUser ?
                    FriendStatus.valueOf(
                            rs.getString("friendRequestStatus")
                    ) :
                    FriendStatus.valueOf(
                            rs.getString("friendInvitationStatus")
                    );

            if (friendStatus == FriendStatus.waiting && !isRequestUser) {
                return FriendStatus.waitingForYourAnswer;
            }
            return friendStatus;
        }).get();
    }
}
