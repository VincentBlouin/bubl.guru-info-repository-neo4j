/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import com.google.inject.AbstractModule;
import org.triple_brain.module.repository.user.UserRepository;

public class Neo4jUserRepositoryModule extends AbstractModule{

    @Override
    protected void configure() {
        bind(UserRepository.class).to(Neo4jUserRepository.class);
    }
}