package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final int noScore = -1;

    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

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
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
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
        // still need to work on this
        Integer[] playersScore = new Integer[env.config.players]; // maybe all winners are in tie
        for (Player player : players) {
            for (int id = 0; id < playersScore.length; id++) {
                playersScore[id] = player.score();
            }
        }
        Integer[] potentionalWinners = new Integer[env.config.players];
        for (int i = 0; i < env.config.players; i++) {
            potentionalWinners[i] = noScore;
        }
        int maxScore = noScore;
        for (int id = 0; id < potentionalWinners.length; id++) {
            if (playersScore[id] >= maxScore) {
                potentionalWinners[id] = id; // score in position i is the score of the player i
                maxScore = playersScore[id];
            }
        }

        int numberOfWinners = 0;
        for (int i = 0; i < potentionalWinners.length; i++) {
            if (potentionalWinners[i] != noScore) {
                numberOfWinners++;
            }
        }

        Integer[] winners = new Integer[numberOfWinners];
        for (int i = 0; i < potentionalWinners.length; i++) {
            if (potentionalWinners[i] != noScore) {
                winners[i] = i;
            }
        }

        env.ui.announceWinner(null);
        // TODO implement
    }
}
