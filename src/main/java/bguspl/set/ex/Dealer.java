package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.sql.Time;
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

    private long remainSeconds;

    private long remainMiliSconds;

    private long lastUpdateTime;

    private final long updateEach = 1000;

    private Thread dealerThread; //This is the way to get the dealer thread

    private final long Minute = 60000;


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
    private volatile boolean terminate; // defult false

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.remainSeconds = env.config.turnTimeoutMillis / 1000;
        this.remainMiliSconds = this.env.config.turnTimeoutMillis;
        this.lastUpdateTime = System.currentTimeMillis();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
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
        for (Player player : players) {
            player.terminate(); // tell all players the game is over
        }

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

        for (Player player : players) {
            if (table.getTokensQueues()[player.id].size() == 3) {
                if (isSet(player.id)) {
                    int[] slotsToRemove = new int[3];
                    java.util.Iterator<Integer> iterator = table.getTokensQueues()[player.id].iterator();
                    int index = 0;
                    while (iterator.hasNext()) {
                        slotsToRemove[index] = iterator.next();
                        index++;
                    }
                    for (int i = 0; i < slotsToRemove.length; i++) {
                        table.removeToken(player.id, slotsToRemove[i]);
                        table.removeCard(slotsToRemove[i]);
                    }
                }
            }
            placeCardsOnTable();

        }

        /*
         * for (int i = 0; i < env.config.players; i++) {
         * Queue<Integer>[] set = table.getTokensQueues(); // get all players tokens
         * for (int playerID = 0; playerID < set.length; playerID++) {
         * int cardsToCheck[] = new int[3];
         * if (set[playerID].size() == 3) { // If the queue is not in size of 3 - cannot
         * check set
         * for (int k = 0; k < set[playerID].size(); k++) { // get all the cards to
         * check
         * int card = set[playerID].poll();
         * cardsToCheck[k] = card;
         * set[playerID].add(card); // get to the end of the queue
         * }
         * 
         * boolean isSet = env.util.testSet(cardsToCheck);
         * 
         * if (isSet) {
         * players[playerID].point();
         * for (int card : cardsToCheck) {
         * table.removeCard(table.cardToSlot[card]);
         * }
         * 
         * } else {
         * players[playerID].penalty();
         * }
         * }
         * 
         * }
         * }
         */
    }

    public Thread getThread(){
        return dealerThread;
    }

    public boolean isSet(int playerId) { // wants to be exceuted when player hits his third token - need to check
        Queue<Integer> playerSet = table.getTokensQueues()[playerId]; // get all players tokens
        java.util.Iterator<Integer> iterator = playerSet.iterator();
        int cardsToCheck[] = new int[3];
        int index = 0;
        while (iterator.hasNext()) {
            cardsToCheck[index] = iterator.next();
            index++;
        }
        return env.util.testSet(cardsToCheck);
    }


    //Returns the ID of the player that has a set on the table or -1 if no player has a set
    public int isSetOnTable(){
        for (int i = 0; i < env.config.players; i++) {
            if (table.getTokensQueues()[i].size() == 3) {
                if (isSet(i)) {
                    return i;
                }
            }
        }
        return -1;
    }
    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (deck.size() > 0) {
            Collections.shuffle(deck);
            for (int slot = 0; slot < env.config.tableSize; slot++) { // check if the slot is empty
                if (table.slotToCard[slot] == null) {
                    synchronized (table.slotsLocks[slot]) {
                        int card = deck.remove(0);
                        table.placeCard(card, slot);
                    }

                }
            }
        }

        else{
            terminate = true;
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
       /*  for (Player player : players) {
           if (table.getTokensQueues()[player.id].size() == 3) { //Just to test other functions!
            if (isSet(player.id)) {
                player.point();
                updateTimerDisplay(true); // by H.W : when player hits set
                updatePlayerTimer(env.config.pointFreezeMillis);
                removeCardsFromTable();
            } else {
                player.penalty();
                updatePlayerTimer(env.config.penaltyFreezeMillis);
            }
            }*/
        try {
           dealerThread.sleep(updateEach);
        }
         catch (InterruptedException e) {
            if (remainMiliSconds == 0) {
                removeAllCardsFromTable();
                placeCardsOnTable();
                updateTimerDisplay(true);
            }

            else{
                int playerID = isSetOnTable();
                if (playerID != -1) {
                    players[playerID].point();
                    removeCardsFromTable();
                }
            }
            
        }


    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */

    private void updatePlayerTimer(long freezeTime, int playerId) {

    }

    private void updateTimerDisplay(boolean reset) {
       /*  if (reset) {
            remainMiliSconds = env.config.turnTimeoutMillis;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= updateEach) {
            if (remainMiliSconds >= 0) {
                env.ui.setCountdown(remainMiliSconds, false);
                remainMiliSconds = remainMiliSconds - 1000;
            }
            lastUpdateTime = System.currentTimeMillis();
        }
        if (remainMiliSconds == 0) {
            removeAllCardsFromTable();
            announceWinners();
        }
        */

        if (reset) {
            remainMiliSconds = Minute; // reset the timer 60,000
            env.ui.setCountdown(remainMiliSconds, false);
            
        }
        else{
            remainMiliSconds = remainMiliSconds - 1000;
            if (remainMiliSconds == 0 ) {
                this.dealerThread.interrupt();
            }
            else{
            env.ui.setCountdown(remainMiliSconds, false);
        }
    }


    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                table.removeCard(i);
                deck.add(table.slotToCard[i]);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        Integer[] playersScore = new Integer[env.config.players]; // get all players scores
        for (int id = 0; id < playersScore.length; id++) {
            playersScore[id] = players[id].score();
        }

        int maxScore = noScore; // get the max score
        for (int i = 0; i < playersScore.length; i++) {
            if (playersScore[i] >= maxScore) {
                maxScore = playersScore[i];
            }
        }

        int numberOfWinners = 0;
        for (Player player : players) { // get number of winners
            if (player.score() == maxScore) {
                numberOfWinners++;
            }
        }

        int[] winners = new int[numberOfWinners];
        int index = 0;
        for (int playerId = 0; playerId < playersScore.length; playerId++) {
            if (playersScore[playerId] == maxScore) {
                winners[index] = playerId;
                index++;
            }
        }

        env.ui.announceWinner(winners);
    }
}
