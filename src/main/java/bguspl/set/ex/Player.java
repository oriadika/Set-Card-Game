package bguspl.set.ex;

import java.sql.Time;
import java.util.LinkedList;
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

    private Queue<Integer> actions;

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

    private Thread dealerThread;

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
        Thread plThread = new Thread(this);
        plThread.start();
        this.actions = new LinkedList<>();
        this.dealer = dealer;
        this.dealerThread = dealer.getThread();
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    public void run() {
        this.playerThread = Thread.currentThread();
        this.env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!this.human) {
            this.createArtificialIntelligence();
        }

        while (!this.terminate) {
            synchronized (actions) {
                while (actions.size() == 0) { // no action preformed yet
                    try {
                        actions.wait();
                    } catch (InterruptedException e) {

                    }
                }
            }
        }

        if (!this.human) {
            try {
                this.aiThread.join();
            } catch (InterruptedException var2) {
            }
        }

        this.env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private synchronized void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                synchronized(actions){
                    while (actions.size()==0){
                        try{
                            actions.wait();
                        }
                        catch(InterruptedException e){}
                    }

                }
                // TODO implement player key press simulator
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
     * s
     * 
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (table.slotToCard[slot] != null & actions.size() < 3 | (actions.contains(slot) & actions.size() == 3)) {
            java.util.Iterator<Integer> iterator = this.actions.iterator();
            boolean placeToken = true;
            while (iterator.hasNext()) {
                int slotOfExistToken = iterator.next();
                if (slotOfExistToken == slot) {// It means there is already a token on the slot, so the player wants to
                    // remove the token
                    placeToken = false;
                    table.removeToken(id, slot); // call the table to remove the token
                    removeAction(slotOfExistToken);
                    break;
                }

            }

            if (placeToken & actions.size() < 3) { // the player wants to put a new token
                synchronized (table.slotsLocks[slot]) { // prevent from a player to place token on empty slot
                    table.placeToken(id, slot); // checks if there is max of 3 or it happends by deafult?
                    addAction(slot);
                }
            }
        }

        if (this.actions.size() == 3 & actions.contains(slot)) { // just put his third token
            dealerThread.interrupt(); // to make him check the cuurent set
            try {
                playerThread.wait(); // waits for the dealer to check my cards

            } catch (InterruptedException e) { // the dealer should remove my tokens in
                // case of set

            }
        }
    }

    public void addAction(int slot){
        while (actions.size()==3 || !actions.contains(slot)){
            synchronized(actions){
                try{
                    actions.wait();
                }
                catch(InterruptedException e){
                    actions.add(slot);
                    actions.notifyAll();
                }
            }
        }
    }

    public void removeAction(int slot){
        while (actions.size()==0 || !actions.contains(slot)){
            synchronized(actions){
                try{
                    actions.wait();
                }
                catch(InterruptedException e){
                    actions.remove(slot);
                    notifyAll();
                }
            }
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
            this.env.ui.setFreeze(this.id, FREEZE_TIME_MILLI);
            playerThread.sleep(FREEZE_TIME_MILLI);

        } catch (InterruptedException e) { // need to understand what to do with the exception

        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        try {
            this.env.ui.setFreeze(id, PENAlTY_MILLISECONDS);
            playerThread.sleep(PENAlTY_MILLISECONDS);

        } catch (InterruptedException e) { // need to understand what to do with the exception
        }
    }

    public int score() {
        return score;
    }

    public Queue<Integer> getActions() {
        return actions;
    }

}
