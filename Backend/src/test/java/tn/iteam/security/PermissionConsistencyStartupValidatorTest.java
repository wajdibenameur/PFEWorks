package tn.iteam.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class PermissionConsistencyStartupValidatorTest {

    @Test
    void validatorPassesWithCurrentRolePermissionMatrix() {
        PermissionConsistencyStartupValidator validator = new PermissionConsistencyStartupValidator();
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
