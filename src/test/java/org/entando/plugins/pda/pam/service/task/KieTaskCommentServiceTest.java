package org.entando.plugins.pda.pam.service.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entando.plugins.pda.core.utils.TestUtils.CONTAINER_ID_1;
import static org.entando.plugins.pda.core.utils.TestUtils.CONTAINER_ID_2;
import static org.entando.plugins.pda.core.utils.TestUtils.TASK_ID_1;
import static org.entando.plugins.pda.core.utils.TestUtils.TASK_ID_2;
import static org.entando.plugins.pda.core.utils.TestUtils.getDummyConnection;
import static org.entando.plugins.pda.core.utils.TestUtils.getDummyUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.plugins.pda.core.engine.Connection;
import org.entando.plugins.pda.core.exception.CommentNotFoundException;
import org.entando.plugins.pda.core.model.Comment;
import org.entando.plugins.pda.core.service.task.TaskCommentService;
import org.entando.plugins.pda.core.service.task.request.CreateCommentRequest;
import org.entando.plugins.pda.pam.exception.KieInvalidIdException;
import org.entando.plugins.pda.pam.exception.KieInvalidResponseException;
import org.entando.plugins.pda.pam.service.api.CustomQueryService;
import org.entando.plugins.pda.pam.service.api.KieApiService;
import org.entando.plugins.pda.pam.service.util.KieInstanceId;
import org.entando.plugins.pda.pam.util.KieTaskTestHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.kie.server.api.exception.KieServicesHttpException;
import org.kie.server.client.UserTaskServicesClient;
import org.springframework.http.HttpStatus;

@SuppressWarnings("PMD.TooManyMethods")
public class KieTaskCommentServiceTest {

    private static final String TASK_1 = "1@c1";
    private static final String TASK_COMMENT_1_1 = "1";

    private TaskCommentService kieTaskService;
    private UserTaskServicesClient userTaskServicesClient;
    private CustomQueryService customQueryService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        KieApiService kieApiService = mock(KieApiService.class);
        userTaskServicesClient = mock(UserTaskServicesClient.class);

        when(kieApiService.getUserTaskServicesClient(any())).thenReturn(userTaskServicesClient);

        customQueryService = mock(CustomQueryService.class);

        kieTaskService = new KieTaskCommentService(kieApiService, customQueryService);
    }

    @Test
    public void shouldListTaskComments() throws Exception {
        KieInstanceId taskId = new KieInstanceId(CONTAINER_ID_1, TASK_ID_1);

        // Given
        List<Comment> expectedResponse = KieTaskTestHelper.createKieTaskComments(TASK_ID_1);
        when(userTaskServicesClient.getTaskCommentsByTaskId(anyString(), anyLong()))
                .thenReturn(expectedResponse.stream()
                        .map(KieTaskCommentService::commentToDto)
                        .collect(Collectors.toList()));

        // When
        List<Comment> comments = kieTaskService.list(getDummyConnection(),
                getDummyUser("Chuck Norris"), taskId.toString());

        // Then
        assertThat(comments).isEqualTo(expectedResponse);
    }

    @Test
    public void shouldCreateTaskComment() {
        KieInstanceId taskId = new KieInstanceId(CONTAINER_ID_2, TASK_ID_2);
        Comment expected = KieTaskTestHelper.createKieTaskComment();
        CreateCommentRequest request = CreateCommentRequest.builder().comment(expected.getText()).build();

        // Given
        when(userTaskServicesClient.addTaskComment(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Long.valueOf(KieTaskTestHelper.TASK_COMMENT_ID_2_2));

        // When
        Comment comment = kieTaskService.create(getDummyConnection(),
                getDummyUser(KieTaskTestHelper.TASK_COMMENT_OWNER_2), taskId.toString(), request);

        // Then
        expected.setCreatedAt(comment.getCreatedAt());
        assertThat(comment).isEqualTo(expected);

        verify(userTaskServicesClient)
                .addTaskComment(eq(taskId.getContainerId()), eq(taskId.getInstanceId()), eq(expected.getText()),
                        eq(KieTaskTestHelper.TASK_COMMENT_OWNER_2), any());
    }

    @Test
    public void shouldAddPrefixToCreatedByWhenItClashesWithExistingGroupName() {
        // Given
        KieInstanceId taskId = new KieInstanceId(CONTAINER_ID_2, TASK_ID_2);
        Comment expected = KieTaskTestHelper.createKieTaskComment();
        CreateCommentRequest request = CreateCommentRequest.builder().comment(expected.getText()).build();
        when(userTaskServicesClient.addTaskComment(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Long.valueOf(KieTaskTestHelper.TASK_COMMENT_ID_2_2));
        Connection connection = getDummyConnection();
        String groupName = "admin";
        when(customQueryService.getGroups(connection, groupName)).thenReturn(Collections.singletonList(groupName));

        // When
        Comment comment = kieTaskService.create(connection, getDummyUser(groupName), taskId.toString(), request);

        // Then
        verify(customQueryService).getGroups(connection, groupName);
        assertThat(comment.getCreatedBy()).isEqualTo(KieTaskCommentService.PDA_USER_PREFIX + groupName);
    }

    @Test
    public void shouldGetTaskComment() {
        KieInstanceId taskId = new KieInstanceId(CONTAINER_ID_1, TASK_ID_1);

        // Given
        Comment expectedComment = KieTaskTestHelper.createKieTaskComments(TASK_ID_1).get(0);
        when(userTaskServicesClient.getTaskCommentById(any(), anyLong(), anyLong()))
                .thenReturn(KieTaskCommentService.commentToDto(expectedComment));

        // When
        Comment comment = kieTaskService.get(getDummyConnection(), null, taskId.toString(),
                KieTaskTestHelper.TASK_COMMENT_ID_1_1);

        // Then
        assertThat(comment).isEqualTo(expectedComment);
        verify(userTaskServicesClient)
                .getTaskCommentById(taskId.getContainerId(), taskId.getInstanceId(),
                        Long.valueOf(expectedComment.getId()));

    }

    @Test
    public void shouldDeleteTaskComment() {
        KieInstanceId taskId = new KieInstanceId(CONTAINER_ID_1, TASK_ID_1);

        // When
        String commentId = kieTaskService.delete(getDummyConnection(), null, taskId.toString(),
                KieTaskTestHelper.TASK_COMMENT_ID_1_1);

        // Then
        assertThat(commentId).isEqualTo(TASK_ID_1);
        verify(userTaskServicesClient)
                .deleteTaskComment(taskId.getContainerId(), taskId.getInstanceId(), Long.valueOf(commentId));
    }

    @Test
    public void shouldThrowNotFoundWhenGetTaskNotFoundResponse() {
        // Given
        when(userTaskServicesClient.getTaskCommentById(any(), anyLong(), anyLong()))
                .thenThrow(new KieServicesHttpException(null, HttpStatus.NOT_FOUND.value(), null, null));

        // Then
        expectedException.expect(CommentNotFoundException.class);

        // When
        kieTaskService.get(getDummyConnection(), null, TASK_1, TASK_COMMENT_1_1);
    }

    @Test
    public void shouldThrowKieInvalidResponseWhenGetTaskInternalErrorResponse() {
        // Given
        when(userTaskServicesClient.getTaskCommentById(any(), anyLong(), anyLong()))
                .thenThrow(new KieServicesHttpException(null, HttpStatus.INTERNAL_SERVER_ERROR.value(), null, null));

        // Then
        expectedException.expect(KieInvalidResponseException.class);

        // When
        kieTaskService.get(getDummyConnection(), null, TASK_1, TASK_COMMENT_1_1);
    }

    @Test
    public void shouldThrowNotFoundWhenDeleteNotFoundResponse() {
        // Given
        doThrow(new KieServicesHttpException(null, HttpStatus.NOT_FOUND.value(), null, null))
                .when(userTaskServicesClient).deleteTaskComment(any(), anyLong(), anyLong());

        // Then
        expectedException.expect(CommentNotFoundException.class);

        // When
        kieTaskService.delete(getDummyConnection(), null, TASK_1, TASK_COMMENT_1_1);
    }

    @Test
    public void shouldThrowKieInvalidResponseWhenDeleteInternalErrorResponse() {
        // Given
        doThrow(new KieServicesHttpException(null, HttpStatus.INTERNAL_SERVER_ERROR.value(), null, null))
                .when(userTaskServicesClient).deleteTaskComment(any(), anyLong(), anyLong());

        // Then
        expectedException.expect(KieInvalidResponseException.class);

        // When
        kieTaskService.delete(getDummyConnection(), null, TASK_1, TASK_COMMENT_1_1);
    }

    @Test
    public void shouldThrowInvalidKieIdWithInstanceIdNotNumeric() {
        // Then
        expectedException.expect(KieInvalidIdException.class);

        // When
        kieTaskService.list(null, null, "notnumeric@c1");
    }

    @Test
    public void shouldThrowInvalidKieIdWithInvalidSeparator() {
        // Then
        expectedException.expect(KieInvalidIdException.class);

        // When
        kieTaskService.list(null, null, "1-c1");
    }
}
