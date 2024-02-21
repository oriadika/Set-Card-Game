package bguspl.set.ex;

import java.sql.Time;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import bguspl.set.Env;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    final long One_Second = 1000;
    final long PENAlTY_MILLISECONDS = 3000;
    final long FREEZE_TIME_MILLI =1000;
    final long NO_Time_MILLI = 0;
    final int POINT = 1;

    /**
     * The game environment object.
     */
    private final Env env;

    // private BlockingQueue actions;

    private boolean isFrozen;

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

    private LinkedBlockingQueue<Integer> actions;

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

    private Dealer dealer;

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
        this.isFrozen = false;
        // this.actions = new BlockingQueue();
        this.actions = new LinkedBlockingQueue<>(3);
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    public void run() {
        this.env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!this.human) {
            createArtificialIntelligence();
        }

        while (!this.terminate) {
            while (!isBlocked()) {
                if (!actions.isEmpty()) {
                    int slot = actions.poll();
                    table.playerAction(this, slot);
                }
                synchronized (this) {
                    notifyAll(); // notify the ai thread he can add elements now
                }

            }

        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

    }

    public boolean getIsFrozen() {
        return isFrozen;
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
                try {
                    Random random = new Random();
                    keyPressed(random.nextInt(table.slotToCard.length));
                    synchronized (this) {
                        wait();
                    }

                } catch (InterruptedException e) {
                }
                ;

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    public Dealer getDealer() {
        return dealer;
    }

    public void setIsFrozen(boolean frozen) {
        isFrozen = frozen;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if (!human) {
            aiThread.interrupt();
        }
    }

    public boolean isBlocked() {
        return dealer.blockPlacing;
    }

    /**
     * This method is called when a key is pressed.
     * s
     * 
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!isFrozen && !isBlocked()) {
            actions.add(slot);
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
            // int ignored = table.countCards(); // this part is just for demonstration in
            // the unit tests
            env.ui.setScore(id, this.score);
            this.env.ui.setFreeze(this.id, FREEZE_TIME_MILLI);
            playerThread.sleep(FREEZE_TIME_MILLI);
            this.env.ui.setFreeze(this.id, NO_Time_MILLI);

        } catch (InterruptedException e) { // need to understand what to do with the exception
        }
        isFrozen = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */

    public void penalty() {
        try {
            env.ui.setFreeze(id, PENAlTY_MILLISECONDS);
            for (long frozenTime = PENAlTY_MILLISECONDS - One_Second; frozenTime >= 0; frozenTime = frozenTime - One_Second) {
                playerThread.sleep(FREEZE_TIME_MILLI);
                env.ui.setFreeze(id, frozenTime);
            }
        }

        catch (InterruptedException e) {
            return;
        }

        isFrozen = false;

    }

    public int score() {
        return score;
    }

    public Thread getDealerThread() {
        return dealer.getThread();
    }

    public Thread getPlayerThread() {
        return dealer.getPlayerThread(id);
    }

}
