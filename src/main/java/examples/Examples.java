package examples;


import java.util.List;
import java.util.concurrent.Future;
import net.iharder.jpushbullet2.*;
/**
 *
 * @author Robert.Harder
 */
public class Examples {
    
    
    public static class GetDevices {
        public static void main(String[] args) throws PushbulletException{
            //args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                List<Device> devices = client.getDevices();
                System.out.println( "Number of devices: " + devices.size() );
                System.out.format( "%-8s\t%-22s\t%s%n", "Active","Identityt","Nickname");
                for( Device dev : devices ){
                    System.out.format( "%-8s\t%-22s\t%s%n",
                            (dev.isActive() ? "Active" : "Inactive"),
                            dev.getIden(),
                            dev.getNickname() );
                    /*System.out.println( 
                            (dev.isActive() ? "Active" : "Inactive") + "\t" +
                            dev.getIden() + "\t" +  
                            dev.getNickname() );*/
                }
            }
        }   // end main
    }   // end Devices
    
    /**
     * Not yet implemented
     */
    public static class GetContacts {
        public static void main(String[] args) throws PushbulletException{
            if( true ) throw new PushbulletException("Not yet implemented");
            args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                client.getContacts();
            }
        }   // end main
    }   // end getContacts
    
    
    
    
    
    public static class GetMe {
        public static void main(String[] args) throws PushbulletException{
            //args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                User me = client.getMe();
                System.out.println( "Current user: " + me);
                //System.out.println( " Name : " + me.getName());
                //System.out.println( " Email:" + me.getEmail_normalized() );
                //System.out.println( " Iden :" + me.getIden());
            }
        }   // end main
    }   // end GetMe
    
    
    public static class GetPushes {
        public static void main(String[] args) throws PushbulletException{
            //args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                List<Push> pushes = client.getPushes();
                System.out.println( "Number of pushes: " + pushes.size() );
            }
        }   // end main
    }   // end Devices
    
    
    public static class GetPushesPaged {
        public static void main(String[] args) throws PushbulletException, InterruptedException{
            //args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                Future<List<Push>> fut = client.getPushesAsync(0, 100, true, new Callback<List<Push>>() {
                    @Override
                    public void completed(List<Push> pushes, PushbulletException ex) {
                        System.out.println( "Number of pushes: " + ( pushes == null ? null : pushes.size() ));
                    }
                });
                while( !fut.isDone() ){
                    Thread.sleep(100);
                }
            }
        }   // end main
    }   // end Devices
    
    
    
    
    public static class GetPushesAsync {
        public static void main(String[] args) throws PushbulletException, InterruptedException{
            //args = new String[]{""};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                Future fut = client.getPushesAsync(1,false,new Callback<List<Push>>() {
                    @Override
                    public void completed(List<Push> pushes, PushbulletException ex) {
                        System.out.println( "Number of pushes: " + pushes.size() );
                        for( Push push : pushes ){
                            System.out.println( push );
                        }
                    }
                });
                Thread.sleep(1000);
                System.out.println("1 sec has passed. Done yet? " + fut.isDone() );
            }
            Thread.sleep(10000);
        }   // end main
    }   // end GetPushesAsync
    
    
    public static class SendNote {
        public static void main(String[] args) throws PushbulletException{
            if( args.length < 4 ){
                System.out.println("Arguments: API_KEY destDevIden title body");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                String result = client.sendNote(args[1], args[2], args[3] );
                System.out.println( "Result: " + result );
            }
        }   // end main
    }   // end Note
    
    
    public static class SendNoteAsync {
        public static void main(String[] args) throws Exception{
            //args = new String[]{"", "?", "body"};
            if( args.length < 3 ){
                System.out.println("Arguments: API_KEY title body");
            } else {
                System.out.println("Calling sendNoteAsync on thread " + Thread.currentThread() );
                PushbulletClient client = new PushbulletClient( args[0] );
                Future<String> fut = client.sendNoteAsync(null, args[1], args[2], new Callback<String>(){
                    @Override
                    public void completed(String result, PushbulletException ex) {
                        System.out.println( "Result complted on thread " + Thread.currentThread() );
                        System.out.println( "Result: " + result );
                        System.out.println( "Exception: " + ex );
                    }
                });
                Thread.sleep(1000);
                System.out.println("1 sec has passed. Done yet? " + fut.isDone() );
            }
            Thread.sleep(10000);
        }   // end main
    }   // end Note
    
    
    public static class SendFile {
        public static void main(String[] args) throws PushbulletException{
            //args = new String[]{"", "/tmp/temp.txt"};
            if( args.length < 2 ){
                System.out.println("Arguments: API_KEY filepath [body of push optional]");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                String result = client.sendFile(null, new java.io.File(args[1]), args.length>2 ? args[2] : null );
                System.out.println( "Result: " + result );
            }
        }   // end main
    }   // end Note
    
    
    
    public static class StartStopWebservice {
        public static void main(String[] args) throws PushbulletException, InterruptedException{
            args = new String[]{"v1WadrAI7e0zaEefOs3dVqnXtyRxWRSuZdujzlLkPSZgq"};
            if( args.length < 1 ){
                System.out.println("Arguments: API_KEY ");
            } else {
                PushbulletClient client = new PushbulletClient( args[0] );
                client.addPushbulletListener(new PushbulletListener(){

                    @Override
                    public void pushReceived(PushbulletEvent pushEvent) {
                        System.out.println("pushReceived PushEvent received: " + pushEvent);
                    }

                    @Override
                    public void devicesChanged(PushbulletEvent pushEvent) {
                        System.out.println("devicesChanged PushEvent received: " + pushEvent);
                    }

                    @Override
                    public void websocketEstablished(PushbulletEvent pushEvent) {
                        System.out.println("websocketEstablished PushEvent received: " + pushEvent);
                    }
                });
                
                System.out.println("Getting previous pushes to find most recent...");
                client.getPushes(1);
                System.out.println("Starting websocket...try sending a push now.");
                client.startWebsocket();
                
                // Wait 30 seconds
                for( int i = 30; i >= 0; i-- ){
                    System.out.print(" " + i + " ");
                    Thread.sleep(1000);
                }
                System.out.println("Stopping websocket");
                client.stopWebsocket();
            }
        }   // end main
    }   // end StartStopWebservice
    
    
    
}   // end Examples
