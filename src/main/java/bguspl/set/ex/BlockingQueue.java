
    package bguspl.set.ex;

    import java.util.LinkedList;
    import java.util.Queue;

    public class BlockingQueue {

        private final int maxSize = 3;

        private Queue<Integer> actions;

        public BlockingQueue() {

            this.actions = new LinkedList<>();

        }

        public synchronized void addAction(int slot) {
            try {
                while (actions.size() == maxSize) {
                    wait();

                }
                actions.add(slot);
                notifyAll();
            } catch (InterruptedException e) {

            }

        }

        public synchronized int removeAction() throws InterruptedException {
            try {
                while (actions.size() == 0) {
                    wait();
                }
                int slot = actions.remove();
                notifyAll();
                return slot;
            } catch (InterruptedException e) {
                throw e;
            }
        }
    }
