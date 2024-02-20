package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterfaceDecorator;
import bguspl.set.UserInterfaceSwing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import javax.management.ObjectName;
import javax.swing.text.html.HTMLDocument.Iterator;

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

    private final int maxTokens = 3;

    protected Integer[] slotsLocks;

    private Queue<Integer>[] tokensQueues;

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
        this.tokensQueues = new Queue[env.config.players];
        for (int i = 0; i < tokensQueues.length; i++) {
            tokensQueues[i] = new java.util.LinkedList<>();
        }
        this.slotsLocks = new Integer[env.config.tableSize]; // slots to lock while prforming actions
        for (int i = 0; i < slotsLocks.length; i++) {
            slotsLocks[i] = i;
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

    public void playerAction(Player player, int slot) {
        System.out.println("player action still running");
        if (!player.isBlocked()) {
            if (slotToCard[slot] != null) {
                if (!removeToken(player.id, slot)) {
                    placeToken(player.id, slot);
                }
            }

            if (tokensQueues[player.id].size() == maxTokens & !tokensQueues[player.id].contains(slot)) {
                return;
            }

            if (tokensQueues[player.id].size() == maxTokens) {
                int action = -1;
                synchronized (this) {
                    player.setIsFrozen(true);
                    action = player.getDealer().testSet(player);
                    if (action == player.getDealer().Set) {
                        while (player.getDealer().isOccupied.get()) {
                            synchronized (player.getDealer().isOccupied) {
                                try {
                                    player.getDealerThread().interrupt();
                                    System.out.println("waiting");
                                    player.getDealer().isOccupied.wait();

                                } catch (InterruptedException e) {
                                }
                                ;
                            }
                        }
                        player.point();
                    }

                    System.out.println("player " + player.id + " action = " + action);
                    System.out.println(player.getDealer().isOccupied.get());
                }

                if (action == player.getDealer().noSet) {
                    player.penalty();
                } else if (action == player.getDealer().tokensRemoved) {
                }
                ;
            }
            player.setIsFrozen(false);
        }

        // If the token isnt there but the player has 3 tokens already*/
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

    // get the token map
    public Queue<Integer>[] getTokensQueues() {
        return tokensQueues;
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
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;

        if (slotToCard[slot] != null) { // there is a card in the given slot, we want to replace it
            synchronized (slotsLocks[slot]) {
                removeCard(slot);
                env.ui.removeCard(slot); // remove from table in ui
                env.ui.placeCard(card, slot);
            }

        } else { // there is no card in the given slot, we still want to lock the slot - no
                 // player puts token on empty
            synchronized (slotsLocks[slot]) {
                env.ui.placeCard(card, slot); // Include ui swing. I have a card that I want to place in empty slot
            }
        }
        slotToCard[slot] = card;
    }

    public Integer getSlot(int slot) {
        return slotsLocks[slot];
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            synchronized (slotsLocks[slot]) { // I want to lock the slot while I am removing the card
                slotToCard[slot] = null; // No card in there

                this.env.ui.removeCard(slot); // remove from table in ui
                Thread.sleep(env.config.tableDelayMillis);
            }

        } catch (InterruptedException ignored) {
        }
    }

    public void removeAllTokens() {

        for (int i = 0; i < env.config.players; i++) {
            int[] slotsToRemove = { -1, -1, -1 };
            int index = 0;
            for (int slot : tokensQueues[i]) {
                slotsToRemove[index] = slot;
                index++;
            }
            for (int slot : slotsToRemove) {
                removeToken(i, slot);
            }
        }
    

    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        synchronized (slotsLocks[slot]) { // prevents from one thread to remove and the other to place token
            if (tokensQueues[player].size() < 3) {
                tokensQueues[player].add(slot);
                env.ui.placeToken(player, slot);
            }

        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
            if (tokensQueues[player].contains(slot)) {
                env.ui.removeToken(player, slot);
                tokensQueues[player].remove(slot);
                return true;
            } else {
                return false;
            }

    }
}
