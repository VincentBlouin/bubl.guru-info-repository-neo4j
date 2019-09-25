/*
 * Copyright Vincent Blouin under the GPL License version 3
 */

package guru.bubl.module.neo4j_user_repository;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import guru.bubl.module.model.friend.FriendManager;
import guru.bubl.module.model.friend.FriendManagerFactory;
import guru.bubl.module.neo4j_graph_manipulator.graph.Neo4jModule;
import guru.bubl.module.repository.user.UserRepository;

public class Neo4jUserRepositoryModule extends AbstractModule{

    @Override
    protected void configure()
    {
        bind(UserRepository.class).to(UserRepositoryNeo4j.class);
        FactoryModuleBuilder factoryModuleBuilder = new FactoryModuleBuilder();
        install(factoryModuleBuilder
                .implement(FriendManager.class, FriendManagerNeo4j.class)
                .build(FriendManagerFactory.class));

    }
}
