package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import javax.swing.text.StyledEditorKit.ForegroundAction;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final int noSet = -1;

    private final Env env;

    private final ThreadLogger[] playersThread;

    private long remainMiliSconds;

    private final long updateEach = 1000;

    private Thread dealerThread; // This is the way to get the dealer thread

    private final long Minute = 60000;

    final long FREEZE_TIME_MILLI = 1000;

    private final int timesUp = -1000;

    public boolean blockPlacing = false;

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
        this.remainMiliSconds = Minute;
        playersThread = new ThreadLogger[env.config.players];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        int i = 0;
        for (Player player : players) {
            playersThread[i] = new ThreadLogger(player, "player " + (player.id), env.logger);
            playersThread[i].startWithLog();
            i++;
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        this.dealerThread = Thread.currentThread();

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
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
            table.hints();
            updateTimerDisplay(false);
            removeCardsFromTable();
        }
    }

    public void resetDeck() {
        removeAllCardsFromTable();
        placeCardsOnTable();
        updateTimerDisplay(true);
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (Thread player : playersThread) {
            player.interrupt(); // tell all players the game is over
        }

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
    private synchronized void removeCardsFromTable() {// remove cards when a set was found

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
                        for (int playerID = 0; playerID < env.config.players; playerID++) {
                            if (table.getTokensQueues()[playerID].contains(slotsToRemove[i])) {
                                table.removeToken(playerID, slotsToRemove[i]);
                            }
                        }

                        table.removeCard(slotsToRemove[i]);
                    }
                }
            }
            placeCardsOnTable();
        }

    }

    public Thread getThread() {
        return this.dealerThread;
    }

    public boolean isSet(int playerId) { // wants to be exceuted when player hits his third token - need to check
        Queue<Integer> playerSet = table.getTokensQueues()[playerId]; // get all players tokens
        java.util.Iterator<Integer> iterator = playerSet.iterator();
        int cardsToCheck[] = new int[3];
        int index = 0;
        while (iterator.hasNext()) {
            cardsToCheck[index] = table.slotToCard[iterator.next()];
            index++;
        }
        return env.util.testSet(cardsToCheck);
    }

    // Returns the ID of the player that has a set on the table or -1 if no player
    // has a set
    public int isSetOnTable() {
        for (int i = 0; i < env.config.players; i++) {
            if (table.getTokensQueues()[i].size() == 3) {
                if (isSet(i)) {
                    return i;
                }
            }
        }
        return noSet;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (deck.size() > 0 || remainMiliSconds > 0) {
            if (deck.size() == 81) {
                blockPlacing = true;
            }
            Collections.shuffle(deck);

            for (int slot = 0; slot < env.config.tableSize && deck.size() > 0; slot++) { // check if the slot is empty
                if (table.slotToCard[slot] == null && deck.size() > 0) {
                    int card = deck.remove(0);
                    table.placeCard(card, slot);
                }
            }

        }

        else {
            terminate = true;
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */

    public synchronized void checkSet1(Player player) {
        System.out.println("check set for player " + player.id);
        if (table.getTokensQueues()[player.id].size() == 3) {
            int[] set = new int[3];
            int i = 0;
            for (int num : table.getTokensQueues()[player.id]) {
                set[i] = table.slotToCard[num];
                i++;
            }
            if (env.util.testSet(set)) {
                System.out.println("player " + player.id + " point");
                player.point();
                this.getThread().interrupt();
                remainMiliSconds = Minute + updateEach;

            } else {
                player.penalty();
            }
        }

    }

    public void checkSet(Player player, int[] set) {
        System.out.println("check set for player " + player.id);
        player.setIsFrozen(true);
        if (env.util.testSet(set) & table.getTokensQueues()[player.id].size() == 3) {
            synchronized (this) {
                if (table.getTokensQueues()[player.id].size() == 3) {
                    player.point();
                    dealerThread.interrupt();
                } else {
                    player.setIsFrozen(false);
                }
            }
        } else {
            player.penalty();

        }
    }

    private void sleepUntilWokenOrTimeout() {
        try {
            dealerThread.sleep(updateEach);
        } catch (InterruptedException e) {
            System.out.println("dealer inter");
            if (remainMiliSconds == -1000) {
                blockPlacing = true;
                resetDeck();
                blockPlacing = false;
            }

            else {
                removeCardsFromTable();
                remainMiliSconds = Minute + updateEach;
            }

        }

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */

    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            remainMiliSconds = Minute; // reset the timer 60,000
            removeAllCardsFromTable();
            placeCardsOnTable();
            env.ui.setCountdown(remainMiliSconds, false);
            blockPlacing = false;

        } else {
            remainMiliSconds = remainMiliSconds - 1000;
            if (remainMiliSconds == timesUp) {
                blockPlacing = true;
                updateTimerDisplay(true);
            } else {
                env.ui.setCountdown(remainMiliSconds, false);
                blockPlacing = false;
            }
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.removeAllTokens();
        for (int i = 0; i < env.config.tableSize; i++) {
            table.removeCard(i);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        System.out.println(deck.size());
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

    public Thread getPlayerThread(int playerId) {
        return playersThread[playerId];
    }
}
