package jenkins.security;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.CallableDecorator;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejects {@link MasterToSlave} callables.
 *
 * @author Kohsuke Kawaguchi
 */
public class CallableDirectionChecker extends CallableDecorator {
    private final SlaveComputer computer;

    public CallableDirectionChecker(SlaveComputer computer) {
        this.computer = computer;
    }

    @Override
    public <V, T extends Throwable> Callable<V, T> userRequest(Callable<V, T> op, Callable<V, T> stem) {
        Class c = op.getClass();

        boolean m2s = c.isAnnotationPresent(MasterToSlave.class);
        boolean s2m = c.isAnnotationPresent(SlaveToMaster.class);

        if (!m2s && !s2m) {
            // no annotation provided, so we don't know.
            // to err on the correctness we'd let it pass with reporting, which
            // provides auditing trail.
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.fine("Unchecked callable from "+computer.getName()+": "+c);

            return stem;
        }

        if (s2m)
            return stem;    // known to be safe

        // this means m2s && !s2m, meaning it's for master->slave
        throw new SecurityException(String.format("Invocation of %s is prohibited", c));
    }

    /**
     * Installs {@link CallableDirectionChecker} to every channel.
     */
    @Extension
    public static class ComputerListenerImpl extends ComputerListener {
        @Override
        public void onChannelBuilding(ChannelBuilder builder, SlaveComputer sc) {
            builder.with(new CallableDirectionChecker(sc));
        }

        @Override
        public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
            super.preLaunch(c, taskListener);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
