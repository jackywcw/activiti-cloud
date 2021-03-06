/*
 * Copyright 2017-2020 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.cloud.services.modeling.rest.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.webAppContextSetup;
import static org.activiti.cloud.services.common.util.FileUtils.resourceAsByteArray;
import static org.activiti.cloud.services.modeling.asserts.AssertResponse.assertThatResponse;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.cloud.modeling.api.JsonModelType;
import org.activiti.cloud.modeling.api.Model;
import org.activiti.cloud.modeling.api.ModelContentValidator;
import org.activiti.cloud.modeling.api.ModelExtensionsValidator;
import org.activiti.cloud.modeling.api.ValidationContext;
import org.activiti.cloud.modeling.core.error.SemanticModelValidationException;
import org.activiti.cloud.modeling.repository.ModelRepository;
import org.activiti.cloud.modeling.repository.ProjectRepository;
import org.activiti.cloud.services.modeling.config.ModelingRestApplication;
import org.activiti.cloud.services.modeling.entity.ModelEntity;
import org.activiti.cloud.services.modeling.security.WithMockModelerUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.Collections;

/**
 * Integration tests for models rest api dealing with JSON models
 */
@ActiveProfiles(profiles = { "test", "generic" })
@SpringBootTest(classes = ModelingRestApplication.class)
@WebAppConfiguration
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@WithMockModelerUser
public class GenericJsonModelTypeValidationControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ModelRepository modelRepository;

    @SpyBean(name = "genericJsonExtensionsValidator")
    ModelExtensionsValidator genericJsonExtensionsValidator;

    @SpyBean(name = "genericJsonContentValidator")
    ModelContentValidator genericJsonContentValidator;

    @Autowired
    JsonModelType genericJsonModelType;

    private static final String GENERIC_MODEL_NAME = "simple-model";

    private Model genericJsonModel;

    @BeforeEach
    public void setUp() {
        webAppContextSetup(context);
        genericJsonModel = modelRepository.createModel(new ModelEntity(GENERIC_MODEL_NAME,
                                                                       genericJsonModelType.getName()));
    }

    private void validateInvalidContent() {
        SemanticModelValidationException exception = new SemanticModelValidationException(Collections
                .singletonList(genericJsonContentValidator.createModelValidationError("Content invalid",
                                                                                      "The content is invalid!!")));

        doThrow(exception).when(genericJsonContentValidator).validateModelContent(any(byte[].class),
                                                                                  any(ValidationContext.class));
    }

    private void validateInvalidExtensions() {
        SemanticModelValidationException exception = new SemanticModelValidationException(Collections
                .singletonList(genericJsonContentValidator.createModelValidationError("Extensions invalid",
                                                                                      "The extensions are invalid!!")));

        doThrow(exception).when(genericJsonExtensionsValidator).validateModelExtensions(any(byte[].class),
                                                                                        any(ValidationContext.class));
    }

    @Test
    public void sholud_callGenericJsonContentValidatorAndNotCallGenericJsonExtensionsValidator_when_validatingModelContentJsonContentType() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple.json");

        given().multiPart("file",
                          "simple-model.json",
                          fileContent,
                          "application/json")
                .post("/v1/models/{modelId}/validate",
                      genericJsonModel.getId())
                .then().expect(status().isNoContent()).body(isEmptyString());

        verify(genericJsonExtensionsValidator,
                       times(0))
                .validateModelExtensions(any(),
                                         any());

        verify(genericJsonContentValidator,
                       times(1))
                .validateModelContent(argThat(content -> new String(content).equals(new String(fileContent))),
                                      argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_callGenericJsonContentValidatorAndNotCallGenericJsonExtensionsValidator_when_validatingModelContentTextContentType() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple.json");

        given().multiPart("file",
                          "simple-model.json",
                          fileContent,
                          "text/plain")
                .post("/v1/models/{modelId}/validate",
                      genericJsonModel.getId())
                .then().expect(status().isNoContent()).body(isEmptyString());

        verify(genericJsonExtensionsValidator,
                       times(0))
                .validateModelExtensions(any(),
                                         any());

        verify(genericJsonContentValidator,
                       times(1))
                .validateModelContent(argThat(content -> new String(content).equals(new String(fileContent))),
                                      argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwExceptionAndCallGenericJsonContentValidatorAndNotCallGenericJsonExtensionsValidator_when_validatingInvalidModelContent() throws IOException {
        this.validateInvalidContent();

        byte[] fileContent = resourceAsByteArray("generic/model-simple.json");

        assertThatResponse(given().multiPart("file",
                                             "invalid-simple-model.json",
                                             fileContent,
                                             "application/json")
                .post("/v1/models/{modelId}/validate",
                      genericJsonModel.getId())
                .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("Content invalid");

        verify(genericJsonExtensionsValidator,
                       times(0))
                .validateModelExtensions(any(),
                                         any());

        verify(genericJsonContentValidator,
                       times(1))
                .validateModelContent(argThat(content -> new String(content).equals(new String(fileContent))),
                                      argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_notCallGenericJsonContentValidatorAndCallGenericJsonExtensionsValidator_when_validatingModelValidExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-valid-extensions.json");

        given().multiPart("file",
                          "simple-model-extensions.json",
                          fileContent,
                          "application/json")
                .post("/v1/models/{modelId}/validate/extensions",
                      genericJsonModel.getId())
                .then().expect(status().isNoContent()).body(isEmptyString());

        verify(genericJsonContentValidator,
                       times(0))
                .validateModelContent(any(),
                                      any());

        verify(genericJsonExtensionsValidator,
                       times(1))
                .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                                         argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelInvalidExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-invalid-extensions.json");

        assertThatResponse(given().multiPart("file",
                                             "simple-model-extensions.json",
                                             fileContent,
                                             "application/json")
                .post("/v1/models/{modelId}/validate/extensions",
                      genericJsonModel.getId())
                .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("required key [id] not found");

        verify(genericJsonContentValidator,
                       times(0))
                .validateModelContent(any(),
                                      any());

        verify(genericJsonExtensionsValidator,
                       times(1))
                .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                                         argThat(context -> !context.isEmpty()));
    }


    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelInvalidNameExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-invalid-name-extensions.json");

        assertThatResponse(given().multiPart("file",
            "model-simple-invalid-name-extensions.json",
            fileContent,
            "application/json")
            .post("/v1/models/{modelId}/validate/extensions",
                genericJsonModel.getId())
            .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("required key [id] not found", "required key [name] not found");

        verify(genericJsonContentValidator,
            times(0))
            .validateModelContent(any(),
                any());

        verify(genericJsonExtensionsValidator,
            times(1))
            .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelMismatchNameExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-mismatch-name-extensions.json");

        assertThatResponse(given().multiPart("file",
            "model-simple-mismatch-name-extensions.json",
            fileContent,
            "application/json")
            .post("/v1/models/{modelId}/validate/extensions",
                genericJsonModel.getId())
            .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("string [!@#$%^&*()] does not match pattern ^[a-z]([-a-z0-9]{0,24}[a-z0-9])?$");

        verify(genericJsonContentValidator,
            times(0))
            .validateModelContent(any(),
                any());

        verify(genericJsonExtensionsValidator,
            times(1))
            .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelLongNameExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-long-name-extensions.json");

        assertThatResponse(given().multiPart("file",
            "model-simple-long-name-extensions.json",
            fileContent,
            "application/json")
            .post("/v1/models/{modelId}/validate/extensions",
                genericJsonModel.getId())
            .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors(
                "expected maxLength: 26, actual: 35", "string [alfresco-adf-app-deployment-develop] does not match pattern ^[a-z]([-a-z0-9]{0,24}[a-z0-9])?$");

        verify(genericJsonContentValidator,
            times(0))
            .validateModelContent(any(),
                any());

        verify(genericJsonExtensionsValidator,
            times(1))
            .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelEmptyNameExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-empty-name-extensions.json");

        assertThatResponse(given().multiPart("file",
            "model-simple-empty-name-extensions.json",
            fileContent,
            "application/json")
            .post("/v1/models/{modelId}/validate/extensions",
                genericJsonModel.getId())
            .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors(
                "expected minLength: 1, actual: 0", "string [] does not match pattern ^[a-z]([-a-z0-9]{0,24}[a-z0-9])?$");

        verify(genericJsonContentValidator,
            times(0))
            .validateModelContent(any(),
                any());

        verify(genericJsonExtensionsValidator,
            times(1))
            .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSyntacticValidationException_when_validatingInvalidJsonExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-invalid-json-extensions.json");

        assertThatResponse(given().multiPart("file",
                                             "simple-model-extensions.json",
                                             fileContent,
                                             "application/json")
                .post("/v1/models/{modelId}/validate/extensions",
                      genericJsonModel.getId())
                .then().expect(status().isBadRequest())).isSyntacticValidationException()
                        .hasValidationErrors("org.json.JSONException: A JSONObject text must begin with '{' at 1 [character 2 line 1]");

        verify(genericJsonContentValidator,
                       times(0))
                .validateModelContent(any(),
                                      any());

        verify(genericJsonExtensionsValidator,
                       times(1))
                .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                                         argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwSemanticValidationException_when_validatingModelInvalidTypeExtensions() throws IOException {
        byte[] fileContent = resourceAsByteArray("generic/model-simple-invalid-type-extensions.json");

        assertThatResponse(given().multiPart("file",
                                             "simple-model-extensions.json",
                                             fileContent,
                                             "application/json")
                .post("/v1/models/{modelId}/validate/extensions",
                      genericJsonModel.getId())
                .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("expected type: String, found: Boolean");

        verify(genericJsonContentValidator,
                       times(0))
                .validateModelContent(any(),
                                      any());

        verify(genericJsonExtensionsValidator,
                       times(1))
                .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                                         argThat(context -> !context.isEmpty()));
    }

    @Test
    public void sholud_throwException_when_validatingModelInvalidSemanticExtensions() throws IOException {
        this.validateInvalidExtensions();

        byte[] fileContent = resourceAsByteArray("generic/model-simple-valid-extensions.json");

        assertThatResponse(given().multiPart("file",
                                             "simple-model-extensions.json",
                                             fileContent,
                                             "application/json")
                .post("/v1/models/{modelId}/validate/extensions",
                      genericJsonModel.getId())
                .then().expect(status().isBadRequest())).isSemanticValidationException().hasValidationErrors("Extensions invalid");

        verify(genericJsonContentValidator,
                       times(0))
                .validateModelContent(any(),
                                      any());

        verify(genericJsonExtensionsValidator,
                       times(1))
                .validateModelExtensions(argThat(content -> new String(content).equals(new String(fileContent))),
                                         argThat(context -> !context.isEmpty()));
    }
}
