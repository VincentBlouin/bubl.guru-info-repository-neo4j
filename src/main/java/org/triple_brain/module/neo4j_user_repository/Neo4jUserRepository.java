/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import org.triple_brain.module.model.User;
import org.triple_brain.module.repository.user.NonExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

public class Neo4jUserRepository implements UserRepository {

    @Override
    public void save(User user) {

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
        return null;
    }

    @Override
    public Boolean emailExists(String email) {
        return null;
    }
}
