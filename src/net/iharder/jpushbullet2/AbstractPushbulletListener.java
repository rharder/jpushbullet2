package net.iharder.jpushbullet2;


/**
 *
 * @author Robert.Harder
 */
public class AbstractPushbulletListener implements PushbulletListener {

    @Override
    public void pushReceived(PushbulletEvent pushEvent) {}
    
    @Override
    public void devicesChanged( PushbulletEvent pushEvent ){}

    @Override
    public void websocketEstablished(PushbulletEvent pushEvent) {}

    
}
