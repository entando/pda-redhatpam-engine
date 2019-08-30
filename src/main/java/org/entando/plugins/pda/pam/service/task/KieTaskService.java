package org.entando.plugins.pda.pam.service.task;

import org.entando.plugins.pda.core.engine.Connection;
import org.entando.plugins.pda.core.model.Task;
import org.entando.plugins.pda.core.service.task.TaskService;
import org.entando.web.request.PagedListRequest;
import org.entando.web.response.PagedMetadata;
import org.entando.web.response.PagedRestResponse;
import org.springframework.stereotype.Service;

@Service
public class KieTaskService implements TaskService {

    public PagedRestResponse<Task> list(Connection connection, PagedListRequest request) {
        return new PagedRestResponse<>(new PagedMetadata<>(request, 0));
    }

}
