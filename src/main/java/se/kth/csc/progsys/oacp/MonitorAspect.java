package se.kth.csc.progsys.oacp;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

@Aspect
public class MonitorAspect {
    final ActorSystemMessages messages;

    public MonitorAspect() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        this.messages = new ActorSystemMessages();
        JMXEndpoint.start(messages);
    }

    @Pointcut(value = "execution (* akka.actor.ActorCell.receiveMessage(..)) && args(msg)", argNames = "msg")
    public void receiveMessagePointcut(Object msg) {}

    //It's not working as expected
    //@Pointcut(value = "execution (* se.kth.csc.progsys.egsp.monitor.protocol.RequestFromClient(..))")
    //public void receiveMessagePointcut(Object msg){}

    @Before(value = "receiveMessagePointcut(msg)", argNames = "jp,msg")
    public void message(JoinPoint jp, Object msg) /*throws java.io.IOException*/ {
//        if (Math.random() < 0.2) {
//            System.err.println("Simulate failure in receiving message");
//            throw new java.io.IOException("Simulated failure in receiving message");
//        }
        //
        messages.recordMessage(msg);
    }
}
