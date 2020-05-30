package com.parabbits.tajniakiserver.lobby;

import com.parabbits.tajniakiserver.shared.Game;
import com.parabbits.tajniakiserver.game.models.Player;
import com.parabbits.tajniakiserver.game.models.Team;
import com.parabbits.tajniakiserver.shared.GameStep;
import com.parabbits.tajniakiserver.utils.MessageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class LobbyController {

    private final String LOBBY_START = "/lobby/players";
    private final String LOBBY_CONNECT = "/queue/connect";
    private final String LOBBY_END = "/queue/lobby/start";
    private final String LOBBY_READY = "/lobby/ready"; // TODO: zmienić to w aplikacji

    @Autowired
    public Game game;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private MessageManager messageManager;

    @PostConstruct
    private void init(){
        messageManager = new MessageManager(messagingTemplate);
    }

    @MessageMapping("/lobby/connect")
    public void connectToGame(@Payload String nickname, SimpMessageHeaderAccessor headerAccessor) throws Exception {
        if(!isCorrectStep(game)){
            return;
        }
        setLobbyStep(game);
        String sessionId = headerAccessor.getSessionId();
        Player player = game.getPlayer(sessionId);
        if(game.getPlayer(sessionId)==null){
            player = game.addPlayer(headerAccessor.getSessionId(), nickname);
        }

        // TODO: wysłąć do podłączonego gracza informacje o ustawieniach rozgrywki
        StartLobbyMessage message = createStartLobbyMessage(player);
        sendStartLobbyMessage(player, message);
    }

    private void sendStartLobbyMessage(Player player, StartLobbyMessage message) {
        messageManager.send(message, player.getSessionId(), LOBBY_START);
        messageManager.sendToAll(player, LOBBY_CONNECT, game);
    }

    private StartLobbyMessage createStartLobbyMessage(Player player) {
        StartLobbyMessage message = new StartLobbyMessage(getAllPlayersInLobby(), game.getSettings());
        message.setMinPlayersInTeam(game.getSettings().getMinTeamSize());
        message.setMaxPlayersInTeam(game.getSettings().getMaxTeamSize());
        message.setPlayerId(player.getId());
        return message;
    }

    private boolean isCorrectStep(Game game){
        return game.getState().getCurrentStep().equals(GameStep.MAIN) || game.getState().getCurrentStep().equals(GameStep.LOBBY);
    }

    private void setLobbyStep(Game game){
        game.getState().setCurrentStep(GameStep.LOBBY);
    }

    private List<Player> getAllPlayersInLobby() {
        return game.getPlayers();
    }

    // TODO: przenieść to do gdzieś
    @MessageMapping("lobby/disconnect")
    public void disconnectPlayer(@Payload String nickname, SimpMessageHeaderAccessor headerAccessor){
        System.out.println("Disconnect event");
        Player player = game.getPlayer(headerAccessor.getSessionId());
        messageManager.sendToAll(player, "/queue/lobby/disconnect", game);
    }

    @MessageMapping("/lobby/team")
    public void changeTeam(@Payload String teamText, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Player player = game.getPlayer(sessionId);
        System.out.println("Gracz " + player.getId() + " mienia drużynę");
        Team team = getTeam(teamText);
        changePlayerTeam(player, team);
    }

    private void changePlayerTeam(Player player, Team team){
        if(canChangeTeam(team)){
            player.setTeam(team);
        }
        TeamMessage message = new TeamMessage(player.getId(), player.getTeam().toString());
        messageManager.sendToAll(message, "/lobby/team", game);
        if (isEndChoosing()) {
            finishChoosing();
        }
    }

    private boolean isEndChoosing(){
        return areAllReady() && isMinNumberOfPlayers();
    }

    @MessageMapping("/lobby/auto_team")
    public void joinAuto(@Payload String message, SimpMessageHeaderAccessor headerAccessor){
        Player player = game.getPlayer(headerAccessor.getSessionId());
        Team smallerTeam = getSmallerTeam();
        changePlayerTeam(player, smallerTeam);
    }

    private Team getSmallerTeam(){
        int blueTeamSize = game.getTeamSize(Team.BLUE);
        int redTeamSize = game.getTeamSize(Team.RED);
        System.out.println("Niebiescy " + blueTeamSize);
        System.out.println("Czerwoni " + redTeamSize);
        return blueTeamSize<redTeamSize? Team.BLUE: Team.RED;
    }


    private boolean canChangeTeam(Team team) {
        if(team == Team.LACK){
            return true;
        }
        int teamSize = game.getPlayers(team).size();
        return teamSize < game.getSettings().getMaxTeamSize();
    }

    // TODO: to powinno być w innym miejscu
    private Team getTeam(String team) {
        switch (team) {
            case "RED":
                return Team.RED;
            case "BLUE":
                return Team.BLUE;
            default:
                return Team.LACK;
        }
    }

    @MessageMapping("/lobby/ready")
    public void changeReady(@Payload boolean ready, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Player player = game.getPlayer(sessionId);
        if (canSetReady(player)){
            player.setReady(ready);
            LobbyReadyMessage message = new LobbyReadyMessage(player.getId(), ready);
            messageManager.sendToAll(message, LOBBY_READY, game);
        }
        if (isEndChoosing()) {
            finishChoosing();
        }
    }

    private boolean canSetReady(Player player){
        return player.getTeam() == Team.BLUE || player.getTeam() == Team.RED;
    }

    private void finishChoosing(){
        int TIME = 1000;
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        messageManager.sendToAll("START", LOBBY_END, game);
                    }
                },
                TIME
        );
    }

    private boolean areAllReady() {
        List<Player> readyPlayers = game.getPlayers().stream().filter(Player::isReady).collect(Collectors.toList());
        return readyPlayers.size() == game.getPlayers().size();
    }

    private boolean isMinNumberOfPlayers(){
        int bluePlayers = game.getPlayers(Team.BLUE).size();
        int redPlayers = game.getPlayers(Team.RED).size();
        return bluePlayers >= game.getSettings().getMinTeamSize() && redPlayers >= game.getSettings().getMinTeamSize();
    }

    /**
     * Metoda służy wyłącznie do testowania. Przydziela graczom numer id, który normalnie jest przydzielany podczas wejścia do lobby
     * @param message
     * @param headerAccessor
     */
    @MessageMapping("/test/getid")
    public void getid(@Payload String message, SimpMessageHeaderAccessor headerAccessor){
        Player player = game.getPlayer(headerAccessor.getSessionId());
        if(player != null){
            messageManager.send(player.getId(), player.getSessionId(), "/queue/lobby/id");
        }
    }
}
