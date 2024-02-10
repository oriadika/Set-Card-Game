package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Queue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;

import javax.swing.text.StyledEditorKit.ForegroundAction;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;


    private final int noScore = -1;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {// remove cards when a set was found

        for(int i = 0; i < env.config.players; i++){
        Queue<Integer>[] set  = table.getTokensQueues(); //get all players tokens
        for(int playerID = 0; playerID < set.length; playerID++){
            int cardsToCheck[] = new int[3];
            if (set[playerID].size() == 3) { //If the queue is not in size of 3 - cannot check set
                for(int k =0; k<set[playerID].size();k++){ //get all the cards to check
                    int card = set[playerID].poll();
                    cardsToCheck[k] = card;
                    set[playerID].add(card); //get to the end of the queue
                }

                boolean isSet = env.util.testSet(cardsToCheck);
                
                if (isSet) {
                    players[playerID].point();
                    for (int card : cardsToCheck){
                        table.removeCard(table.cardToSlot[card]);
                    }
                
                }
                   else{
                    players[playerID].penalty(); 
                }
            }       
            
        }
   }
}

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() { 
 
        while (deck.size() > 0) {
            
            Collections.shuffle(deck);

            for(int slot = 0; slot < env.config.tableSize; slot++){ //check if the slot is empty
                if (table.slotToCard[slot] == null){
                    int card = deck.remove(0);
                    table.placeCard(card, slot);

                }
            }
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
         Integer[] playersScore = new Integer[env.config.players]; // get all players scores
            for (int id = 0; id < playersScore.length; id++) {
                playersScore[id] = players[id].score();
            }
        

        int maxScore = noScore; //get the max score
        for (int i = 0; i < playersScore.length; i++) {
            if (playersScore[i] >= maxScore) {
                maxScore = playersScore[i];
            }
        }

        int numberOfWinners = 0;
        for (Player player : players) { //get number of winners 
            if (player.score() == maxScore) {
                numberOfWinners++;
            }
        }

        int[] winners = new int[numberOfWinners];
        int index = 0;
        for (int playerId = 0; playerId<playersScore.length; playerId++){
            if (playersScore[playerId]==maxScore){
                winners[index] = playerId;
                index++;
            }
        }

        env.ui.announceWinner(winners);
    }
}
