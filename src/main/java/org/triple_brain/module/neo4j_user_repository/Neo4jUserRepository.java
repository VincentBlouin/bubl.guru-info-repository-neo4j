/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import org.neo4j.rest.graphdb.query.QueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;
import org.triple_brain.module.model.User;
import org.triple_brain.module.model.UserUris;
import org.triple_brain.module.model.graph.GraphElementType;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jFriendlyResource;
import org.triple_brain.module.repository.user.NonExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;
import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.map;
import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.wrap;

public class Neo4jUserRepository implements UserRepository {

    enum props{
        username
    }

    @Inject
    protected QueryEngine queryEngine;

    @Override
    public void save(User user) {
        queryEngine.query(
                "create (n:" + GraphElementType.vertex + " {props})",
                wrap(map(
                        Neo4jFriendlyResource.props.uri.name(),
                        new UserUris(user).baseUri().toString(),
                        props.username.name(),
                        user.username()
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
        return null;
    }

    @Override
    public Boolean usernameExists(String username) {
        URI uri = new UserUris(username).baseUri();
        QueryResult<Map<String, Object>> result = queryEngine.query(
                "START n=node:node_auto_index('uri:" + uri + "') " +
                        "return n." + props.username,
                wrap(map())
        );
        return result.iterator().hasNext();
    }

    @Override
    public Boolean emailExists(String email) {
        return null;
    }
}
