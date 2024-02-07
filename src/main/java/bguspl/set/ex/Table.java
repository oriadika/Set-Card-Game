package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterfaceDecorator;
import bguspl.set.UserInterfaceSwing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {
    /**
     * The game environment object.
     */
    private final Env env;

    private final int maxTokens = 2;

    private final int noToken = -1;

    private final Integer[][] tokensMap;

    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokensMap = new Integer [env.config.players][maxTokens]; 
        for (int i=0; i<env.config.players; i++){
            for (int j=0; j<maxTokens; j++){
                tokensMap[i][j] = noToken;
            }

        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);

    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) { // while I am placing a new card, I do not want any player to choose the
        // temporariy empty slot as his set. So, I want to lock the slot
        try {
            Thread.sleep(env.config.tableDelayMillis); /* While I am placing a card, no one can touch the table */
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;

        Object lock = slot; //Here I get the slot of the card

        if (slotToCard[slot] != null) { // there is a card in the given slot, we want to replace it
            synchronized(lock){
                removeCard(slot);
                env.ui.removeCard(slot); //remove from table in ui
                env.ui.placeCard(card, slot);
            }
            
        } else { //there is no card in the given slot, we still want to lock the slot
            synchronized(lock){
                env.ui.placeCard(card, slot); // Include ui swing. I have a card that I want to place in empty slot
            }
        }
        slotToCard[slot] = card;
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) { //No need to lock becuse only place card calls me and it has lock
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        slotToCard[slot] = null; // No card in there
    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) { //there is nothing to lock here becuse 2 players can place token on the same card
        env.ui.placeToken(player, slot); // for logger and ui
    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        for (int i=0; i<maxTokens; i++){
            if (tokensMap[player][i]==slot){ //there is a token there
                env.ui.removeToken(player, slot);
                tokensMap[player][i] = noToken;
                return false;
            } 
        }
         //there is no token to remove
        return false;
        
    }
}
