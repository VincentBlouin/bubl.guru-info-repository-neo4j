/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.rest.graphdb.query.QueryEngine;
import org.triple_brain.module.model.User;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jModule;
import org.triple_brain.module.neo4j_graph_manipulator.graph.test.Neo4JGraphComponentTest;
import org.triple_brain.module.repository.user.ExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jRestApiUtils.map;

public class Neo4jUserRepositoryTest {

//    test

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
                "roger.lamothe@me.com",
                "[fr]"
        ).password("patate");
        userRepository.save(user);
        assertTrue(
                userRepository.usernameExists("roger_lamothe")
        );
    }

    @Test
    public void try_to_save_twice_a_user_with_same_email_is_not_possible() {
        User user_1 = User.withUsernameEmailAndLocales(
                "roger_lamothe",
                "some_email@example.org",
                "[fr]"
        );
        User user_2 = User.withUsernameEmailAndLocales(
                "roger_lamothe_2",
                "some_email@example.org",
                "[fr]"
        );
        userRepository.save(user_1);
        try {
            userRepository.save(user_2);
            fail();
        } catch (ExistingUserException e) {
            assertThat(
                    e.getMessage(),
                    is("A user already exist with username or email: some_email@example.org")
            );
        }
    }

    @Test
    public void try_to_save_twice_a_user_with_same_username_is_not_possible() {

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
}
