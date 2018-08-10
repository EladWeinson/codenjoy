package com.codenjoy.dojo.services;

import com.codenjoy.dojo.services.dao.ActionLogger;
import com.codenjoy.dojo.services.multiplayer.GameField;
import com.codenjoy.dojo.services.multiplayer.MultiplayerService;
import com.codenjoy.dojo.services.multiplayer.MultiplayerServiceImpl;
import com.codenjoy.dojo.services.multiplayer.MultiplayerType;
import com.codenjoy.dojo.services.printer.BoardReader;
import com.codenjoy.dojo.services.printer.PrinterFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class PlayerServiceImplIntegrationTest {

    private PlayerService service;

    private ActionLogger actionLogger;
    private AutoSaver autoSaver;
    private GameService gameService;
    private PlayerController screenController;
    private PlayerController playerController;
    private MultiplayerService multiplayer;
    private Statistics statistics;
    private PlayerGames playerGames;
    private Map<String, GameType> gameTypes = new HashMap<>();

    @Before
    public void setup() {
        service = new PlayerServiceImpl() {{
            statistics = mock(Statistics.class);

            PlayerServiceImplIntegrationTest.this.playerGames
                    = this.playerGames = new PlayerGames(statistics);

            PlayerServiceImplIntegrationTest.this.multiplayer
                    = this.multiplayer = new MultiplayerServiceImpl(playerGames){};

            PlayerServiceImplIntegrationTest.this.playerController
                    = this.playerController = mock(PlayerController.class);

            PlayerServiceImplIntegrationTest.this.screenController
                    = this.screenController = mock(PlayerController.class);

            PlayerServiceImplIntegrationTest.this.gameService
                    = this.gameService = mock(GameService.class);

            PlayerServiceImplIntegrationTest.this.autoSaver
                    = this.autoSaver = mock(AutoSaver.class);

            PlayerServiceImplIntegrationTest.this.actionLogger
                    = this.actionLogger = mock(ActionLogger.class);

            this.autoSaverEnable = true;
        }};
    }

    @Test
    public void createTwoPlayersInSingleTypeWithOneAI() {
        when(gameService.getGame(anyString())).thenAnswer(
                inv -> getOrCreateGameType(inv.getArgumentAt(0, String.class))
        );

        // первый плеер зарегался
        Player player1 = service.register("player1", "callback1", "game1");
        assertEquals("[game1-super-ai@codenjoy.com, player1]", service.getAll().toString());

        // потом еще двое подоспели на ту же игру
        Player player2 = service.register("player2", "callback2", "game1");
        Player player3 = service.register("player3", "callback2", "game1");
        verify(gameTypes.get("game1"), times(1)).newAI("game1-super-ai@codenjoy.com");
        assertEquals("[game1-super-ai@codenjoy.com, player1, player2, player3]",
                service.getAll().toString());

        // второй вышел
        service.remove("player2");
        assertEquals("[game1-super-ai@codenjoy.com, player1, player3]",
                service.getAll().toString());

        // и третий тоже
        service.remove("player3");
        assertEquals("[game1-super-ai@codenjoy.com, player1]",
                service.getAll().toString());

        // смотрим есть ли пользователи
        assertEquals(true, service.contains("player1"));
        assertEquals(false, service.contains("player2"));

        // потом зарегались на другую игру
        Player player4 = service.register("player4", "callback4", "game2");
        verify(gameTypes.get("game2"), times(1)).newAI("game2-super-ai@codenjoy.com");
        Player player5 = service.register("player5", "callback5", "game2");
        Player player6 = service.register("player6", "callback6", "game1");
        assertEquals("[game1-super-ai@codenjoy.com, player1, player6]",
                service.getAll("game1").toString());
        assertEquals("[game2-super-ai@codenjoy.com, player4, player5]",
                service.getAll("game2").toString());

        // при этом у нас теперь два AI
        assertEquals(true, service.contains("game1-super-ai@codenjoy.com"));
        assertEquals(true, service.contains("game2-super-ai@codenjoy.com"));

        // взяли игру с плеерами
        assertEquals("game1", service.getAnyGameWithPlayers().name());

        // и рендомных прееров
        assertEquals("game1-super-ai@codenjoy.com",
                service.getRandom("game1").toString());
        assertEquals("game2-super-ai@codenjoy.com",
                service.getRandom("game2").toString());

        // несложно понять что берется просто первый в очереди
        service.remove("game2-super-ai@codenjoy.com");
        assertEquals("player4",
                service.getRandom("game2").toString());

        // закрыли регистрацию
        assertEquals(true, service.isRegistrationOpened());
        service.closeRegistration();
        assertEquals(false, service.isRegistrationOpened());
        Player player7 = service.register("player7", "callback7", "game3");
        assertEquals(false, service.contains("player7"));
        assertEquals("[]",
                service.getAll("game3").toString());

        // открыли регистрацию
        service.openRegistration();
        assertEquals(true, service.isRegistrationOpened());
        player7 = service.register("player7", "callback7", "game3");
        verify(gameTypes.get("game3"), times(1)).newAI("game3-super-ai@codenjoy.com");
        assertEquals(true, service.contains("player7"));
        assertEquals("[game3-super-ai@codenjoy.com, player7]",
                service.getAll("game3").toString());

        // загрузили AI вместо плеера
        service.reloadAI("player7");
        verify(gameTypes.get("game3"), times(1)).newAI("player7");
        assertEquals("[game3-super-ai@codenjoy.com, player7]",
                service.getAll("game3").toString());

        // обновили описание ребят
        List<PlayerInfo> infos = service.getAll().stream().map(player -> new PlayerInfo(player.getName() + "_updated",
                player.getCode(), player.getCallbackUrl(), player.getGameName())).collect(toList());
        service.updateAll(infos);
        assertEquals("[game1-super-ai@codenjoy.com_updated, " +
                "player1_updated, player4_updated, player5_updated, " +
                "player6_updated, game3-super-ai@codenjoy.com_updated, " +
                "player7_updated]", service.getAll().toString());

        // зарегали существующего пользователя в другую игру
        assertEquals("[game1-super-ai@codenjoy.com_updated, player1_updated, player6_updated]",
                service.getAll("game1").toString());
        assertEquals("[player4_updated, player5_updated]",
                service.getAll("game2").toString());
        player1 = service.register("player1_updated", "callback1", "game2");
        assertEquals("[game1-super-ai@codenjoy.com_updated, player6_updated]",
                service.getAll("game1").toString());
        assertEquals("[player4_updated, player5_updated, game2-super-ai@codenjoy.com, player1_updated]",
                service.getAll("game2").toString()); // TODO какого фига сюда AI ломится? Там же есть ребята уже

        // удалили всех нафиг
        service.removeAll();
        assertEquals("[]", service.getAll("game1").toString());
        assertEquals("[]", service.getAll("game2").toString());
        assertEquals("[]", service.getAll("game3").toString());

        // грузим плеера из сейва
        player1 = service.register(new PlayerSave("player1", "callback1", "game1", 120, "save"));
        assertEquals("[player1]", service.getAll("game1").toString());
        verify(gameTypes.get("game1"), times(0)).newAI("player1");

        // а теперь AI из сейва
        player1 = service.register(new PlayerSave("bot-super-ai@codenjoy.com", "callback", "game1", 120, "save"));
        assertEquals("[player1, bot-super-ai@codenjoy.com]", service.getAll("game1").toString());
        verify(gameTypes.get("game1"), times(1)).newAI("bot-super-ai@codenjoy.com");
    }

    private GameType getOrCreateGameType(String name) {
        if (gameTypes.containsKey(name)) {
            return gameTypes.get(name);
        }

        GameField field = mock(GameField.class);
        when(field.reader()).thenReturn(mock(BoardReader.class));

        GameType gameType = mock(GameType.class);
        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.SINGLE);
        when(gameType.createGame()).thenReturn(field);
        when(gameType.getPrinterFactory()).thenReturn(mock(PrinterFactory.class));
        when(gameType.newAI(anyString())).thenReturn(true);
        when(gameType.name()).thenReturn(name);

        gameTypes.put(name, gameType);

        return gameType;
    }
}