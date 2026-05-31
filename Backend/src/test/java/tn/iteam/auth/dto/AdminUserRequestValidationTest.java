package tn.iteam.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createRequestShouldRejectUsernameWithSpaces() {
        AdminCreateUserRequest request = validCreateRequest();
        request.setUsername("admin user");

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v ->
                assertThat(v.getMessage()).isEqualTo("Username must not contain spaces"));
    }

    @Test
    void createRequestShouldRejectShortUsername() {
        AdminCreateUserRequest request = validCreateRequest();
        request.setUsername("adm123");

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v ->
                assertThat(v.getMessage()).isEqualTo("Username must be at least 8 characters"));
    }

    @Test
    void createRequestShouldRejectPasswordWithoutSpecialCharacter() {
        AdminCreateUserRequest request = validCreateRequest();
        request.setPassword("Password9");

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v ->
                assertThat(v.getMessage()).isEqualTo("Password must include at least one special character"));
    }

    @Test
    void createRequestShouldAcceptValidUsernameAndPassword() {
        AdminCreateUserRequest request = validCreateRequest();

        Set<ConstraintViolation<AdminCreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void updateRequestShouldRejectPasswordWithoutSpecialCharacterWhenProvided() {
        AdminUpdateUserRequest request = validUpdateRequest();
        request.setPassword("Password9");

        Set<ConstraintViolation<AdminUpdateUserRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v ->
                assertThat(v.getMessage()).isEqualTo("Password must include at least one special character"));
    }

    @Test
    void updateRequestShouldAcceptMissingPasswordAndValidUsername() {
        AdminUpdateUserRequest request = validUpdateRequest();
        request.setPassword(null);

        Set<ConstraintViolation<AdminUpdateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    private static AdminCreateUserRequest validCreateRequest() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("monitoradm");
        request.setEmail("admin@company.tn");
        request.setPassword("Password@9");
        request.setRole("VIEWER");
        request.setEnabled(true);
        return request;
    }

    private static AdminUpdateUserRequest validUpdateRequest() {
        AdminUpdateUserRequest request = new AdminUpdateUserRequest();
        request.setUsername("monitoradm");
        request.setEmail("admin@company.tn");
        request.setPassword("Password@9");
        request.setRole("VIEWER");
        request.setEnabled(true);
        return request;
    }
}
