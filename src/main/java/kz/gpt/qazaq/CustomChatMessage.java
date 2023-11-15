package kz.gpt.qazaq;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theokanning.openai.completion.chat.ChatMessage;
import lombok.Data;

@Data
public class CustomChatMessage extends ChatMessage {

    @JsonIgnore
    private Integer id;

    public CustomChatMessage(String role, String content, Integer id) {
        super(role, content);
        this.id = id;
    }
}
