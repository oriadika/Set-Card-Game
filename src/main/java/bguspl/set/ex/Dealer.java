package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final Env env;

    private final ThreadLogger[] playersThread;

    private long remainMiliSconds;

    private long updateEach = 1000;

    private Thread dealerThread; // This is the way to get the dealer thread

    private final long Minute = 60000;

    final long FREEZE_TIME_MILLI = 1000;

    private final int timesUp = 0;

    public boolean blockPlacing = false;

    private final int deckSize;

    volatile AtomicBoolean isOccupied;

    public final int Set = 1;
    public final int noSet = 2;
    public final int tokensRemoved = 3;

    public final int setSize = 3;
    public final long turnTimeoutWarningMillis;

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
        this.isOccupied = new AtomicBoolean();
        this.deckSize = env.config.deckSize;
        this.turnTimeoutWarningMillis = env.config.turnTimeoutWarningMillis;
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
        for (Player player : players) {
            player.terminate(); // tell all players the game is over
        }
        terminate = true;
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
            if (player.getIsFrozen()) {
                if (table.getTokensQueues()[player.id].size() == 3) {
                    if (isSet(player.id)) {
                        int[] slotsToRemove = new int[setSize];
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
            }

            placeCardsOnTable();
        }
        System.out.println("finished removing cards");

    }

    public Thread getThread() {
        return this.dealerThread;
    }

    public boolean isSet(int playerId) { // wants to be exceuted when player hits his third token - need to check
        Queue<Integer> playerSet = table.getTokensQueues()[playerId]; // get all players tokens
        java.util.Iterator<Integer> iterator = playerSet.iterator();
        int cardsToCheck[] = new int[setSize];
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
            if (deck.size() == deckSize) {
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

    public int testSet(Player player) {
        synchronized (player.getDealer().isOccupied) {
            System.out.println("player " + player.id + " in check set");
            if (table.getTokensQueues()[player.id].size() == setSize) {
                int[] set = new int[setSize];
                int i = 0;
                for (int num : table.getTokensQueues()[player.id]) {
                    set[i] = table.slotToCard[num];
                    i++;
                    System.out.println(num);
                }
                if (env.util.testSet(set)) {
                    isOccupied.set(true);
                    return Set;
                } else {
                    return noSet;
                }

            } 
                isOccupied.set(false);
                return tokensRemoved;
            
        }

    }

    public boolean checkSet1(Player player) {
        if (true) {
            dealerThread.interrupt();
            player.point();
            return true;
        } else {
            synchronized (isOccupied) {
                isOccupied.set(false);
                isOccupied.notifyAll();
                return false;
            }

        }

    }

    private void sleepUntilWokenOrTimeout() {
        synchronized (isOccupied) {
            if (remainMiliSconds > turnTimeoutWarningMillis) {
                updateEach = 1000;
            } else {
                updateEach = 10;
            }
            try {
                dealerThread.sleep(updateEach);
            } catch (InterruptedException e) {
                if (remainMiliSconds == -1000) {
                    blockPlacing = true;
                    resetDeck();
                    blockPlacing = false;

                } else {
                    System.out.println("dealer inter");
                    removeCardsFromTable();
                    remainMiliSconds = Minute + updateEach;
                }
                isOccupied.set(false);
                System.out.println("set isOccupied to false");
                isOccupied.notifyAll();
                System.out.println("notifyAll");
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
            if (remainMiliSconds == timesUp) {
                blockPlacing = true;
                updateTimerDisplay(true);
            }
            if (remainMiliSconds <= turnTimeoutWarningMillis) {
                remainMiliSconds = remainMiliSconds - 10;
                env.ui.setCountdown(remainMiliSconds, true);
            } else {
                env.ui.setCountdown(remainMiliSconds, false);
                remainMiliSconds = remainMiliSconds - 1000;
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
            int card = table.slotToCard[i];
            deck.add(card);
            table.removeCard(i);
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

    public Thread getPlayerThread(int playerId) {
        return playersThread[playerId];
    }
}
