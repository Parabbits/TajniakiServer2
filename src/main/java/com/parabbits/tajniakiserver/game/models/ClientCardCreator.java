package com.parabbits.tajniakiserver.game.models;

import com.parabbits.tajniakiserver.game.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientCardCreator {

    public static ClientCard createCard(Card card, Game game, Role role, Team team){
        switch (role){
            case BOSS:
                return createCardForBoss(card, game, team);
            case PLAYER:
                return createCardForPlayer(card, game, team);
        }
        return null;
    }

    public static List<ClientCard> createCards(List<Card> cards, Game game, Role role, Team team){
        List<ClientCard> clientCards = new ArrayList<>();
        for(Card card: cards){
            clientCards.add(createCard(card, game, role, team));
        }
        return clientCards;
    }

    private static ClientCard createCardForBoss(Card card, Game game, Team team){
        return new ClientCard(card.getIndex(), card.getWord(), card.getColor(), card.isChecked());
    }

    private static ClientCard createCardForPlayer(Card card, Game game, Team team) {
        WordColor color = getPlayerCardColor(card);
        ClientCard clientCard = new ClientCard(card.getIndex(), card.getWord(), color, card.isChecked());
        clientCard.setAnswers(getPlayerFromAnswer(game, card));
        clientCard.setFlags(getPlayerFromFlags(game, card, team));

        return clientCard;
    }

    private static WordColor getPlayerCardColor(Card card) {
        return card.isChecked()? card.getColor() : WordColor.LACK;
    }

    private static Set<String> getPlayerFromAnswer(Game game, Card card){
        Set<Player> players = game.getBoard().getAnswerManager().getPlayers(card);
        return getPlayersNicknames(players);
    }

    private static Set<String> getPlayersNicknames(Set<Player> players){
        return players.stream().map(Player::getNickname).collect(Collectors.toSet());
    }

    private static Set<String> getPlayerFromFlags(Game game, Card card, Team team){
        Set<Player> players = game.getBoard().getFlagsManager().getFlagsOwners(card, team);
        return getPlayersNicknames(players);
    }
}
