package org.activiti.cloud.starter.tests.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.activiti.cloud.services.events.listeners.MessageProducerCommandContextCloseListener;
import org.activiti.cloud.services.test.containers.KeycloakContainerApplicationInitializer;
import org.activiti.cloud.services.test.containers.RabbitMQContainerApplicationInitializer;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles(AuditProducerIT.AUDIT_PRODUCER_IT)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"spring.activiti.asyncExecutorActivate=true"})
@TestPropertySource("classpath:application-test.properties")
@ContextConfiguration(classes = ServicesAuditITConfiguration.class,
                      initializers = {RabbitMQContainerApplicationInitializer.class,
                                      KeycloakContainerApplicationInitializer.class}
)
@DirtiesContext
public class MessageProducerCommandContextCloseListenerIT {

    @Autowired
    private RuntimeService runtimeService;

    @SpyBean
    private MessageProducerCommandContextCloseListener subject;

    @Autowired
    private AuditConsumerStreamHandler streamHandler;

    @BeforeEach
    public void setUp() {
        streamHandler.clear();
    }

    @Test
    public void contextLoads() {
        //
    }

    @Test
    public void shouldNot_callCloseListener_when_exceptionOccursOnActivitiTransaction() {
        // given
        String processDefinitionKey = "rollbackProcess";

        // when
        Throwable thrown = catchThrowable(() -> {
            runtimeService.createProcessInstanceBuilder()
                          .processDefinitionKey(processDefinitionKey)
                          .start();
        });

        // then
        ProcessInstance result = runtimeService.createProcessInstanceQuery()
                                               .processDefinitionKey(processDefinitionKey)
                                               .singleResult();
        assertThat(result).isNull();
        assertThat(thrown).isInstanceOf(ActivitiException.class);
        verify(subject, never()).closed(any(CommandContext.class));
    }

    @Test
    public void should_rollbackSentMessages_when_exceptionOccursAfterSent() throws InterruptedException {
        // given
        String processDefinitionKey = "SimpleProcess";

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                CommandContext commandContext = invocation.getArgument(0);

                doCallRealMethod().when(subject)
                                  .closed(any(CommandContext.class));

                subject.closed(commandContext);

                throw new MessageDeliveryException("Test exception");
            }
        }).when(subject)
          .closed(any(CommandContext.class));

        // when
        Throwable thrown = catchThrowable(() -> {
            runtimeService.createProcessInstanceBuilder()
                          .processDefinitionKey(processDefinitionKey)
                          .start();
        });

        // then
        ProcessInstance result = runtimeService.createProcessInstanceQuery()
                                               .processDefinitionKey(processDefinitionKey)
                                               .singleResult();
        assertThat(result).isNull();
        assertThat(thrown).isInstanceOf(MessageDeliveryException.class);

        // let's wait
        Thread.sleep(2000);
        assertThat(streamHandler.getAllReceivedEvents()).isEmpty();
    }
}
