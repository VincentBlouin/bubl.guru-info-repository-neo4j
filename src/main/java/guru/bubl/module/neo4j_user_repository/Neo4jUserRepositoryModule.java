/*
 * Copyright Vincent Blouin under the GPL License version 3
 */

package guru.bubl.module.neo4j_user_repository;

import com.google.inject.AbstractModule;
import guru.bubl.module.repository.user.UserRepository;

public class Neo4jUserRepositoryModule extends AbstractModule{

    @Override
    protected void configure()
    {
        bind(UserRepository.class).to(Neo4jUserRepository.class);
    }
}
