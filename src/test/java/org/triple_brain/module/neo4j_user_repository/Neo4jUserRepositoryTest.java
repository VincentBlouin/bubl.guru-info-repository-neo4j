/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.hamcrest.Matchers;
import org.junit.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.rest.graphdb.query.QueryEngine;
import org.triple_brain.module.model.User;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jFriendlyResource;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jModule;
import org.triple_brain.module.neo4j_graph_manipulator.graph.test.Neo4JGraphComponentTest;
import org.triple_brain.module.repository.user.ExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.map;

public class Neo4jUserRepositoryTest {

    protected static Injector injector;

    @Inject
    UserRepository userRepository;

    @Inject
    Neo4JGraphComponentTest graphComponentTest;

    static GraphDatabaseService graphDatabaseService;


    static QueryEngine queryEngine;

    @BeforeClass
    public static void realBeforeClass() {
        Neo4jModule.clearDb();
        injector = Guice.createInjector(
                Neo4jModule.forTestingUsingEmbedded(),
                new Neo4jUserRepositoryModule()
        );
        graphDatabaseService = injector.getInstance(GraphDatabaseService.class);
        queryEngine = injector.getInstance(QueryEngine.class);
        createUniqueEmailConstraint();
        createUniqueUriConstraint();
    }

    @Before
    public final void before(){
        injector.injectMembers(this);
    }

    @After
    public final void after(){
        graphComponentTest.removeWholeGraph();
    }

    @AfterClass
    public static void afterClass() {
        graphDatabaseService.shutdown();
        Neo4jModule.clearDb();
    }

    @Test
    public void can_save_user() {
        assertFalse(
                userRepository.usernameExists(
                        "roger_lamothe"
                )
        );
        User user = User.withUsernameEmailAndLocales(
                "roger_lamothe",
                randomEmail(),
                "[fr]"
        ).password("patate");
        userRepository.save(user);
        assertTrue(
                userRepository.usernameExists("roger_lamothe")
        );
    }

    @Test
    public void try_to_save_twice_a_user_with_same_email_is_not_possible() {
        String email = randomEmail();
        User user_1 = User.withUsernameEmailAndLocales(
                randomUserName(),
                email,
                "[fr]"
        );
        User user_2 = User.withUsernameEmailAndLocales(
                randomUserName(),
                email,
                "[fr]"
        );
        userRepository.save(user_1);
        try {
            userRepository.save(user_2);
            fail();
        } catch (ExistingUserException e) {
            assertThat(
                    e.getMessage(),
                    is("A user already exist with username or email: " + email)
            );
        }
    }

    @Test
    public void try_to_save_twice_a_user_with_same_username_is_not_possible() {
        User user_1 = User.withUsernameEmailAndLocales(
                "a_user_name",
                randomEmail(),
                "[fr]"
        );
        String user2Email = randomEmail();
        User user_2 = User.withUsernameEmailAndLocales(
                "a_user_name",
                user2Email,
                "[fr]"
        );
        userRepository.save(user_1);
        try {
            userRepository.save(user_2);
            fail();
        } catch (ExistingUserException e) {
            assertThat(e.getMessage(), Matchers.is("A user already exist with username or email: " + user2Email));
        }
    }

    @Test
    public void user_fields_are_well_saved() {

    }

    @Test
    public void can_find_user_by_email() {

    }

    @Test
    public void try_to_find_none_existing_user_by_email_throw_and_Exception() {

    }

    @Test
    public void can_find_user_by_user_name() {

    }

    @Test
    public void try_to_find_none_existing_user_by_username_throw_and_Exception() {

    }

    private static void createUniqueEmailConstraint(){
        String query = "CREATE CONSTRAINT ON (user:" +
                Neo4jUserRepository.neo4jType +
                ") ASSERT user." + Neo4jUserRepository.props.email + " IS UNIQUE";
        queryEngine.query(
                query,
                map()
        );
    }

    private static void createUniqueUriConstraint(){
        String query = "CREATE CONSTRAINT ON (user:" +
                Neo4jUserRepository.neo4jType +
                ") ASSERT user." + Neo4jFriendlyResource.props.uri + " IS UNIQUE";
        queryEngine.query(
                query,
                map()
        );
    }

    private String randomUserName(){
        return UUID.randomUUID().toString();
    }

    private String randomEmail(){
        return UUID.randomUUID().toString() + "@me.com";
    }
}
