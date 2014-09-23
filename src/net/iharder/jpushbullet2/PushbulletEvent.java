package net.iharder.jpushbullet2;


import java.util.EventObject;
import java.util.List;

/**
 *
 * @author Robert.Harder
 */
public class PushbulletEvent extends EventObject{
    private List<Push> pushes;

    public PushbulletEvent(PushbulletClient aThis) {
        super(aThis);
    }
    
    public PushbulletEvent(PushbulletClient aThis, List<Push> pushes) {
        super(aThis);
        this.pushes = pushes;
    }
    
    
    
    public PushbulletClient getPushbulletClient(){
        return (PushbulletClient) getSource();
    }
    
    
    public List<Push> getPushes(){
        return pushes;
    }
    
}
