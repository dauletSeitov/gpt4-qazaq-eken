package kz.gpt.qazaq;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BotService extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;
    private final OpenAiService serviceWithTimeout;
    private final OpenAiService serviceWithoutTimeout;
    private final Gson gson;
    private static final String PATH = "data/users.json";

    private Map<Long, List<ChatMessage>> history = new ConcurrentHashMap<>();
    private Map<String, Long> authorizedUsers = new HashMap<>();
    private final Long adminChatId;

    public BotService(String botUsername, String botToken, OpenAiService serviceWithTimeout, OpenAiService serviceWithoutTimeout, Gson gson, Long adminChatId, String adminLogin, Map<String, Long> users) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.serviceWithTimeout = serviceWithTimeout;
        this.serviceWithoutTimeout = serviceWithoutTimeout;
        this.gson = gson;
        this.adminChatId = adminChatId;

        load();
        authorizedUsers.put(adminLogin, adminChatId);
        authorizedUsers.putAll(users);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }


    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage()) {
            onMessageReceived(update.getMessage());
        } else if (update.hasEditedMessage()) {
            onMessageEdited(update);
        } else if (update.hasCallbackQuery()) {
            onCallback(update.getCallbackQuery());
        }
    }

    private void onCallback(CallbackQuery callbackQuery) throws TelegramApiException {
        String[] split = callbackQuery.getData().split("=");
        Long userId = Long.parseLong(split[1]);
        authorizedUsers.put(split[0], userId);
        save();
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(callbackQuery.getFrom().getId());
        sendMessage.setText("User successfully registered");
        execute(sendMessage);

        SendMessage sendMessage2 = new SendMessage();
        sendMessage2.setChatId(userId);
        sendMessage2.setText("You successfully registered");
        execute(sendMessage2);
    }

    private void validate(Message message) throws TelegramApiException {
        String userName = message.getFrom().getUserName();
        if (!authorizedUsers.containsKey(userName)) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(message.getChatId());
            sendMessage.setText("User not authorized!");
            execute(sendMessage);
            throw new RuntimeException("User not authorized!");
        }
    }

    private void onMessageReceived(Message message) throws TelegramApiException {
        String text = message.getText();
        log.info("got message: {}", text);

        if (text.equalsIgnoreCase("/start")) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(message.getChatId());
            sendMessage.setText("""
                    Type "/c" to clean.
                    Type "/r" to register.
                    To force GPT-4, use the "f.query" format; otherwise, it will first attempt GPT-4, and in case of failure, it will return an answer from GPT-3.
                    """);
            execute(sendMessage);
            return;
        }


        if (text.equalsIgnoreCase("/r")) {

            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();

            InlineKeyboardButton acceptButton = new InlineKeyboardButton();
            acceptButton.setText("accept");
            acceptButton.setCallbackData(message.getFrom().getUserName() + "=" + message.getChatId());
            rowInline.add(acceptButton);

            markupInline.setKeyboard(List.of(rowInline));

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(adminChatId);
            sendMessage.setText(String.format("user @%s asks for registration", message.getFrom().getUserName()));
            sendMessage.setReplyMarkup(markupInline);

            execute(sendMessage);
            return;
        }

        if (text.equalsIgnoreCase("/c")) {
            history.remove(message.getFrom().getId());
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(message.getChatId());
            sendMessage.setText("----MY MIND IS CLEAN NOW----");
            execute(sendMessage);
            return;
        }

        validate(message);
        process(message);
    }

    private void process(Message message) throws TelegramApiException {
        Long userId = message.getFrom().getId();
        Integer messageId = message.getMessageId();
        Long chatId = message.getChatId();

        long start = System.currentTimeMillis();
        ChatMessage process = openApiCall(userId, messageId, message.getText());
        long end = System.currentTimeMillis();

        process.setContent(process.getContent() + " " + (end - start) / 1000 + "s.");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(process.getContent());
        Message execute = execute(sendMessage);

        addToHistory(userId, execute.getMessageId(), process);
    }

    private ChatMessage openApiCall(Long userId, Integer messageId, String query) {

        try {
            if (query.startsWith("f.")) {
                return openApiInnerCall(userId, messageId, query.substring(2), serviceWithoutTimeout, "gpt-4");
            }

            try {
                return openApiInnerCall(userId, messageId, query.substring(2), serviceWithTimeout, "gpt-4");
            } catch (Exception e) {
                return openApiInnerCall(userId, messageId, query.substring(2), serviceWithTimeout, "gpt-3.5-turbo");
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return new CustomChatMessage(ChatMessageRole.ASSISTANT.value(), "error", new Random().nextInt());
        }
    }

    private ChatMessage openApiInnerCall(Long userId, Integer messageId, String query, OpenAiService service, String model) {
        List<ChatMessage> messages = history.getOrDefault(userId, new ArrayList<>());

        CustomChatMessage userMessage = new CustomChatMessage(ChatMessageRole.USER.value(), query, messageId);
        messages.add(userMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(model)
                .messages(messages)
                .build();

        ChatCompletionResult chatCompletion = service.createChatCompletion(chatCompletionRequest);
        ChatMessage message = chatCompletion.getChoices().get(0).getMessage();
        message.setContent(message.getContent() + " " + model);
        return message;
    }

    private void addToHistory(Long userId, Integer messageId, ChatMessage process) {
        List<ChatMessage> chatMessages = history.getOrDefault(userId, new ArrayList<>());
        CustomChatMessage message = new CustomChatMessage(process.getRole(), process.getContent(), messageId);
        chatMessages.add(message);
        history.put(userId, chatMessages);
    }


    private void onMessageEdited(Update update) throws TelegramApiException {

        Message editedMessage = update.getEditedMessage();
        Long userId = editedMessage.getFrom().getId();
        Integer messageId = editedMessage.getMessageId();
        Long chatId = editedMessage.getChatId();

        List<ChatMessage> toDelete = clearHistory(userId, messageId);
        deleteMessages(chatId, toDelete);
        process(editedMessage);
    }

    private void deleteMessages(Long chatId, List<ChatMessage> toDelete) throws TelegramApiException {
        for (ChatMessage chatMessage : toDelete) {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setMessageId(((CustomChatMessage) chatMessage).getId());
            deleteMessage.setChatId(chatId);
            execute(deleteMessage);
        }
    }

    private List<ChatMessage> clearHistory(Long userId, Integer messageId) {

        List<ChatMessage> before = new ArrayList<>();
        List<ChatMessage> toDelete = new ArrayList<>();

        List<ChatMessage> messages = history.getOrDefault(userId, new ArrayList<>());

        boolean found = false;
        for (ChatMessage message : messages) {

            if (found) {
                toDelete.add(message);
            } else {
                before.add(message);
            }

            if (message instanceof CustomChatMessage cm && cm.getId().equals(messageId)) {
                found = true;
            }
        }

        history.put(userId, before);
        return toDelete;
    }


    @SneakyThrows
    private void save() {
        String json = gson.toJson(authorizedUsers);
        Files.writeString(Path.of(PATH), json);
    }

    @SneakyThrows
    private void load() {
        if (Files.exists(Path.of(PATH))) {
            String json = Files.readString(Path.of(PATH));
            Type mapType = new TypeToken<Map<String, Long>>() {
            }.getType();
            authorizedUsers = gson.fromJson(json, mapType);
        }
    }
}
