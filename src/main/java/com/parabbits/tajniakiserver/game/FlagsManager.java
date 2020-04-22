package com.parabbits.tajniakiserver.game;

import com.parabbits.tajniakiserver.game.models.Card;
import com.parabbits.tajniakiserver.game.models.Player;
import com.parabbits.tajniakiserver.game.models.Team;

import java.util.*;
import java.util.stream.Collectors;

public class FlagsManager {
    
    private final Map<Card, Set<Player>> flagsMap = new HashMap<>();

    public void addFlag(Player player, Card card){
        if(!flagsMap.containsKey(card)){
            flagsMap.put(card, new HashSet<>());
        }
        flagsMap.get(card).add(player);
    }

    public void removeFlag(Player player, Card card){
        if(!flagsMap.containsKey(card)){
            flagsMap.get(card).remove(player);
        }
    }

    public Set<Player> getFlagsOwners(Card card, Team team){
        if(flagsMap.containsKey(card)){
            return flagsMap.get(card).stream().filter(x->x.getTeam()==team).collect(Collectors.toSet());
        }
        return new HashSet<>();
    }
}