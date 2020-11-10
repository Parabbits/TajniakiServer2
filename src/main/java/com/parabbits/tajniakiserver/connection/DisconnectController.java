package com.parabbits.tajniakiserver.connection;


import com.parabbits.tajniakiserver.game.messages.StartGameMessage;
import com.parabbits.tajniakiserver.game.models.Player;
import com.parabbits.tajniakiserver.game.models.Role;
import com.parabbits.tajniakiserver.game.models.Team;
import com.parabbits.tajniakiserver.lobby.manager.Lobby;
import com.parabbits.tajniakiserver.lobby.manager.LobbyManager;
import com.parabbits.tajniakiserver.shared.DisconnectMessage;
import com.parabbits.tajniakiserver.shared.PlayerSessionId;
import com.parabbits.tajniakiserver.shared.game.Game;
import com.parabbits.tajniakiserver.shared.game.GameManager;
import com.parabbits.tajniakiserver.shared.game.GameStep;
import com.parabbits.tajniakiserver.utils.MessageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DisconnectController {

    private final String DISCONNECT_PATH = "/queue/common/disconnect";
    private final String NEW_BOSS_PATH = "/queue/game/new_boss";

    @Autowired
    private MessageManager messageManager;

    @Autowired
    private GameManager gameManager;

    @Autowired
    private LobbyManager lobbyManager;


    public void disconnectPlayer(String playerSession, UUID gameId){
        Lobby lobby = lobbyManager.findLobby(gameId);
        Game game = gameManager.findGame(gameId);
        Player player = game.getPlayers().getPlayer(playerSession);
        lobby.removePlayer(playerSession);
        List<String> remainingsPlayersSessions = PlayerSessionId.getSessionsIds(lobby.getPlayers());
        DisconnectMessage message = null;
        switch (game.getState().getCurrentStep()){
            case LOBBY:

                message = LobbyDisconnector.getMessage(player, remainingsPlayersSessions);
                break;
            case VOTING:
                message = VotingDisconnector.getMessage(player, game);
                break;
            case GAME:
                message = GameDisconnector.getMessage(player, game);
                break;

        }
        if(message != null){
            messageManager.sendToAll(message, DISCONNECT_PATH, remainingsPlayersSessions);
            game.getState().setCurrentStep(message.getCurrentStep());

        }
    }



}
