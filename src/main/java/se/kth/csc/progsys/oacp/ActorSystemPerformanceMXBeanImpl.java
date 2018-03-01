package se.kth.csc.progsys.oacp;

/**
 * @author janmachacek
 */
public class ActorSystemPerformanceMXBeanImpl implements ActorSystemPerformanceMXBean{
    private ActorSystemMessages messages;

    ActorSystemPerformanceMXBeanImpl(ActorSystemMessages messages) {
        this.messages = messages;
    }

    @Override
    public int getExternalMsg() {

        return this.messages.read();
    }

}
