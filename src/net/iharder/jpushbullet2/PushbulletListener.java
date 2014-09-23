package net.iharder.jpushbullet2;


import java.util.EventListener;

/**
 *
 * @author Robert.Harder
 */
public interface PushbulletListener extends EventListener {

    /**
     * Called when a new push has been received. The new pushes
     * can be retrieved from the {@link PushbulletEvent} passed
     * as an argument.
     * @param pushEvent The event related to the new pushes 
     */
    public abstract void pushReceived(PushbulletEvent pushEvent);
    
    
    /**
     * Called when changes have been detected in the devices
     * associated with the Pushbullet account.
     * @param pushEvent The event related to the change 
     */
    public abstract void devicesChanged( PushbulletEvent pushEvent );
    
    //public abstract void websocketClosed(PushbulletEvent pushEvent);

    public abstract void websocketEstablished(PushbulletEvent pushEvent);
    
}
