package kz.gpt.qazaq;


import com.google.gson.Gson;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class Main {
    public static void main(String[] args) throws TelegramApiException {
        YamlConfigReader configReader = new YamlConfigReader("application.yml");
        String botToken = configReader.getValue("telegram.bot.token");
        String botUsername = configReader.getValue("telegram.bot.username");
        String token = configReader.getValue("telegram.bot.open-ai-token");
        String login = configReader.getValue("telegram.bot.admin-user-login");
        Integer chatId = configReader.getValue("telegram.bot.admin-user-chat-id");

        List<Map<String, Object>> users = configReader.getValue("telegram.bot.users");
        Map<String, Long> usersMap = users.stream().collect(Collectors.toMap(itm -> (String) itm.get("login"), itm -> Long.valueOf(itm.get("chat-id").toString())));

        OpenAiService openAiService = new OpenAiService(token);
        BotService botService = new BotService(botUsername, botToken, openAiService, new Gson(), Long.valueOf(chatId), login, usersMap);
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(botService);
        log.info("service started");
    }
}