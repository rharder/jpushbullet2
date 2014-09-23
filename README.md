jpushbullet2
============

http://iharder.net/jpushbullet2

This is a Public Domain Java library for interacting with the Pushbullet service. It supports both sending and receiving notifications. Thanks to shakethat at github for the insights from his library, which was made for an earlier set of Pushbullet APIs.

The Pushbullet HTTP API identifies seven capabilities, of which jPushbullet2 supports five:

Pushbullet API	Supported in 
jPushbullet2	Future Plans
/v2/pushes	Yes	
/v2/devices	Yes	
/v2/contacts	No	Will explore this later
/v2/users/me	Yes	
/v2/upload-request	Yes	
/v2/oauth2	No	None
websocket	Yes	

Examples

The quickest way to understand the library is to see some examples. These examples and more are included in the source code. For security reasons, the examples are not included in the binary distribution jar file.

To send a note:

public class SendNote {
    public static void main(String[] args) throws PushbulletException{
        PushbulletClient client = new PushbulletClient( "ABCD1034...your.key.here...ABCD" );
        String result = client.sendNote(null, "My First Push", "Great library. All my devices can see this!");
        System.out.println( "Result: " + result );
    }   // end main
}   // end Note
To retrieve, on another thread, all pushes ever sent with this Pushbullet account:

public class GetPushesAsync {
    public static void main(String[] args) throws PushbulletException, InterruptedException{
        PushbulletClient client = new PushbulletClient( "ABCD1034...your.key.here...ABCD" );
        Future fut = client.getPushesAsync(new Callback<List<Push>>() {
            @Override
            public void completed(List pushes, PushbulletException ex) {
                System.out.println( "Number of pushes: " + pushes.size() );
                System.out.println("Destination\ttype");
                for( Push push : pushes ){
                    System.out.println( 
                            push.getTarget_device_iden()+ "\t" + 
                            push.getType() );
                }
            }
        });
        Thread.sleep(1000);
        System.out.println("1 sec has passed. Done yet? " + fut.isDone() );
        Thread.sleep(10000);
    }   // end main
}   // end GetPushesAsync
To listen for new incoming pushes and other changes to your Pushbullet account:

public class StartStopWebservice {
    public static void main(String[] args) throws PushbulletException, InterruptedException{
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
            });
            
            System.out.println("Getting previous pushes to find most recent...");
            client.getPushes();
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
A Note About Public Domain

I have released this software into the Public Domain. That means you can do whatever you want with it. Really. You don't have to match it up with any other open source license &em; just use it. You can rename the files, move the Java packages, whatever you want. If your lawyers say you have to have a license, contact me, and I'll make a special release to you under whatever reasonable license you desire: MIT, BSD, GPL, whatever.

Changes

v0.2 - Adding paging capability. Changed Push sorting to be newest first in keeping with Pushbullet.com practice. Cleaned up some dependencies.
v0.1 - Initial Release
