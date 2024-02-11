package bguspl.set.ex;

import java.util.Queue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    final int PENAlTY_MILLISECONDS = 3000;
    final int FREEZE_TIME_MILLI = 1000;
    final int POINT = 1;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) {
                        wait();
                    } // wait until notify from someone
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        playerThread.interrupt();
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) {
        if (table.slotToCard[slot] != null) {
            Queue<Integer> tokenQueue = table.getTokensQueues()[id]; // thy not [id][slot]?
            java.util.Iterator<Integer> iterator = tokenQueue.iterator();
            boolean placeToken = true;
            while (iterator.hasNext()) {
                int slotOfExistToken = iterator.next();
                if (slotOfExistToken == slot) {// It means there is already a token on the slot, so the player wants to
                    // remove the token
                    placeToken = false;
                    table.removeToken(id, slot); // call the table to remove the token
                    break;
                }

            }

            if (placeToken & tokenQueue.size() < 3) { // the player wants to put a new token
                synchronized (table.slotsLocks[slot]) { // prevent from a player to place token on empty slot
                    table.placeToken(id, slot); // checks if there is max of 3 or it happends by deafult?
                }
            }

            if (tokenQueue.size() == 3) {
                notifyAll(); // once the player hit third token on the table, he must notify to the delaer
                // and wait for him to check

            }

            // TODO implement
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {

        try {

            this.score = this.score + POINT;
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, this.score);
            playerThread.sleep(FREEZE_TIME_MILLI);
            this.env.ui.setFreeze(this.id, FREEZE_TIME_MILLI);

        } catch (InterruptedException e) { // need to understand what to do with the exception
            e.printStackTrace();
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        try {
            playerThread.sleep(PENAlTY_MILLISECONDS);
            this.env.ui.setFreeze(this.id, PENAlTY_MILLISECONDS);
        } catch (InterruptedException e) { // need to understand what to do with the exception
            e.printStackTrace();
        }
    }

    public int score() {
        return score;
    }
}
