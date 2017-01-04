package net.iharder.jpushbullet2;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import javax.activation.MimetypesFileTypeMap;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/**
 * Access the Pushbullet (version 2) API including receiving updates via
 * websockets.
 *
 * Usage example:
 * <pre><code>
 * PushbulletClient pbClient = new PushbulletClient("AFC1334...API Key...958DF");
 * try{
 *     pbClient.sendNote( "A34...device iden...98C", "My Title", "My Body" );
 * } catch( PushbulletException e ){
 *     // Would indicate a problem
 * }
 * </code></pre>
 *
 * @author Robert Harder
 * @author rob@iharder.net
 * @version 0.2
 */
public class PushbulletClient {

    //private static final Logger LOGGER = LoggerFactory.getLogger(PushbulletClient.class);
    private final Log LOGGER = LogFactory.getLog(getClass());

    /**
     * User's Pushbullet key, until I can figure out OAuth.
     */
    private String apiKey;

    /**
     * Express intention of user. The {@link KeepAliveTask} reads this and
     * either keeps the websocket going or kills it.
     */
    private boolean websocketShouldBeRunning = false;

    private long websocketPulseInterval = 10000;

    /**
     * When keeping track of ongoing pushes using the websocket.
     */
    private double mostRecentPushTimestamp = 0;//Preferences.userNodeForPackage(this.getClass()).getDouble("mostRecentPushTimestamp", 0);

    private final List<PushbulletListener> listenerList;
    //private final Set<PushbulletListener> listenerList;
    private ExecutorService asyncExecutor;

    private final static String API_URL = "https://api.pushbullet.com";
    private final static String API_PUSHES_URL = API_URL + "/v2/pushes";
    private final static String API_DEVICES_URL = API_URL + "/v2/devices";
    private final static String API_CONTACTS_URL = API_URL + "/v2/contacts";
    private final static String API_UPLOAD_REQUEST_URL = API_URL + "/v2/upload-request";
    private final static String API_USERS_ME_URL = API_URL + "/v2/users/me";
    private final static String WEBSOCKET_URL = "wss://stream.pushbullet.com/websocket";
    private final static String OAUTH_URL = "https://api.pushbullet.com/oauth2";
    private final static String API_CRED_SCOPE = "api.pushbullet.com";

    /**
     * Apache HTTP commons stuff.
     */
    private final CredentialsProvider credsProvider;
    private CloseableHttpClient httpClient;

    /**
     * Web socket stuff (I used Tyrus).
     */
    private ClientEndpointConfig websocketClientEndpointConfig;
    private WebSocketContainer websocketClient;
    private Session websocketSession;
    private Timer checkPulse;

    /**
     * Handles common instantiation items.
     */
    public PushbulletClient() {
        this.listenerList = new LinkedList<PushbulletListener>();
        this.websocketClientEndpointConfig = null;
        this.credsProvider = new BasicCredentialsProvider();
        this.httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
    }

    /**
     * Create instances of the http httpClient and other needed things. No
     * logging done with this constructor
     *
     * @param api_key The only credential to be passed. Acts as user/password
     */
    public PushbulletClient(String api_key) {
        this();
        this.setApiKey(api_key);
    }

    /**
     * Sets the key used to access a Pushbullet account. Will restart the
     * websocket if it is running.
     *
     * @param key Sets the key used to access a pushbullet account
     */
    public void setApiKey(String key) {
        this.apiKey = key;
        credsProvider.setCredentials(new AuthScope(API_CRED_SCOPE, 443), new UsernamePasswordCredentials(key, null));
        try {
            httpClient.close();
        } catch (Exception e) {
            // Whether NullPointerException or an IO error, just ignore it.
        }
        httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        synchronized (this) {
            if (this.websocketShouldBeRunning) {
                try {
                    this.websocketSession.close();
                } catch (Exception ex) {
                    // Not expecting any trouble, even if there's an exception
                }
            }
        }   // end sync
    }

    public String getApiKey() {
        return this.apiKey;
    }

    /* ********   W E B S O C K E T   ******** */
    /**
     * Begin websocket listening. Not necessary if you're only sending
     * notificaitons. Future enhancements: be able to turn this off as well.
     * Once you init this, the socket will be kept alive across network outages
     * by periodically checking the connection and then reconnecting.
     */
    public synchronized void startWebsocket() {
        websocketShouldBeRunning = true;
        if (checkPulse == null) {
            checkPulse = new Timer("Websocket-Pulse-Check");
        }
        checkPulse.schedule(new KeepAliveTask(), 1);
    }

    /**
     * Stops the websocket that would be listening for changes to the Pushbullet
     * account.
     */
    public synchronized void stopWebsocket() {
        websocketShouldBeRunning = false;
        if (checkPulse != null) {
            checkPulse.cancel();
            checkPulse = null;
        }
        try {
            if (websocketSession != null) {
                websocketSession.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "User stopped the service"));
            }
        } catch (IOException ex) {
            LOGGER.error("while closing websocketSession", ex);
        } finally {
            websocketSession = null;
        }
    }

    /**
     * Initialize the websocket.
     */
    private synchronized void initWebsocket() {
        if (websocketSession != null && websocketSession.isOpen()) {
            LOGGER.debug("initWebsocket called when session was already open");
            return;
        }

        // Lazily create timer if we're using a websocket
        try {
            // Lazily create
            if (websocketClientEndpointConfig == null) {
                websocketClientEndpointConfig = ClientEndpointConfig.Builder.create().build();
            }
            if (websocketClient == null) {
                //websocketClient = ClientManager.createClient();
                websocketClient = ContainerProvider.getWebSocketContainer();
            }
            websocketSession = websocketClient.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            handleOnWebSocketMessage(message);
                        }
                    });
                    LOGGER.info("Websocket session established.");
                } // end onOpen

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Websocket session closed: " + closeReason.getReasonPhrase());
                    }
                    // Timer will detect and correct
                }   // end onClose

                @Override
                public void onError(Session session, Throwable thr) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Websocket session error: " + thr.getLocalizedMessage(), thr);
                    }
                }   // end onClose
            }, websocketClientEndpointConfig, new URI(WEBSOCKET_URL + "/" + apiKey));
            // SUCCESS!
            fireWebsocketEstablishedEvent();
        } // end try
        catch (DeploymentException ex) {
            LOGGER.error("Error connecting to Pushbullet websocket: " + ex.getMessage());
            websocketSession = null;
        } catch (IOException ex) {
            LOGGER.error("Error connecting to Pushbullet websocket: " + ex.getMessage());
            websocketSession = null;
        } catch (URISyntaxException ex) {
            LOGGER.error("Error connecting to Pushbullet websocket: " + ex.getMessage());
            websocketSession = null;
        } finally {
        }
    }   // end startWebsocket

    /**
     * Internal method to handle when the websocket has some traffic.
     *
     * @param msg The incoming websocket data
     */
    private void handleOnWebSocketMessage(String msg) {
        StreamMessage smsg = null;
        try {
            smsg = JsonHelper.fromJson(msg, StreamMessage.class);
        } catch (PushbulletException ex) {
            LOGGER.error("", ex);
            return;
        }

        if (StreamMessage.TICKLE_TYPE.equals(smsg.type)) {
            if (StreamMessage.PUSH_SUBTYPE.equals(smsg.subtype)) {
                List<Push> pushes;
                try {
                    pushes = getNewPushes();
                    if (!pushes.isEmpty()) {
                        firePushReceivedEvent(pushes);
                    }
                } catch (PushbulletException ex) {
                    LOGGER.error("Error getting pushes: " + ex.getMessage());
                }
            } // end if: push
            else if (StreamMessage.DEVICE_SUBTYPE.equals(smsg.subtype)) {
                fireDevicesChangedEvent();
            }   // end if: device
        }   // end if: tickle
    }   // end handleOnWebSocketMessage

    /* ********   E V E N T S   ******** */
    /**
     * Add a listener to be notified of various changes.
     *
     * @param l The listener
     */
    public void addPushbulletListener(PushbulletListener l) {
        listenerList.add(l);
    }

    /**
     * Removes a listener to be no longer notified of various changes.
     *
     * @param l The listener
     */
    public void removePushBulletListener(PushbulletListener l) {
        listenerList.remove(l);
    }

    /**
     * Helper method to fire an event when new pushes come in as notified by the
     * websocket.
     *
     * @param pushes The pushes received
     */
    protected void firePushReceivedEvent(List<Push> pushes) {
        PushbulletEvent pushEvent = null;
        // Guaranteed to return a non-null array
        PushbulletListener[] listeners = listenerList.toArray(new PushbulletListener[0]);
        for (PushbulletListener l : listeners) {
            if (pushEvent == null) {
                pushEvent = new PushbulletEvent(this, pushes);
            }
            l.pushReceived(pushEvent);
        }
    }

    /**
     * Helper method to fire an event when something about the devices has
     * changed, as notified by the websocket.
     */
    protected void fireDevicesChangedEvent() {
        PushbulletEvent pushEvent = null;
        // Guaranteed to return a non-null array
        PushbulletListener[] listeners = listenerList.toArray(new PushbulletListener[0]);
        for (PushbulletListener l : listeners) {
            if (pushEvent == null) {
                pushEvent = new PushbulletEvent(this);
            }
            l.devicesChanged(pushEvent);
        }
    }

    protected void fireWebsocketEstablishedEvent() {
        PushbulletEvent pushEvent = null;
        // Guaranteed to return a non-null array
        PushbulletListener[] listeners = listenerList.toArray(new PushbulletListener[0]);
        for (PushbulletListener l : listeners) {
            if (pushEvent == null) {
                pushEvent = new PushbulletEvent(this);
            }
            l.websocketEstablished(pushEvent);
        }
    }

    /* ********   D E V I C E S   ******** */
    /**
     * Creates a device with the given nickname and returns the {@link Device}
     * created.
     *
     * @param nickname The device nickname
     * @return The newly created Device
     * @throws PushbulletException if there is a communication or other error
     */
    public Device createDevice(String nickname) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("nickname", nickname));
        nameValuePairs.add(new BasicNameValuePair("type", "stream"));
        String result = doHttpPost(API_DEVICES_URL, nameValuePairs);
        return JsonHelper.fromJson(result, Device.class);
    }   // end createDevice

    /**
     * Deletes the given device. Pushbullet actually just marks the device
     * inactive.
     *
     * @param deviceIden The device identifier to delete
     * @return The HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String deleteDevice(String deviceIden) throws PushbulletException {
        HttpDelete delete = new HttpDelete(API_DEVICES_URL + "/" + deviceIden);
        String result = doHttp(delete);
        return result;
    }

    /**
     * Parse all the devices available. This is needed if you want to use it to
     * send any data.
     *
     * @return PushbulletDevice, a class holding all the devices.
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Device> getDevices() throws PushbulletException {
        String devResult = doHttpGet(API_DEVICES_URL);

        DeviceList devList = JsonHelper.fromJson(devResult, DeviceList.class);
        if (devList == null || devList.devices == null) {
            throw new PushbulletException("Unknown problem with response from Pushbullet: " + devResult);
        }

        return devList.devices;
    }

    /**
     * Same as {@link #getDevices} but executes on another thread.
     *
     * @param callback optional {@link Callback} to be notified when finished
     * @return a java Future object related to work to be completed
     */
    public Future<List<Device>> getDevicesAsync(Callback<List<Device>> callback) {
        return doAsync(new Callable<List<Device>>() {
            @Override
            public List<Device> call() throws Exception {
                return getDevices();
            }
        }, callback);
    }

    /**
     * Returns all the <b>active</b> devices in your account.
     *
     * @return a non-null list of {@link Device} objects
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Device> getActiveDevices() throws PushbulletException {
        List<Device> devices = getDevices();
        List<Device> activeDevices = new ArrayList<Device>(devices.size());
        for (Device d : devices) {
            if (d.isActive()) {
                activeDevices.add(d);
            }
        }
        //Collections.sort(activeDevices);
        return activeDevices;
    }

    /**
     * Same as {@link #getActiveDevices} but executes on another thread.
     *
     * @param callback optional {@link Callback} to be notified when finished
     * @return a java Future object related to work to be completed
     */
    public Future<List<Device>> getActiveDevicesAsync(Callback<List<Device>> callback) {
        return doAsync(new Callable<List<Device>>() {
            @Override
            public List<Device> call() throws Exception {
                return getActiveDevices();
            }
        }, callback);
    }

    /* ********   C O N T A C T S   ******** */
    /**
     * Not yet implemented.
     *
     * @return not yet implemented
     * @throws PushbulletException not yet implemented
     */
    public List<String> getContacts() throws PushbulletException {
        if (true) {
            throw new PushbulletException("Contact support not yet implemented.");
        }

        String conResult = doHttpGet(API_CONTACTS_URL);

        return null;
    }

    /* ********   U S E R S   /   M E   ******** */
    /**
     * Returns info about the current user, that is, the user whose API key is
     * being used.
     *
     * @return info about the user
     * @throws PushbulletException if there is a communication or other error
     */
    public User getMe() throws PushbulletException {
        String result = doHttpGet(API_USERS_ME_URL);
        User me = JsonHelper.fromJson(result, User.class);
        return me;
    }

    /**
     * Same as {@link #getMe} but executes on another thread.
     *
     * @param callback optional {@link Callback} to be notified when finished
     * @return a java Future object related to work to be completed
     */
    public Future<User> getMeAsync(Callback<User> callback) {
        return doAsync(new Callable<User>() {
            @Override
            public User call() throws Exception {
                return getMe();
            }
        }, callback);
    }

    /* ********   G E T T I N G   P U S H E S   ******** */
    /**
     * Sets the timestamp that is used as a filter when retrieving "new" pushes.
     * This will be updated automatically whenever one of the {@link #getPushes}
     * methods is called, but you can override it here.
     *
     * @param mostRecentPushTimestamp the timestamp associated with the
     * {@link #getNewPushes} method
     */
    private void setMostRecentPushTimestamp(double mostRecentPushTimestamp) {
        this.mostRecentPushTimestamp = mostRecentPushTimestamp;
    }

    /**
     * Returns the timestamp that is used as a filter when retrieving "new"
     * pushes.
     *
     * @return the most recent push timestamp
     */
    public double getMostRecentPushTimestamp() {
        return this.mostRecentPushTimestamp;
    }

    /**
     * <p>
     * Returns pushes that have been posted to Pushbullet since the last call to
     * {@link #getPushes}. This results in less network traffic for accounts
     * with hundreds of pushes in their history.</p>
     * <p>
     * If you are restarting an app, you might want to either have already saved
     * knowledge of the most recent timestamp or else call {@link #getPushes}
     * with a limit of 1 to retrieve the most recent push and have
     * PushbulletClient save the timestamp.</p>
     * <p>
     * This method is the same as calling another <tt>getPushes</tt> method with
     * {@link #getMostRecentPushTimestamp()} as the timestamp.</p>
     *
     * @return a non-null list of {@link Push} objects
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Push> getNewPushes() throws PushbulletException {
        return getPushes(getMostRecentPushTimestamp(), 0);
    }

    /**
     * Same as {@link #getNewPushes} except that no more than <tt>limit</tt>
     * pushes will be returned. Since Pushbullet sends the most recent pushes
     * first, setting a limit of one will return the single most recent push.
     * Setting the limit to zero is the same as not setting a limit at all.
     *
     * @param limit the max number of pushes to return
     * @return the non-null list of pushes
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Push> getNewPushes(int limit) throws PushbulletException {
        return getPushes(getMostRecentPushTimestamp(), limit);
    }

    /**
     * Fetch new pushes (according to the highest timestamp seen so far) but on
     * another thread.
     *
     * @param callback optional {@link Callback} to notify when complete
     * @return a Java Future object relating to work being completed
     */
    public Future<List<Push>> getNewPushesAsync(Callback<List<Push>> callback) {
        return getNewPushesAsync(0, callback);
    }

    /**
     * Fetch up to <tt>limit</tt> new pushes (according to the highest timestamp
     * seen so far) but on another thread.
     *
     * @param limit max number of pushes
     * @param callback optional {@link Callback} to notify when complete
     * @return a Java Future object relating to work being completed
     */
    public Future<List<Push>> getNewPushesAsync(final int limit, Callback<List<Push>> callback) {
        return doAsync(new Callable<List<Push>>() {
            @Override
            public List<Push> call() throws Exception {
                return getNewPushes(limit);
            }
        }, callback);
    }

    /**
     * Returns a list of all pushes since the beginning of your Pushbullet
     * account. Also remembers what the most recent push timestamp is in order
     * to support the {@link #getNewPushes} method.
     *
     * @return a non-null list of {@link Push} objects
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Push> getPushes() throws PushbulletException {
        return getPushes(0.0, 0);
    }

    /**
     * Fetch up to <tt>limit</tt> pushes.
     *
     * @param limit max number of pushes
     * @return non-null list of pushes
     * @throws PushbulletException up communication or other error
     */
    public List<Push> getPushes(int limit) throws PushbulletException {
        return getPushes(0.0, limit);
    }

    /**
     * Returns all pushes but makes the call on another thread. You can access
     * the result either by submitting a {@link Callback} object or by using
     * Java's Future object.
     *
     * @param callback optional {@link Callback} to notify when completed
     * @return a Java Future object referring to the future work
     */
    public Future<List<Push>> getPushesAsync(Callback<List<Push>> callback) {
        return getPushesAsync(0, false, callback);
    }

    /**
     * Fetch pushes but on a separate thread. See
     * {@link #getPushesAsync(double, int, boolean, Callback)} for details about
     * paging.
     *
     * @param limit max number of pushes to return
     * @param allowPaging return pushes in pages
     * @param callback optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<List<Push>> getPushesAsync(final int limit, boolean allowPaging, Callback<List<Push>> callback) {
        return getPushesAsync(0.0, limit, allowPaging, callback);
        /*        return doAsync( new Callable(){
         @Override
         public Object call() throws Exception {
         return getPushes(limit);
         }
         }, callback ); */
    }

    /**
     * Returns pushes after a given timestamp (or zero for all) but no more than
     * <tt>limit</tt> amount, or zero for no limit. If no limit is specified
     * (zero), and Pushbullet.com returns a paged set of pushes (their default
     * is 500 pushes per page), then this will continue retrieving all the
     * pushes until there are no more. If you desire greater control over
     * receiving large sets of pushes, consider using one of the methods that
     * supports the <tt>allowPaging</tt> flag.
     *
     * @param modifiedAfter the timestamp after which pushes will be retrieved
     * @param limit max number of pushes to return (zero indicates no limit)
     * @return a non-null list of {@link Push} objects
     * @throws PushbulletException if there is a communication or other error
     */
    public List<Push> getPushes(double modifiedAfter, int limit) throws PushbulletException {
        // Retrieve pushes
        PushList pushList = getPushList(modifiedAfter, limit, null);
        if (pushList.pushes == null) {
            throw new PushbulletException("Unknown problem retrieving pushes. Push list is null.");
        }

        // If limit=0 and we have pages, get all the pages 
        if (limit == 0 && pushList.cursor != null) {
            List<Push> cumulative = new ArrayList<Push>(500);
            cumulative.addAll(pushList.pushes);
            String cursor = pushList.cursor;
            while (cursor != null) {
                PushList temp = getPushList(modifiedAfter, limit, cursor);
                cumulative.addAll(temp.pushes);
                cursor = temp.cursor;
            }
            pushList.pushes = cumulative;
        }

        return pushList.pushes;
    }

    /**
     * Fetches pushes on another thread and optionally returns them in pages
     * according to the size of <tt>limit</tt> or the Pushbullet.com default of
     * 500.
     *
     * <p>
     * If not paging, the {@link Callback} object will be notified when the
     * pushes are retrieved (up to <tt>limit</tt> in number). The returned Java
     * Future object will also return the list of pushes upon completion.</p>
     *
     * <p>
     * If paging, the {@link Callback} object will have its
     * {@link Callback#completed} method called repeatedly until no more pushes
     * remain, at which point the resulting <tt>List</tt> passed to the callback
     * method will be null. The returned Java Future object therefore will not
     * have access to the list of pushes but can be used as an additional way to
     * test for when the entire list of pushes are retrieved.</p>
     *
     * <pre><code>
     * PushbulletClient client = new PushbulletClient( "AFC1334...API Key...958DF" );
     * Future&lt;List&lt;Push&gt;&gt; fut = client.getPushesAsync(null, 0, 100, true, new Callback&lt;List&lt;Push&gt;&gt;() {
     * public void completed(List&lt;Push&gt; pushes, PushbulletException ex) {
     * System.out.println( "Number of pushes: " + ( pushes == null ? null : pushes.size() ));
     * }
     * });
     * while( !fut.isDone() ){
     * Thread.sleep(100);
     * }
     * // Work is done now
     * </code></pre>
     *
     * @param modifiedAfter the timestamp after which pushes will be retrieved
     * @param limit max number of pushes to return (zero indicates no limit)
     * @param allowPaging continue retrieving <tt>limit</tt> at a time until
     * done
     * @param callback to notify when pushes arrive
     * @return Java Future object alerting when task is complete
     * @since 0.2
     */
    public Future<List<Push>> getPushesAsync(
            final double modifiedAfter, final int limit,
            final boolean allowPaging, final Callback<List<Push>> callback) {

        if (allowPaging) {
            return doAsync(new Callable<List<Push>>() {
                @Override
                public List<Push> call() throws Exception {
                    // Retreive pushes, notifying after each page
                    PushList pushList = null;
                    // Initial retrieval
                    try {
                        pushList = getPushList(modifiedAfter, limit, null);
                        callback.completed(pushList.pushes, null);
                    } catch (PushbulletException e) {
                        callback.completed(null, e);
                        throw e;
                    }
                    // Repeat until all pages retrieved
                    while (pushList.cursor != null) {
                        try {
                            pushList = getPushList(modifiedAfter, limit, pushList.cursor);
                            callback.completed(pushList.pushes, null);
                        } catch (PushbulletException e) {
                            callback.completed(null, e);
                            throw e;
                        }
                    }
                    return null;
                }
            }, callback);
        } else {
            return doAsync(new Callable<List<Push>>() {
                @Override
                public List<Push> call() throws Exception {
                    return getPushes(modifiedAfter, limit);
                }
            }, callback);
        }
    }

    /**
     * Used internally to retrieve pushes including some metadata like the
     * cursor.
     *
     * @param devIden the identify of the device (optional)
     * @param modifiedAfter the timestamp after which pushes will be retrieved
     * @param limit max number of pushes to return (zero indicates no limit)
     * @return a non-null PushList object
     * @throws PushbulletException if there is a communication or other error
     */
    private PushList getPushList(double modifiedAfter, int limit, String cursor) throws PushbulletException {

        // Build the GET string
        StringBuilder get = new StringBuilder(API_PUSHES_URL + "?modified_after=" + modifiedAfter);
        if (limit > 0) {
            get.append("&limit=").append(limit);
        }
        if (cursor != null) {
            get.append("&cursor=").append(cursor);
        }

        // Make request to Pushbullet.com
        String result = doHttp(new HttpGet(get.toString()));
        PushList pushlist = JsonHelper.fromJson(result, PushList.class);
        if (pushlist == null || pushlist.pushes == null) {
            throw new PushbulletException("Unknown problem with response from Pushbullet: " + result);
        }

        // Remember the most recent push, if it's newer than what we know about
        if (!pushlist.pushes.isEmpty()) {
            double mod = pushlist.pushes.get(0).getModified();
            if (mod > getMostRecentPushTimestamp()) {
                setMostRecentPushTimestamp(mod);
            }
        }
        return pushlist;
    }   // end getPushList

    /* ********   S E N D I N G   P U S H E S   ******** */
    /**
     * Send a note
     *
     *
     * <pre><code>
     * PushbulletClient pbClient = new PushbulletClient("AFC1334...API Key...958DF");
     * try{
     *     pbClient.sendNote( "A34...device iden...98C", "My Title", "My Body" );
     * } catch( PushbulletException e ){
     *     // Would indicate a problem
     * }
     * </code></pre>
     *
     * @param iden The device identification code
     * @param title Title of the note
     * @param body Body text of the note
     * @return resulting json from the api
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendNote(String iden, String title, String body) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "note"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("body", body));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Send a note with custom source device identification code
     *
     * @param iden The target device identification code
     * @param title Title of the note
     * @param body Body text of the note
     * @param source_iden The source device identification code
     * @return resulting json from the api
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendNote(String iden, String title, String body, String source_iden) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "note"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("body", body));
        nameValuePairs.add(new BasicNameValuePair("source_device_iden", source_iden));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a note to the specified Pushbullet device on a separate thread.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param body the note to send
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendNoteAsync(final String iden, final String title, final String body, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendNote(iden, title, body);
            }
        }, async);
    }   // end sendNoteAsync

    /**
     * Sends a note to the specified Pushbullet device on a separate thread with
     * custom source device identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param body the note to send
     * @param source_iden The source device identification code
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendNoteAsync(final String iden, final String title, final String body, final String source_iden, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendNote(iden, title, body, source_iden);
            }
        }, async);
    }   // end sendNoteAsync

    /**
     * Sends a link to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param url the link to send
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendLink(String iden, String title, String url) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "link"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("url", url));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a link to the specified Pushbullet device with custom source device
     * identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param url the link to send
     * @param source_iden The source device identification code
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendLink(String iden, String title, String url, String source_iden) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "link"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("url", url));
        nameValuePairs.add(new BasicNameValuePair("source_device_iden", source_iden));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /*
    
     * Sends a link on a separate thread and optionally alerts {@link Callback#completed}
     * when the task is completed. 
     * 
     * 
     * <code>
     *  PushbulletClient pbClient = new PushbulletClient("AFC1334...API Key...958DF");
     *  pbClient.sendLinkAsync( "A34...device iden...98C", "My Title", "http://.....", new Callback(){
     *          public void completed( String result, PushbulletException ex ){
     *              System.out.println(result);
     *          }   // end completed
     *     );
     * </code>
     */
    /**
     * Sends a link to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param url the link to send
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendLinkAsync(final String iden, final String title, final String url, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendLink(iden, title, url);
            }
        }, async);
    }   // end sendLinkAsync

    /**
     * Sends a link to the specified Pushbullet device with custom source device
     * identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the link
     * @param url the link to send
     * @param source_iden The source device identification code
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendLinkAsync(final String iden, final String title, final String url, final String source_iden, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendLink(iden, title, url, source_iden);
            }
        }, async);
    }   // end sendLinkAsync

    /**
     * Sends a list to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendList(String iden, String title, List<String> list) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "list"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        for (String s : list) {
            nameValuePairs.add(new BasicNameValuePair("items", s));
        }
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a list to the specified Pushbullet device with custom source device
     * identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @param source_iden The source device identification code
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendList(String iden, String title, String source_iden, List<String> list) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "list"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("source_device_iden", source_iden));
        for (String s : list) {
            nameValuePairs.add(new BasicNameValuePair("items", s));
        }
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a list to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendList(String iden, String title, String... list) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "list"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        for (String s : list) {
            nameValuePairs.add(new BasicNameValuePair("items", s));
        }
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a list to the specified Pushbullet device with custom source device
     * identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @param source_iden The source device identification code
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendList(String iden, String title, String source_iden, String... list) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "list"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("title", title));
        nameValuePairs.add(new BasicNameValuePair("source_device_iden", source_iden));
        for (String s : list) {
            nameValuePairs.add(new BasicNameValuePair("items", s));
        }
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends a list to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendListAsync(final String iden, final String title, final List<String> list, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendList(iden, title, list);
            }
        }, async);
    }   // end sendNoteAsync

    /**
     * Sends a list to the specified Pushbullet device with custom source device
     * identification code.
     *
     * @param iden the Pushbullet device
     * @param title title to accompany the list
     * @param list items to include in the list
     * @param source_iden The source device identification code
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendListAsync(final String iden, final String title, final String source_iden, final List<String> list, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendList(iden, title, source_iden, list);
            }
        }, async);
    }   // end sendNoteAsync

    /**
     * Sends an address to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param name name to accompany the push
     * @param address the address to send
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendAddress(String iden, String name, String address) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "addess"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("name", name));
        nameValuePairs.add(new BasicNameValuePair("address", address));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends an address to the specified Pushbullet device with custom source
     * device identification code.
     *
     * @param iden the Pushbullet device
     * @param name name to accompany the push
     * @param address the address to send
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendAddress(String iden, String name, String address, String source_iden) throws PushbulletException {
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("type", "addess"));
        nameValuePairs.add(new BasicNameValuePair("device_iden", iden));
        nameValuePairs.add(new BasicNameValuePair("name", name));
        nameValuePairs.add(new BasicNameValuePair("address", address));
        nameValuePairs.add(new BasicNameValuePair("source_device_iden", source_iden));
        return doHttpPost(API_PUSHES_URL, nameValuePairs);
    }

    /**
     * Sends an address to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param name name to accompany the push
     * @param address the address to send
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendAddressAsync(final String iden, final String name, final String address, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendAddress(iden, name, address);
            }
        }, async);
    }   // end sendAddressAsync

    /**
     * Sends an address to the specified Pushbullet device with custom source
     * device identification code.
     *
     * @param iden the Pushbullet device
     * @param name name to accompany the push
     * @param address the address to send
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendAddressAsync(final String iden, final String name, final String address, final String source_iden, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendAddress(iden, name, address, source_iden);
            }
        }, async);
    }   // end sendAddressAsync

    /**
     * Sends a file to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param file the file to send
     * @param body optional text to accompany the push
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendFile(String iden, File file, String body) throws PushbulletException {
        if (file.length() >= 26214400) {
            String errMsg = "The file you are trying to upload is too big. File: " + file.getName() + " Size: " + file.length();
            LOGGER.warn(errMsg);
            throw new PushbulletException(errMsg);
        }

        //
        // S T E P   1 :   R E Q U E S T   U P L O A D
        //
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap(); // Get MIME type of file 
        String mime = mimeTypesMap.getContentType(file);                // I suspect Java is pretty bad here
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("file_name", file.getName()));
        nameValuePairs.add(new BasicNameValuePair("file_type", mime == null ? "application/octet-stream" : mime));
        UploadRequest upReq = JsonHelper.fromJson(doHttpPost(API_UPLOAD_REQUEST_URL, nameValuePairs), UploadRequest.class);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("File will be available at " + upReq.file_url);
        }

        //
        // S T E P   2 :   U P L O A D   F I L E
        //
        // Build upload connection
        // Transfer all of the "data" elements from Pushbullet's response
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        for (Map.Entry<String, String> entry : upReq.data.entrySet()) {
            builder.addTextBody(entry.getKey(), entry.getValue());
        }
        builder.addBinaryBody("file", file); // Actual file contents
        String uploadResult = doHttpPost(upReq.upload_url, builder); // Expect result to be empty

        //
        // S T E P   3 :   P U S H   N E W S   O F   T H E   F I L E
        //
        List<NameValuePair> pairs2 = new LinkedList<NameValuePair>();
        pairs2.add(new BasicNameValuePair("device_iden", iden));
        pairs2.add(new BasicNameValuePair("type", "file"));
        pairs2.add(new BasicNameValuePair("file_name", upReq.file_name));
        pairs2.add(new BasicNameValuePair("file_type", upReq.file_type));
        pairs2.add(new BasicNameValuePair("file_url", upReq.file_url));
        if (body != null) {
            pairs2.add(new BasicNameValuePair("body", body));
        }
        return doHttpPost(API_PUSHES_URL, pairs2);
    }

    /**
     * Sends a file to the specified Pushbullet device with custom source device
     * identification code
     *
     * @param iden the Pushbullet device
     * @param file the file to send
     * @param body optional text to accompany the push
     * @param source_iden The source device identification code
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    public String sendFile(String iden, File file, String body, String source_iden) throws PushbulletException {
        if (file.length() >= 26214400) {
            String errMsg = "The file you are trying to upload is too big. File: " + file.getName() + " Size: " + file.length();
            LOGGER.warn(errMsg);
            throw new PushbulletException(errMsg);
        }

        //
        // S T E P   1 :   R E Q U E S T   U P L O A D
        //
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap(); // Get MIME type of file 
        String mime = mimeTypesMap.getContentType(file);                // I suspect Java is pretty bad here
        List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("file_name", file.getName()));
        nameValuePairs.add(new BasicNameValuePair("file_type", mime == null ? "application/octet-stream" : mime));
        UploadRequest upReq = JsonHelper.fromJson(doHttpPost(API_UPLOAD_REQUEST_URL, nameValuePairs), UploadRequest.class);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("File will be available at " + upReq.file_url);
        }

        //
        // S T E P   2 :   U P L O A D   F I L E
        //
        // Build upload connection
        // Transfer all of the "data" elements from Pushbullet's response
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        for (Map.Entry<String, String> entry : upReq.data.entrySet()) {
            builder.addTextBody(entry.getKey(), entry.getValue());
        }
        builder.addBinaryBody("file", file); // Actual file contents
        String uploadResult = doHttpPost(upReq.upload_url, builder); // Expect result to be empty

        //
        // S T E P   3 :   P U S H   N E W S   O F   T H E   F I L E
        //
        List<NameValuePair> pairs2 = new LinkedList<NameValuePair>();
        pairs2.add(new BasicNameValuePair("device_iden", iden));
        pairs2.add(new BasicNameValuePair("source_device_iden", source_iden));
        pairs2.add(new BasicNameValuePair("type", "file"));
        pairs2.add(new BasicNameValuePair("file_name", upReq.file_name));
        pairs2.add(new BasicNameValuePair("file_type", upReq.file_type));
        pairs2.add(new BasicNameValuePair("file_url", upReq.file_url));
        if (body != null) {
            pairs2.add(new BasicNameValuePair("body", body));
        }
        return doHttpPost(API_PUSHES_URL, pairs2);
    }

    /**
     * Sends a file to the specified Pushbullet device
     *
     * @param iden the Pushbullet device
     * @param file the file to send
     * @param body optional text to accompany the push
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendFileAsync(final String iden, final File file, final String body, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendFile(iden, file, body);
            }
        }, async);
    }   // end sendFileAsync

    /**
     * Sends a file to the specified Pushbullet device with custom source device
     * identification code
     *
     * @param iden the Pushbullet device
     * @param file the file to send
     * @param body optional text to accompany the push
     * @param source_iden The source device identification code
     * @param async optional callback
     * @return A Java Future object related to the completion of the task
     */
    public Future<String> sendFileAsync(final String iden, final File file, final String body, final String source_iden, final Callback<String> async) {
        return doAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return sendFile(iden, file, body, source_iden);
            }
        }, async);
    }   // end sendFileAsync

    /* ********   I N T E R N A L   ******** */
    /**
     * Helper method for processing GET requests.
     *
     * @param url the URL to process
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    protected String doHttpGet(String url) throws PushbulletException {
        return doHttp(new HttpGet(url));
    }

    /**
     * Helper method for sending binary data (like a file).
     *
     * @param url the HTTP url to process
     * @param builder parameters to add to HTTP request
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    protected String doHttpPost(String url, MultipartEntityBuilder builder) throws PushbulletException {
        HttpPost post = new HttpPost(url);
        post.setEntity(builder.build());
        return doHttp(post);
    }

    /**
     * Helper method for posting data.
     *
     * @param url the HTTP url to process
     * @param nameValuePairs parameters to add to HTTP header
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    protected String doHttpPost(String url, List<NameValuePair> nameValuePairs) throws PushbulletException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, Charset.defaultCharset()));
        return doHttp(post);
    }

    /**
     * Helper method for posting data using inline list technique.
     *
     * @param url the HTTP url to process
     * @param nameValuePairs parameters to add to HTTP header
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    protected String doHttpPost(String url, NameValuePair... nameValuePairs) throws PushbulletException {
        List<NameValuePair> pairs = new LinkedList<NameValuePair>();
        pairs.addAll(Arrays.asList(nameValuePairs));
        return doHttpPost(url, pairs);
    }

    /**
     * Helper method for processing all of the HTTP requests.
     *
     * @param request the HTTP request to process
     * @return the HTTP response
     * @throws PushbulletException if there is a communication or other error
     */
    protected String doHttp(HttpUriRequest request) throws PushbulletException {
        StringBuilder result = new StringBuilder();
        try {
            HttpResponse response = httpClient.execute(request);
            LOGGER.debug(response.getStatusLine().toString());
            HttpEntity respEnt = response.getEntity();
            if (respEnt != null) {
                BufferedReader br = null;
                br = new BufferedReader(new InputStreamReader(respEnt.getContent()));
                for (String line; (line = br.readLine()) != null;) {
                    result.append(line);
                }
                br.close();
            }   // end if: got response
        } catch (IOException ex) {
            LOGGER.error("", ex);
            throw new PushbulletException(ex);
        }

        // First check for error
        PushbulletError err = JsonHelper.fromJson(result.toString(), PushbulletError.class);
<<<<<<< HEAD
        if( err != null && err.error != null ){
            LOGGER.error("Pushbullet error in result: " + result);
            throw new PushbulletException( err.error.message);
=======
        if (err != null && err.error != null) {
            throw new PushbulletException(err.error.message);
>>>>>>> 54492c15f9a16920a3926c67bebe461292b4382b
        }

        return result.toString();
    }

    /**
     * Used internally for the many sendXxxxAsync, getXxxxAsync, etc methods.
     *
     * @param callable the task to perform on another thread
     * @param callback the object to call when the task is done
     */
    private synchronized <T extends Object> Future<T> doAsync(final Callable<T> callable, final Callback<T> callback) {
        if (this.asyncExecutor == null) {
            asyncExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("Executor-for-" + PushbulletClient.this);
                    return t;
                }
            });
        }
        return asyncExecutor.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                T response = null;
                PushbulletException exc = null;
                try {
                    response = callable.call();
                } catch (Exception ex) {
                    exc = new PushbulletException(ex);
                    //throw exc;
                } finally {
                    // Although the Callable<T> may throw an exception,
                    // and we want the associated Future object to have
                    // knowledge of it, we still need to call the user's
                    // Callback<T> method.  This is why we have a method
                    // call within a finally block but the return outside.
                    if( callback != null ){
                        callback.completed(response, exc);
                    }
                }
                return response;
            }
        });
    }   // end doAsync

    /**
     * Used to keep the websocket alive, if it closes for some reason.
     */
    private class KeepAliveTask extends TimerTask {

        private final PushbulletClient parent = PushbulletClient.this;

        @Override
        public void run() {
            synchronized (parent) {
                if (websocketShouldBeRunning) {
                    try {
                        if (websocketSession != null && websocketSession.isOpen()) {
                            websocketSession.getBasicRemote().sendText("\n");
                            checkPulse.schedule(new KeepAliveTask(), websocketPulseInterval);
                        } else {
                            LOGGER.info("Timer discovered that websocket was closed. Attempting to reopen...");
                            initWebsocket();
                            if( LOGGER.isInfoEnabled() ){
                                LOGGER.info("Websocket opened: " + ( websocketSession == null ? "null" : websocketSession.isOpen() ) );
                            }
                        }
                    } catch (IOException ex) {
                        LOGGER.warn(ex.getMessage());
<<<<<<< HEAD
                        ex.printStackTrace();
                    }  catch (IllegalStateException ex) {
=======
                    } catch (IllegalStateException ex) {
>>>>>>> 54492c15f9a16920a3926c67bebe461292b4382b
                        LOGGER.warn(ex.getMessage());
                        ex.printStackTrace();
                    } finally {
                        checkPulse.schedule(new KeepAliveTask(), websocketPulseInterval); // If startWebsocket fails, try again later
                    }
                } else { // Should not be running. Shut it down.
                    if (websocketSession != null) {
                        try {
                            websocketSession.close();
                        } catch (IOException ex) {
                            LOGGER.error("while closing websocketSession", ex);
                        }
                    }
                    // Don't restart the timer
                }   // end else
            }   // end sync
        }   // end run

    }   // end KeepAliveTask

    /* ********   J S O N   H E L P E R   C L A S S E S   ******** */
    protected static class PushList {

        protected List<Push> pushes;
        protected String cursor;
    }

    protected static class DeviceList {

        protected List<Device> devices;
    }

    protected static class ContactsList {
        //protected List<Contact> contacts;
    }

    protected static class PushbulletError {

        protected String cat;
        protected Error error;

        protected class Error {

            protected String message;
            protected String type;
        }
    }

    protected static class UploadRequest {
        /*        protected static class Data {
         protected String acl;
         protected String awsaccesskeyid;
         protected String content_type;
         protected String key;
         protected String policy;
         protected String signature;
         protected void addToBuilder( MultipartEntityBuilder builder ){
         builder.addTextBody( "acl", acl );
         builder.addTextBody( "awsaccesskeyid", awsaccesskeyid );
         builder.addTextBody( "content-type", content_type );
         builder.addTextBody( "key", key );
         builder.addTextBody( "policy", policy );
         builder.addTextBody( "signature", signature );
         }
         public String toString(){
         return "acl: " + acl;
         }
         }*/

        protected Map<String, String> data;
        protected String file_name;
        protected String file_type;
        protected String file_url;
        protected String upload_url;
    }

    protected static class StreamMessage {

        protected final static String TICKLE_TYPE = "tickle";
        protected final static String NOP_TYPE = "nop";
        protected final static String PUSH_TYPE = "push";
        protected final static String MIRROR_TYPE = "mirror";
        protected final static String DISMISSAL_TYPE = "dismissal";

        protected final static String PUSH_SUBTYPE = "push";
        protected final static String DEVICE_SUBTYPE = "device";

        protected String type;
        protected String subtype;
        protected Push push;
    }

}   // end class
