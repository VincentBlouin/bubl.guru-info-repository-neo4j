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
import org.triple_brain.module.model.ModelModule;
import org.triple_brain.module.model.User;
import org.triple_brain.module.model.UserNameGenerator;
import org.triple_brain.module.model.forget_password.UserForgetPasswordToken;
import org.triple_brain.module.neo4j_graph_manipulator.graph.Neo4jModule;
import org.triple_brain.module.neo4j_graph_manipulator.graph.test.Neo4JGraphComponentTest;
import org.triple_brain.module.repository.user.ExistingUserException;
import org.triple_brain.module.repository.user.NonExistingUserException;
import org.triple_brain.module.repository.user.UserRepository;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class Neo4jUserRepositoryTest {

    protected static Injector injector;

    @Inject
    UserRepository userRepository;

    @Inject
    Neo4JGraphComponentTest graphComponentTest;

    @Inject
    UserNameGenerator userNameGenerator;

    static GraphDatabaseService graphDatabaseService;


    static QueryEngine queryEngine;

    @BeforeClass
    public static void realBeforeClass() {
        Neo4jModule.clearDb();
        injector = Guice.createInjector(
                Neo4jModule.forTestingUsingEmbedded(),
                new Neo4jUserRepositoryModule(),
                new ModelModule()
        );
        graphDatabaseService = injector.getInstance(GraphDatabaseService.class);
        queryEngine = injector.getInstance(QueryEngine.class);
    }

    @Before
    public final void before() {
        injector.injectMembers(this);
    }

    @After
    public final void after() {
        graphComponentTest.removeWholeGraph();
        userNameGenerator.reset();
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
        User user = User.withEmail(
                "some_email@example.org"
        ).password("password");
        userRepository.createUser(user);
        assertTrue(
                userRepository.emailExists("some_email@example.org")
        );
    }

    @Test
    public void try_to_save_twice_a_user_with_same_email_is_not_possible() {
        String email = randomEmail();
        User user1 = User.withEmail(
                email
        ).password("password");
        User user2 = User.withEmail(
                email
        ).password("password");
        userRepository.createUser(user1);
        try {
            userRepository.createUser(user2);
            fail();
        } catch (ExistingUserException e) {
            assertThat(
                    e.getMessage(),
                    is("A user already exist with username or email: " + email)
            );
        }
    }

    @Test
    public void creating_a_user_generates_a_user_name(){
        User user = createAUser();
        assertNull(user.username());
        user = userRepository.createUser(
                createAUser()
        );
        assertNotNull(user.username());
    }

    @Test
    public void try_to_save_twice_a_user_with_same_username_is_not_possible() {
        userNameGenerator.setOverride(new UserNameGenerator() {
            @Override
            public String generate() {
                return "same";
            }

            @Override
            public void setOverride(UserNameGenerator userNameGenerator) {

            }

            @Override
            public void reset() {

            }
        });
        User user1 = User.withEmail(
                randomEmail()
        ).password("password");
        String user2Email = randomEmail();
        User user2 = User.withEmail(
                user2Email
        ).password("password");
        userRepository.createUser(user1);
        try {
            userRepository.createUser(user2);
            fail();
        } catch (ExistingUserException e) {
            assertThat(e.getMessage(), Matchers.is("A user already exist with username or email: " + "same"));
        }
    }

    @Test
    public void user_fields_are_well_saved() {
        String email = randomEmail();
        User user = User.withEmail(
                email
        ).password("secret");
        userRepository.createUser(user);
        User loadedUser = userRepository.findByEmail(email);
        assertThat(
                loadedUser.id(),
                is(user.id())
        );
        assertThat(
                loadedUser.email(),
                is(user.email())
        );
        assertTrue(
                loadedUser.hasPassword("secret")
        );
    }

    @Test
    public void can_find_user_by_email() {
        User user = createAUser();
        userRepository.createUser(user);
        assertThat(
                userRepository.findByEmail(user.email()),
                is(user)
        );
    }

    @Test
    public void try_to_find_none_existing_user_by_email_throw_and_Exception() {
        try {
            userRepository.findByEmail("non_existing@example.org");
            fail();
        } catch (NonExistingUserException e) {
            assertThat(e.getMessage(), Matchers.is("User not found: non_existing@example.org"));
        }

        try {
            userRepository.findByEmail("");
            fail();
        } catch (NonExistingUserException e) {
            assertThat(e.getMessage(), Matchers.is("User not found: "));
        }
    }

    @Test
    public void can_find_user_by_user_name() {
        User user = createAUser();
        user = userRepository.createUser(user);
        assertThat(
                userRepository.findByUsername(user.username()),
                is(user)
        );
    }

    @Test
    public void try_to_find_non_existing_user_by_username_throws_an_exception() {
        try {
            userRepository.findByUsername("non_existing_user_name");
            fail();
        } catch (NonExistingUserException e) {
            assertThat(e.getMessage(), Matchers.is("User not found: non_existing_user_name"));
        }
    }

    @Test
    public void resetting_password_sets_a_token(){
        User user = userRepository.createUser(
                createAUser()
        );
        UserForgetPasswordToken userForgetPasswordToken = userRepository.getUserForgetPasswordToken(
                user
        );
        assertTrue(
                userForgetPasswordToken.isEmpty()
        );
        userRepository.generateForgetPasswordToken(user);
        userForgetPasswordToken = userRepository.getUserForgetPasswordToken(
                user
        );
        assertFalse(
                userForgetPasswordToken.isEmpty()
        );
    }

    private String randomEmail() {
        return UUID.randomUUID().toString() + "@me.com";
    }

    private User createAUser() {
        return User.withEmail(
                randomEmail()
        ).password("password");
    }
}
