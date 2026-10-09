package me.leleawa.rotatedworld.kernel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在共享线程池上按提交顺序逐个执行任务（每个玩家一个，保证发包顺序）。 */
public final class SerialExecutor implements Executor {
    private final Executor backing;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SerialExecutor(Executor backing) {
        this.backing = backing;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (!running.compareAndSet(false, true)) return;
        backing.execute(() -> {
            try {
                Runnable t;
                while ((t = tasks.poll()) != null) {
                    try {
                        t.run();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                running.set(false);
                if (!tasks.isEmpty()) schedule();
            }
        });
    }
}
