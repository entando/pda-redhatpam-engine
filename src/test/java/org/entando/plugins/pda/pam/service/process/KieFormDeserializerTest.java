package org.entando.plugins.pda.pam.service.process;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.entando.plugins.pda.core.utils.TestUtils.createFullProcessForm;
import static org.entando.plugins.pda.core.utils.TestUtils.createFullTaskForm;
import static org.entando.plugins.pda.core.utils.TestUtils.createSimpleProcessForm;
import static org.entando.plugins.pda.core.utils.TestUtils.createSimpleTaskForm;
import static org.entando.plugins.pda.core.utils.TestUtils.readFromFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.entando.plugins.pda.core.model.form.Form;
import org.junit.Test;

public class KieFormDeserializerTest {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Form.class, new KieFormDeserializer());
        MAPPER.registerModule(module);
    }

    @Test
    public void shouldDeserializeSimpleProcessKieJsonToForm() throws Exception {
        Form result = MAPPER.readValue(readFromFile("form/simple-process-form.json"), Form.class);
        assertThat(result).isEqualTo(createSimpleProcessForm());
    }

    @Test
    public void shouldDeserializeFullProcessKieJsonToForm() throws Exception {
        Form result = MAPPER.readValue(readFromFile("form/full-process-form.json"), Form.class);
        assertThat(result).isEqualTo(createFullProcessForm());
    }

    @Test
    public void shouldDeserializeSimpleTaskKieJsonToForms() throws Exception {
        Form result = MAPPER.readValue(readFromFile("form/simple-task-form.json"), Form.class);
        assertThat(result).isEqualTo(createSimpleTaskForm());
    }

    @Test
    public void shouldDeserializeFullTaskKieJsonToForms() throws Exception {
        Form result = MAPPER.readValue(readFromFile("form/full-task-form.json"), Form.class);
        assertThat(result).isEqualTo(createFullTaskForm());
    }
}
