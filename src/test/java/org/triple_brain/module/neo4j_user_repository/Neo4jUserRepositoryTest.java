/*
 * Copyright Vincent Blouin under the Mozilla Public License 1.1
 */

package org.triple_brain.module.neo4j_user_repository;

import com.google.inject.Inject;
import org.junit.Test;
import org.triple_brain.module.model.User;
import org.triple_brain.module.repository.user.UserRepository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Neo4jUserRepositoryTest {

    @Inject
    UserRepository userRepository;

    @Test
    public void can_save_user(){
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
    public void try_to_save_twice_a_user_with_same_email_is_not_possible(){

    }

    @Test
    public void try_to_save_twice_a_user_with_same_username_is_not_possible(){

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
}
