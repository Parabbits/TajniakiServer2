package com.parabbits.tajniakiserver.game;

import com.google.gson.Gson;

import com.parabbits.tajniakiserver.game.messages.*;
import com.parabbits.tajniakiserver.game.models.*;
import com.parabbits.tajniakiserver.history.HistoryEntry;
import com.parabbits.tajniakiserver.shared.Game;
import com.parabbits.tajniakiserver.shared.GameStep;
import com.parabbits.tajniakiserver.utils.MessageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class GameController {

    // TODO: refaktoryzacja tego

    private final String START_MESSAGE_RESPONSE = "/queue/game/start";
    private final String ANSWER_MESSAGE_RESPONSE = "/queue/game/answer";
    private final String CLICK_MESSAGE_RESPONSE = "/queue/game/click";
    private final String QUESTION_MESSAGE_RESPONSE = "/queue/game/question";
    private final String END_MESSAGE_RESPONSE = "/queue/game/question";

    @Autowired
    private Game game;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private MessageManager messageManager;

    @PostConstruct
    public void init() {
        messageManager = new MessageManager(messagingTemplate);
    }

    @MessageMapping("/game/start")
    public void startGame(@Payload String nickname, SimpMessageHeaderAccessor headerAccessor) throws Exception {
        game.getState().setCurrentStep(GameStep.GAME); // TODO: to też powinno znajdować się w innym miejscu
        game.initializeGame();
        Player player = game.getPlayer(headerAccessor.getSessionId());
        if (player.getRole() == Role.BOSS) {
            StartGameMessage bossMessage = createStartGameMessage(Role.BOSS, player, game);
            messageManager.send(bossMessage, player.getSessionId(), START_MESSAGE_RESPONSE);
        } else {
            StartGameMessage playersMessage = createStartGameMessage(Role.PLAYER, player, game);
            messageManager.send(playersMessage, player.getSessionId(), START_MESSAGE_RESPONSE);
        }

        initHistory(game);
    }

    private void initHistory(Game game){
        game.getHistory().setWords(getWordsFromCards(WordColor.BLUE, game), Team.BLUE);
        game.getHistory().setWords(getWordsFromCards(WordColor.RED, game), Team.RED);
        game.getHistory().setKiller(getWordsFromCards(WordColor.KILLER, game).get(0));
    }

    private List<String> getWordsFromCards(WordColor color, Game game){
        return game.getBoard().getCards().stream().filter(card -> card.getColor() == color).map(Card::getWord).collect(Collectors.toList());
    }

    // TODO: można przenieść do oodzielnej klasy
    public static StartGameMessage createStartGameMessage(Role role, Player player, Game game) {
        StartGameMessage message = new StartGameMessage();
        message.setNickname(player.getNickname());
        message.setPlayerRole(role);
        message.setPlayerTeam(player.getTeam());
        message.setGameState(game.getState());
        List<ClientCard> cards = ClientCardCreator.createCards(game.getBoard().getCards(), game, role, player.getTeam());
        message.setCards(cards);
        message.setPlayers(new ArrayList<>(game.getPlayers()));

        return message;
    }

    @MessageMapping("/game/click")
    public void servePlayersAnswer(@Payload String word, SimpMessageHeaderAccessor headerAccessor) {
        Player player = game.getPlayer(headerAccessor.getSessionId());
        Card card = findCard(word);
        // TODO: zrobić refaktoryzację z tym
        if (!isPlayerTurn(player) || card.isChecked() || player.getRole()==Role.BOSS) {
            return;
        }
        game.getBoard().getAnswerManager().setAnswer(card, player);
        int answerForCard = game.getBoard().getAnswerManager().getCounter(card);
        if (isAllPlayersAnswer(player, answerForCard)) {
            game.getHistory().addAnswer(card.getWord(), card.getColor());
            handleAnswerMessage(player, card);
        } else {
            handleClickMessage(player);
        }
    }

    private Card findCard(String word){
        Card card = null;
        if(word.equals("--PASS--")){
            card = new Card(-1, "", WordColor.LACK, false);
        } else {
            card = game.getBoard().getCard(word);
        }
        return card;
    }

    private boolean isAllPlayersAnswer(Player player, int answerForCard) {
        return answerForCard == game.getTeamSize(player.getTeam()) - 1;
    }

    private void handleAnswerMessage(Player player, Card card) {
        // TODO: refaktoryzacja
        game.useCard(card);
        // TODO: zlikwidować to jakoś
        AnswerCorrectness.Correctness correctness = AnswerCorrectness.checkCorrectness(card.getColor(), player.getTeam());
        switch (correctness) {
            case CORRECT:
                handleCorrectMessage(card, true, player);
                break;
            case INCORRECT:
                handleIncorrectMessage(card, player);
                break;
            case KILLER:
                handleEndGameMessage(player, EndGameCause.KILLER, player.getTeam() == Team.BLUE ? Team.RED : Team.BLUE);
                break;
        }
        if (!game.getState().isGameActive()) { // TODO: zrobić lepsze zakończenie gry
            handleEndGameMessage(player, EndGameCause.ALL, player.getTeam());
        }
    }

    private void handleIncorrectMessage(Card card, Player player) {
        handleCorrectMessage(card, false, player);
    }

    private void handleCorrectMessage(Card card, boolean correct, Player player) {
        AnswerMessage answerResult = buildAnswerMessage(card, correct, player);
        messageManager.sendToAll(answerResult, ANSWER_MESSAGE_RESPONSE, game);
    }

    private void handleEndGameMessage(Player player, EndGameCause cause, Team winnerTeam) {
        EndGameMessage message = new EndGameMessage();
//        Team winner = player.getTeam() == Team.BLUE? Team.RED : Team.BLUE;
        message.setWinner(winnerTeam);
        message.setCause(cause);
        message.setRemainingBlue(game.getState().getRemainingBlue());
        message.setRemainingRed(game.getState().getRemainingRed());
        messageManager.sendToAll(message, END_MESSAGE_RESPONSE, game);
    }

    private void handleClickMessage(Player player) {
        ClickMessage message = buildClickMessage(player);
        messageManager.sendToRoleFromTeam(message, Role.PLAYER, player.getTeam(), CLICK_MESSAGE_RESPONSE, game);
    }

    private ClickMessage buildClickMessage(Player player) {
        List<Card> editedCards = game.getBoard().getAnswerManager().popCardsToUpdate(player);
        List<ClientCard> clientCards = prepareClientCards(editedCards, player);
        ClickMessage message = new ClickMessage(clientCards);
        List<Card> passCard = editedCards.stream().filter(x->x.getIndex() < 0).collect(Collectors.toList());
        if(!passCard.isEmpty()){
            ClientCard passClientCard = ClientCardCreator.createCard(passCard.get(0), game, player.getRole(), player.getTeam());
            message.setPass(passClientCard.getAnswers().size());
        }
        // TODO: dodac liczbę pominiętych
        return message;
    }

    private List<ClientCard> prepareClientCards(List<Card> cards, Player player) {
        List<ClientCard> clientCards = new ArrayList<>();
        for (Card card : cards) {
            if(card.getIndex() >= 0){
                ClientCard clientCard = ClientCardCreator.createCard(card, game, player.getRole(), player.getTeam());
                clientCards.add(clientCard);
            }
        }
        return clientCards;
    }

    private AnswerMessage buildAnswerMessage(Card card, boolean correct, Player player) {
        ClientCard clientCard = ClientCardCreator.createCard(card, game, player.getRole(), player.getTeam());
        return new AnswerMessage(clientCard, correct, game.getState());
    }

    private boolean isPlayerTurn(Player player) {
        return player.getTeam() == game.getState().getCurrentTeam() && player.getRole() == game.getState().getCurrentStage();
    }

    @MessageMapping("/game/question")
    public void setQuestion(@Payload String messsageText, SimpMessageHeaderAccessor headerAccessor) {
        Player player = game.getPlayer(headerAccessor.getSessionId());
        if (!isPlayerTurn(player) || player.getRole()==Role.PLAYER) {
            return;
        }
        BossMessage message = buildBossMessage(messsageText, new Gson());
        game.getHistory().addQuestion(message.getWord(), message.getNumber(), player.getTeam());
        if(!WordValidator.validate(message.getWord())){
            return;
        }
        messageManager.sendToAll(message, QUESTION_MESSAGE_RESPONSE, game);
    }

    private BossMessage buildBossMessage(@Payload String messsageText, Gson gson) {
        BossMessage message = gson.fromJson(messsageText, BossMessage.class);
        game.getState().setAnswerState(message.getWord(), message.getNumber());
        message.setGameState(game.getState());
        return message;
    }

    @MessageMapping("/game/flag")
    public void setFlag(@Payload String word, SimpMessageHeaderAccessor headerAccessor) {
        Player player = game.getPlayer(headerAccessor.getSessionId());
        Card card = game.getBoard().getCard(word);
        if (card.isChecked() || player.getRole()==Role.BOSS) {
            return;
        }
        game.getBoard().getFlagsManager().addFlag(player, card);
        ClickMessage message = buildFlagMessage(player, card);
        messageManager.sendToRoleFromTeam(message, Role.PLAYER, player.getTeam(), CLICK_MESSAGE_RESPONSE, game);
    }

    private ClickMessage buildFlagMessage(Player player, Card card) {
        List<Card> editedCards = Collections.singletonList(card);
        List<ClientCard> clientCards = prepareClientCards(editedCards, player);
        return new ClickMessage(clientCards);
    }

    @MessageMapping("/game/summary")
    public void getSummary(@Payload String message, SimpMessageHeaderAccessor headerAccessor){
//        if(!game.isStarted()){ // TODO: później to odkomentować
//            return;
//        } // TODO: spróbować zrobić to jakoś lepiej
        SummaryMessage summary = new SummaryMessage();
        // TODO: powstawiać odpowiednie wartości
        summary.setWinner(Team.BLUE);
        summary.setBlueRemaining(0);
        summary.setRedRemaining(4);
//        List<HistoryEntry> blueHistory = game.getHistory().getEntries().stream().filter(x->x.getTeam() == Team.BLUE).collect(Collectors.toList());
//        List<HistoryEntry> redHistory = game.getHistory().getEntries().stream().filter(x->x.getTeam() == Team.RED).collect(Collectors.toList());
//        List<HistoryEntry> blueHistory = getBlueHistory();
//        List<HistoryEntry> redHistory = getRedHistory();

        summary.setProcess(getHistory());
        summary.setCause(WinnerCause.ALL_FOUND);

        messageManager.sendToAll(summary, "queue/game/summary", game);

        // po utworzeniu
        game.reset();
    }

    private List<HistoryEntry> getHistory(){
        HistoryEntry entry1 = new HistoryEntry();
        entry1.setQuestion("Kaszanka");
        entry1.setNumber(3);
        entry1.addAnswer("Osioł", WordColor.BLUE);
        entry1.addAnswer("Kaszana", WordColor.NEUTRAL);
        entry1.setTeam(Team.BLUE);

        HistoryEntry entry2 = new HistoryEntry();
        entry2.setQuestion("Osiem");
        entry2.setNumber(2);
        entry2.addAnswer("Jeden", WordColor.RED);
        entry2.addAnswer("Dwa", WordColor.BLUE);
        entry2.setTeam(Team.RED);

        HistoryEntry entry3 = new HistoryEntry();
        entry3.setQuestion("Radar");
        entry3.setNumber(1);
        entry3.addAnswer("Talerz", WordColor.RED);
        entry3.setTeam(Team.BLUE);

        return Arrays.asList(entry1, entry2, entry3);
    }
}