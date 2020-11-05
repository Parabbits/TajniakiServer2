package com.parabbits.tajniakiserver.shared.game;

import com.parabbits.tajniakiserver.summary.GameHistory;
import com.parabbits.tajniakiserver.game.GameState;
import com.parabbits.tajniakiserver.game.models.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class Game {

    // TODO: pomyśleć, jak rozwiązać to inaczej
//    @Autowired
//    private VotingController bossController;
    private final GamePlayersManager players;
    private final VotingManager voting;

    private  GameHistory history = new GameHistory();

    private Team firstTeam;
    private  GameState state;
    private GameSettings settings;
    private  Board board;

    public GamePlayersManager getPlayers(){
        return players;
    }

    public VotingManager getVoting(){
        return voting;
    }

    public Game(){
        state = new GameState();
        getState().setCurrentStep(GameStep.MAIN);
        settings = new GameSettings();
        players = new GamePlayersManager(settings);
        voting = new VotingManager();
        board = new Board();
    }
    // =====================================================
    //  zbiór, który może być wykorzystywany np. do zliczania, ilu graczy wykonało jakąś akcję. Np. ile osób wyświetliło podsumowanie
    // TODO: będzie można przenieść to do klasy zarządzającej graczami
    private final Set<Long> usedPlayers = new HashSet<>();

    public void usePlayer(Player player){
        usedPlayers.add(player.getId());
    }

    public void removeUsePlayer(Player player){
        usedPlayers.remove(player.getId());
    }

    public boolean areAllPlayerUsed(){
        return usedPlayers.size() == players.getPlayersCount();
    }

    public void clearUsedPlayer(){
        usedPlayers.clear();
    }
    // =====================================================

    // TODO: dodać oddzielną klasę do zarządzania graczami w grze

    private boolean started = false;

    public boolean isStarted(){
        return started;
    }

    // TODO: to też będzie trzeba jakoś rozwiązać
//    public boolean isVotingTimerStarted(){
//        return votingTimerStared;
//    }
//
//    public void setVotingTimerStarted(boolean started){
//        votingTimerStared = started;
//    }

    public void reset(){
        voting.reset();
        history = new GameHistory();
        firstTeam = null;
        state = new GameState();
        settings = new GameSettings();
        board = new Board();
//        playerCounter = 0;
        started = false;

//        setVotingTimerStarted(false);
        // TODO: przenieść to w jakieś inne miejsce. Może w GameManager zrobić reset graczy
        for(Player player: players.getAllPlayers()){
            player.setReady(false);
        }
        getState().setCurrentStep(GameStep.LOBBY);
    }

    public GameState getState(){
        return state;
    }

    public Team getFirstTeam(){
        return firstTeam;
    }

    public Board getBoard(){
        return board;
    }


    public void startVoting(){
        // TODO: zrobić to jakoś fajnie
//        if(blueVoting.getCandidates().isEmpty()){
//            blueVoting.init(players);
//            redVoting.init(players);
//            // TODO: z uruchomieniem licznika można poczekać, aż wszyscy się załadują
////            startTimer();
//        }
    }

//    private void startTimer(){
//        // TODO: można zrobić klasę Timer, która będzie znajdować się w Game. Kontrolery będą mogły z niego korzystać.
//        new java.util.Timer().schedule(
//                new java.util.TimerTask() {
//                    @Override
//                    public void run() {
//                        bossController.endVoting();
//                        // TODO: można zrobić wysyłanie czasu do klientów co sekunde
//                    }
//                },
//                10000
//        );
//    }

    public void initializeGame() throws IOException {
        if(firstTeam == null){
            firstTeam = randomFirstGroup();
            board.init(firstTeam, settings);
            state.initState(firstTeam, settings.getFirstTeamWords());
        }
        started = true;
    }

    private Team randomFirstGroup(){
        int randValue = new Random().nextInt(100);
        return randValue < 50 ? Team.BLUE : Team.RED;
    }

    public void useCard(Card card){
        // TODO: prawdopodobnie tutaj wystarczy word
        board.getAnswerManager().reset();
        board.getFlagsManager().removeFlags(card);
        state.useCard(card);
    }

    public GameSettings getSettings() {
        return settings;
    }

    public GameHistory getHistory(){
        return history;
    }
}
